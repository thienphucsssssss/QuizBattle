package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.*;
import javax.net.ssl.*;
import java.net.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.server.Wire.*;

/** One bounded game worker owns all room and JDBC state; one writer per TLS socket. */
public final class QuizServer implements AutoCloseable {
    private final ServerConfig config;
    private final QuizStore store;
    private final ProximityStore proximityStore;
    private final SurveyRepository surveyStore;
    private final ProximityRoomTracker proximity = new ProximityRoomTracker();
    private final ServerWifiMonitor serverWifi;
    private long lastWifiBroadcast;
    private final SSLServerSocket listener;
    private final LanDiscoveryServer discovery;
    private final String serverId=UUID.randomUUID().toString();
    private final ThreadPoolExecutor worker = pool("game-worker",1,1024), auth = pool("auth-worker",2,8), handshakes=pool("tls-handshake",4,16);
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("game-clock").factory());
    private final Set<Peer> peers=ConcurrentHashMap.newKeySet();
    private final Semaphore connectionSlots;
    private final AtomicBoolean closed=new AtomicBoolean(), tickPending=new AtomicBoolean();
    private final PasswordHasher.Hash dummy=PasswordHasher.hash(UUID.randomUUID().toString().toCharArray());
    private final LinkedHashMap<String,Attempts> loginLimits=new LinkedHashMap<>();
    private final LinkedHashMap<String,Attempts> registrationLimits=new LinkedHashMap<>();
    private final LinkedHashMap<String,Player> players=new LinkedHashMap<>();
    private final AvatarRoster avatars=new AvatarRoster();
    private GamePhase phase=GamePhase.WAITING;
    private int owner, questionCount=10, questionSeconds=15, questionIndex,questionSetId;
    private String questionSetName="",historySessionId="",historyRoomName="";
    private String roomCode="",roomName="",session="",started="";
    private String roomGeneration=UUID.randomUUID().toString();
    private long deadline,questionOpenedAt;
    private final RoomTelemetry telemetry=new RoomTelemetry();
    private final FloorTracker floor=new FloorTracker();
    private long floorVersion,lastFloorBroadcast;
    private List<ObjectNode> questions=List.of();
    private boolean resultSaved, aborted;
    private static ThreadPoolExecutor pool(String name,int count,int size) {return new ThreadPoolExecutor(count,count,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(size),Thread.ofPlatform().daemon().name(name+"-",0).factory(),new ThreadPoolExecutor.AbortPolicy());}
    private record Attempts(long start,int count) { }
    private static final class Player {
        final String id,nickname,avatarAtJoin; String ipAddress; Peer peer; boolean ready; String answer="",avatar; int score,correct,streak,bestStreak,lastPoints,speedBonus,streakBonus;
        byte[] resumeHash; long resumeUntil; boolean resumable,telemetrySupported;
        long answerElapsedMs=-1;
        void resetRound(){answer="";answerElapsedMs=-1;lastPoints=speedBonus=streakBonus=0;}
        void resetGame(){score=correct=streak=bestStreak=0;resetRound();}
        Player(String id,String nickname,String avatar,Peer peer){this.id=id;this.nickname=nickname;this.avatar=this.avatarAtJoin=avatar;this.peer=peer;this.ipAddress=peer.socket.getInetAddress().getHostAddress();}
    }
    private record Cached(String signature,ObjectNode response) { }
    public QuizServer(ServerConfig config,SSLContext tls,QuizStore store) throws Exception {
        this(config,tls,store,new ServerWifiMonitor());
    }
    QuizServer(ServerConfig config,SSLContext tls,QuizStore store,ServerWifiMonitor serverWifi) throws Exception {
        this.serverWifi=serverWifi;
        this.config=config;this.store=store;connectionSlots=new Semaphore(config.maxConnections());
        proximity.accessPoints.load(config.database());
        this.surveyStore=new SurveyRepository(config.database(),false);
        this.proximityStore=new ProximityStore(config.database(),128,warning->{
            try{worker.execute(()->{proximity.setPersistenceWarning(warning);emitProximityUpdate();});}catch(Exception ignored){}
        });
        listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();listener.setReuseAddress(true);
        try {
            listener.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});listener.bind(new InetSocketAddress(config.bind(),config.port()));
            discovery=new LanDiscoveryServer(config.bind(),config.discoveryPort(),port());
        } catch(Exception error){listener.close();proximityStore.close();surveyStore.close();serverWifi.close();throw error;}
    }
    ProximityRoomTracker proximityTracker(){return proximity;}
    ProximityStore proximityStore(){return proximityStore;}
    public int port(){return listener.getLocalPort();}
    public int discoveryPort(){return discovery.port();}
    public void start(){
        serverWifi.start();
        discovery.start();
        Thread.ofPlatform().daemon().name("tls-accept").start(this::accept);
        clock.scheduleAtFixedRate(()-> {if(!closed.get()&&tickPending.compareAndSet(false,true)){try{worker.execute(()->{try{tick();}catch(Exception e){System.err.println("Game tick failed; aborting current match.");if(running())abort();}finally{tickPending.set(false);}});}catch(RejectedExecutionException busy){tickPending.set(false);}}},0,50,TimeUnit.MILLISECONDS);
    }
    private void accept(){
        while(!closed.get())try{
            var socket=(SSLSocket)listener.accept();socket.setSoTimeout(5000);socket.setTcpNoDelay(true);
            // Directed TLS also supports overlay addresses (e.g. Tailscale 100.64/10).
            // Inbound reachability is controlled by the bind address and OS firewall.
            if(!connectionSlots.tryAcquire()){socket.close();continue;}
            var peer=new Peer(socket);peers.add(peer);
            try{handshakes.execute(()->{try{socket.startHandshake();socket.setSoTimeout(1000);peer.handshaken=true;Thread.ofVirtual().name("tls-writer").start(peer::write);Thread.ofVirtual().name("tls-reader").start(peer::read);}catch(Exception e){peer.close();}});}catch(RejectedExecutionException busy){peer.close();}
        }catch(Exception error){if(!closed.get())System.err.println("Accept failed: "+error.getClass().getSimpleName());}
    }
    private final class Peer {
        final SSLSocket socket; final BlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(100); final AtomicBoolean gone=new AtomicBoolean();
        final Map<String,Cached> cache=new LinkedHashMap<>(); final long connected=System.nanoTime();
        volatile long lastSeen=connected; volatile boolean handshaken,closeAfterWrite;
        int userId,credentialVersion; String role="",playerId=""; boolean mustChange,authBusy; long rateStart; int rateCount;
        QuestionImportSession questionImport;
        boolean proximityHistoryBusy,surveyBusy;
        Peer(SSLSocket socket){this.socket=socket;}
        void send(ObjectNode data){
            if(gone.get()||closeAfterWrite)return;
            try{if(!outgoing.offer(JsonMessages.encode(data)))close();}catch(Exception error){close();}
        }
        void read(){
            var decoder=new NdjsonDecoder();var buffer=new byte[8192];
            try{while(!gone.get()){
                int count;try{count=socket.getInputStream().read(buffer);}catch(SocketTimeoutException timeout){continue;}
                if(count<0)break;
                for(String frame:decoder.feed(Arrays.copyOf(buffer,count))){var message=JsonMessages.parse(frame);lastSeen=System.nanoTime();
                    try{worker.execute(()->handle(this,message));}catch(RejectedExecutionException busy){close();break;}
                }
            }}catch(Exception ignored){/* Payloads and secrets never logged. */}finally{close();}
        }
        void write(){try{while(!gone.get()){
            byte[] data=outgoing.poll(250,TimeUnit.MILLISECONDS);
            if(data!=null){socket.getOutputStream().write(data);socket.getOutputStream().flush();}
            if(closeAfterWrite&&outgoing.isEmpty())break;
        }}catch(Exception ignored){}finally{close();}}
        void close(){if(gone.compareAndSet(false,true)){try{socket.close();}catch(Exception ignored){}connectionSlots.release();/* worker tick removes state even if event queue is full */}}
    }
    private void handle(Peer p,JsonNode request){
        if(p.gone.get()||p.closeAfterWrite)return;
        String id=request.path("request_id").asText(),type=request.path("type").asText();JsonNode payload=request.path("payload");
        if(!request.path("request_id").isTextual()||id.isBlank()||id.length()>80){p.close();return;}
        try{
            if(!type.matches("[A-Z][A-Z0-9_]{0,63}"))throw fail("INVALID_INPUT","Loại yêu cầu không hợp lệ.");
            long now=System.nanoTime();if(now-p.rateStart>TimeUnit.SECONDS.toNanos(1)){p.rateStart=now;p.rateCount=0;}
            if(++p.rateCount>60)throw fail("RATE_LIMITED","Gửi quá nhiều yêu cầu. Hãy chờ một chút.");
            if(type.equals("PING")){p.send(envelope("PONG",id,obj()));return;}
            if(type.equals("ROOM_BROWSE")){p.send(envelope("ROOM_BROWSE_OK",id,publicDirectory()));return;}
            if(type.equals("LOGOUT")||type.equals("LEAVE")){var leaving=players.get(p.playerId);if(leaving!=null){leaving.resumeHash=null;leaving.resumeUntil=0;}p.send(envelope(type+"_OK",id,obj()));p.closeAfterWrite=true;detach(p);return;}
            if(p.authBusy)throw fail("SERVER_BUSY","Đang xử lý xác thực. Hãy chờ.");
            if(type.equals("LOGIN")){login(p,id,payload);return;}
            if(type.equals("REGISTER")){register(p,id,payload);return;}
            if(type.equals("JOIN")){join(p,id,request);return;}
            if(type.equals("RESUME")){resume(p,id,request);return;}
            if(p.userId==0&&p.playerId.isEmpty())throw fail("AUTH_REQUIRED","Hãy đăng nhập hoặc tham gia phòng.");
            if(type.equals("PASSWORD_CHANGE")){manager(p);changePassword(p,id,payload);return;}
            if(p.mustChange)throw fail("PASSWORD_CHANGE_REQUIRED","Hãy đổi mật khẩu tạm trước khi tiếp tục.");
            if(type.startsWith("SURVEY_")){
                manager(p);
                if(p.surveyBusy)throw fail("SERVER_BUSY","Đang lưu dữ liệu khảo sát. Hãy chờ.");
                p.surveyBusy=true;
                surveyStore.handle(String.valueOf(p.userId),type,payload.deepCopy()).whenComplete((value,error)->{
                    try{worker.execute(()->{p.surveyBusy=false;if(p.gone.get()||p.closeAfterWrite)return;
                        if(error==null)p.send(envelope(type+"_OK",id,value));
                        else {Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                            p.send(envelope("ERROR",id,fail(cause instanceof IllegalArgumentException?"SURVEY_INVALID":"SURVEY_STORAGE_ERROR",cause instanceof IllegalArgumentException?cause.getMessage():"Không lưu được khảo sát. Mẫu trên client vẫn được giữ; hãy thử đồng bộ lại.").payload()));}
                    });}catch(RejectedExecutionException ignored){}
                });return;
            }
            if(type.equals("FLOOR_CONFIGURE")){
                host(p);waiting();if(p.surveyBusy)throw fail("SERVER_BUSY","Đang xử lý khảo sát.");
                if(payload.has("room_code")&&!roomCode.equals(payload.path("room_code").asText()))throw fail("FLOOR_CONTEXT_CHANGED","Phòng thi đã đổi trong lúc đồng bộ.");
                String areaId=text(payload,"area_id",80);int revision=integer(payload,"revision",1,1,1000000);
                vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan plan;
                try{plan=payload.has("plan")?WifiSurvey.decode(payload.path("plan"),vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan.class):vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan.DEFAULT;}
                catch(IllegalArgumentException invalid){throw fail("FLOOR_INVALID",invalid.getMessage());}
                long version=++floorVersion;String roomToken=roomGeneration;int ownerToken=owner;p.surveyBusy=true;
                surveyStore.bundle(String.valueOf(p.userId),areaId,revision).thenApply(bundle->FloorTracker.prepare(bundle,plan)).whenComplete((value,error)->{
                    try{worker.execute(()->{
                        p.surveyBusy=false;if(p.gone.get()||p.closeAfterWrite)return;
                        if(version!=floorVersion||!roomToken.equals(roomGeneration)||ownerToken!=owner||phase!=GamePhase.WAITING){p.send(envelope("ERROR",id,fail("FLOOR_CONTEXT_CHANGED","Phòng đã đổi; chọn lại bản đồ.").payload()));return;}
                        if(error!=null){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();p.send(envelope("ERROR",id,fail("FLOOR_INVALID",cause instanceof IllegalArgumentException?cause.getMessage():"Không mở được khảo sát đã đồng bộ.").payload()));return;}
                        floor.configure(value);p.send(envelope(type+"_OK",id,floorSnapshot(p)));broadcastState();emitFloorUpdate();
                    });}catch(RejectedExecutionException ignored){}
                });return;
            }
            if(type.equals("FLOOR_DISABLE")){host(p);waiting();floorVersion++;floor.clear();p.send(envelope(type+"_OK",id,floorSnapshot(p)));broadcastState();emitFloorUpdate();return;}
            if(type.equals("FLOOR_GET")){if(p.playerId.isEmpty())host(p);else student(p);p.send(envelope(type+"_OK",id,floorSnapshot(p)));return;}
            if(type.equals("FLOOR_REPORT")){var player=student(p);try{floor.report(player.id,payload,System.nanoTime());}catch(IllegalArgumentException invalid){throw fail("FLOOR_INVALID",invalid.getMessage());}p.send(envelope(type+"_OK",id,floorSnapshot(p)));emitFloorUpdate();return;}
            if(type.equals("ROOM_STATE")){p.send(envelope("ROOM_STATE_OK",id,state(p,true)));return;}
            if(type.equals("NETWORK_PROBE_REPLY")){var player=student(p);telemetry.reply(player.id,text(payload,"probe_id",80),System.nanoTime());p.send(envelope(type+"_OK",id,obj()));broadcastState();emitProximityUpdate();return;}
            if(type.startsWith("USER_")){admin(p);users(p,id,type,payload);return;}
            if(type.startsWith("QUESTION_")){
                manager(p);boolean mutation=Set.of("QUESTION_CREATE","QUESTION_UPDATE","QUESTION_DELETE","QUESTION_SET_ACTIVE","QUESTION_IMPORT_COMMIT","QUESTION_SET_CREATE","QUESTION_SET_DELETE").contains(type);
                String signature=type+":"+payload;
                if(mutation&&p.cache.containsKey(id)){var cached=p.cache.get(id);if(!cached.signature.equals(signature))throw fail("REQUEST_ID_REUSED","Mã yêu cầu đã dùng cho dữ liệu khác.");p.send(cached.response.deepCopy());return;}
                if(mutation)waiting();
                if(type.equals("QUESTION_IMPORT_PREVIEW")){
                    waiting();
                    if(!payload.has("import_id")&&integer(payload,"offset",0,0,500)==0)p.questionImport=new QuestionImportSession();
                    if(p.questionImport==null)throw fail("IMPORT_INVALID_STATE","Hãy chọn tệp để tạo bản xem trước.");
                    p.send(envelope(type+"_OK",id,p.questionImport.append(store,p.userId,payload)));return;
                }
                JsonNode value=switch(type){
                    case "QUESTION_LIST"->store.questions(p.userId,payload);
                    case "QUESTION_GET"->store.question(p.userId,integer(payload,"question_id",0,1,Integer.MAX_VALUE));
                    case "QUESTION_CATEGORIES"->store.categories(p.userId);
                    case "QUESTION_SET_LIST"->store.questionSets(p.userId,payload);
                    case "QUESTION_SET_CREATE"->store.createQuestionSet(p.userId,payload);
                    case "QUESTION_SET_DELETE"->{int set=integer(payload,"question_set_id",0,1,Integer.MAX_VALUE);
                        if(owner==p.userId&&questionSetId==set)throw fail("QUESTION_SET_IN_USE","Phòng đang dùng bộ câu hỏi này. Hãy đổi nguồn câu hỏi hoặc đóng phòng trước.");
                        store.deleteQuestionSet(p.userId,set);yield obj();}
                    case "QUESTION_CHECK_DUPLICATE"->store.duplicates(p.userId,text(payload,"content",2000),payload.hasNonNull("exclude_question_id")?integer(payload,"exclude_question_id",0,1,Integer.MAX_VALUE):0);
                    case "QUESTION_CREATE","QUESTION_UPDATE"->store.saveQuestion(p.userId,payload,type.equals("QUESTION_UPDATE"));
                    case "QUESTION_IMPORT_COMMIT"->{if(p.questionImport==null)throw fail("IMPORT_INVALID_STATE","Hãy kiểm tra lại tệp trước khi nhập.");var imported=p.questionImport.commit(store,p.userId,payload);p.questionImport=null;yield imported;}
                    case "QUESTION_DELETE","QUESTION_SET_ACTIVE"->{store.mutateQuestion(p.userId,type,payload);yield obj();}
                    default->throw fail("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.");};
                var response=envelope(type+"_OK",id,value);if(mutation){p.cache.put(id,new Cached(signature,response.deepCopy()));if(p.cache.size()>128)p.cache.remove(p.cache.keySet().iterator().next());}p.send(response);return;
            }
            switch(type){
                case "HISTORY_LIST"->{manager(p);p.send(envelope(type+"_OK",id,store.history(p.userId,payload)));}
                case "HISTORY_GET"->{manager(p);p.send(envelope(type+"_OK",id,store.historyDetail(p.userId,p.role.equals("ADMIN"),text(payload,"session_id",80))));}
                case "PROXIMITY_HISTORY_LIST","PROXIMITY_HISTORY_GET"->{
                    manager(p);
                    if(p.proximityHistoryBusy)throw fail("SERVER_BUSY","Đang đọc nhật ký. Hãy chờ một chút.");
                    CompletableFuture<ObjectNode> query;
                    if(type.equals("PROXIMITY_HISTORY_LIST"))query=proximityStore.listHistoryAsync(p.userId,p.role.equals("ADMIN"),payload);
                    else {
                        String sid=payload.hasNonNull("session_id")?text(payload,"session_id",80):text(request,"session_id",80);
                        int page=integer(payload,"alert_page",1,1,1_000_000);
                        query=proximityStore.getHistoryDetailAsync(p.userId,p.role.equals("ADMIN"),sid,page);
                    }
                    p.proximityHistoryBusy=true;
                    query.whenComplete((value,error)->{
                        try{worker.execute(()->{
                            p.proximityHistoryBusy=false;
                            if(p.gone.get()||p.closeAfterWrite)return;
                            if(error==null)p.send(envelope(type+"_OK",id,value));
                            else p.send(envelope("ERROR",id,(error instanceof Failure f?f:fail("SERVER_ERROR","Không đọc được nhật ký giám sát.")).payload()));
                        });}catch(RejectedExecutionException ignored){}
                    });
                }
                case "ROOM_CONFIGURE"->{manager(p);if(phase!=GamePhase.WAITING&&phase!=GamePhase.FINISHED)throw fail("INVALID_STATE","Hãy kết thúc ván đang chạy trước khi đổi cấu hình.");
                    if(owner!=0&&owner!=p.userId)throw fail("ROOM_BUSY","Phòng đang do giảng viên khác tổ chức.");
                    String name=text(payload,"room_name",80);int count=integer(payload,"question_count",10,1,20),seconds=integer(payload,"duration_seconds",15,5,120);
                    int set=integer(payload,"question_set_id",0,0,Integer.MAX_VALUE);
                    String source=optional(payload,"history_session_id","",80),sourceName="",setName="";
                    if(!source.isEmpty()&&set!=0)throw fail("INVALID_INPUT","Chỉ chọn một nguồn câu hỏi cho phòng.");
                    if(!source.isEmpty()){store.historyQuestions(p.userId,source,count);sourceName=store.historyMatch(p.userId,source).path("room_name").asText();}
                    else {store.gameQuestions(p.userId,count,set);if(set>0)setName=store.questionSet(p.userId,set).path("name").asText();}
                    boolean clearReady=proximity.configure(payload,false);
                    boolean sourceChanged=set!=questionSetId||!source.equals(historySessionId)||count!=questionCount||seconds!=questionSeconds;
                    if(phase==GamePhase.FINISHED)resetRoom();
                    if(owner==0){owner=p.userId;roomCode=newCode();}roomName=name;questionCount=count;questionSeconds=seconds;
                    questionSetId=set;questionSetName=setName;historySessionId=source;historyRoomName=sourceName;
                    if(clearReady||sourceChanged)players.values().forEach(v->v.ready=false);
                    p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();emitProximityUpdate();}
                case "PROXIMITY_CONFIGURE"->{host(p);waiting();
                    boolean clearReady=proximity.configure(payload,true);
                    if(clearReady)players.values().forEach(v->v.ready=false);
                    p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();emitProximityUpdate();}
                case "AP_CONFIGURE"->{host(p);waiting();
                    proximity.configureAccessPoints(payload);
                    p.send(envelope(type+"_OK",id,monitoringSnapshot()));broadcastState();emitProximityUpdate();}
                case "AP_CONFIG_GET"->{host(p);
                    p.send(envelope(type+"_OK",id,proximity.accessPoints.configuration()));}
                case "PROXIMITY_CALIBRATION"->{Player player=student(p);waiting();
                    var res=proximity.handleCalibration(player.id,payload,System.nanoTime());
                    p.send(envelope(type+"_OK",id,res));broadcastState();emitProximityUpdate();}
                case "PROXIMITY_REPORT"->{Player player=student(p);long nowNano=System.nanoTime();
                    var selfView=proximity.handleReport(player.id,request,running(),session,nowNano);
                    syncTelemetryContext();
                    String refreshId=payload.path("refresh_id").asText();
                    if(telemetry.accepts(player.id,refreshId)){
                        boolean fresh=payload.path("samples").size()==LanTelemetryRules.REFRESH_SAMPLES;
                        boolean measured=fresh&&(selfView.has("estimated_meters")||selfView.path("ap_distance").path("fresh").asBoolean());
                        telemetry.complete(player.id,refreshId,measured?"UPDATED":"FAILED",measured?"":selfView.path("status_label").asText("Chưa có đủ mẫu mới"));
                    }
                    if(phase==GamePhase.WAITING&&player.ready&&!proximity.isReadyEligible(player.id,nowNano)){player.ready=false;broadcastState();}
                    p.send(envelope(type+"_OK",id,selfView));emitProximityUpdate();}
                case "PROXIMITY_REFRESH_STATUS"->{var player=student(p);syncTelemetryContext();
                    String reason=text(payload,"reason",60);
                    if(!Set.of("CALIBRATING","PERMISSION_DENIED","WIFI_DISABLED","NO_ADAPTER","DISCONNECTED","API_ERROR","CANCELLED","NOT_REPORTING").contains(reason))throw fail("INVALID_INPUT","Trạng thái thu mẫu không hợp lệ.");
                    telemetry.complete(player.id,text(payload,"refresh_id",80),"FAILED",reason);
                    p.send(envelope(type+"_OK",id,obj()));emitProximityUpdate();}
                case "PROXIMITY_REFRESH"->{manager(p);
                    if(!p.role.equals("ADMIN")&&(owner==0||owner!=p.userId))throw fail("FORBIDDEN","Chỉ chủ phòng hoặc Admin được làm mới số đo.");
                    if(owner==0)throw fail("PROXIMITY_DISABLED","Hãy tạo phòng thi trước khi lấy số đo.");
                    syncTelemetryContext();long refreshNow=System.nanoTime();boolean active=telemetry.refresh(refreshNow).path("state").asText().equals("RUNNING");
                    String refresh=telemetry.begin(players.values().stream().filter(v->v.peer!=null&&!v.peer.gone.get()).map(v->v.id).toList(),refreshNow);
                    if(!active)for(String target:telemetry.pending()){var v=players.get(target);if(v!=null&&v.peer!=null)v.peer.send(envelope("PROXIMITY_REFRESH_REQUEST",null,obj().put("refresh_id",refresh).put("room_code",roomCode).put("config_epoch",proximity.configEpoch()).put("session_id",running()?session:"")));}
                    p.send(envelope(type+"_OK",id,monitoringSnapshot()));emitProximityUpdate();}
                case "PROXIMITY_GET"->{manager(p);
                    if(!p.role.equals("ADMIN")&&(owner==0||owner!=p.userId))throw fail("FORBIDDEN","Giảng viên chỉ xem dữ liệu giám sát phòng của mình.");
                    p.send(envelope(type+"_OK",id,monitoringSnapshot()));}
                case "AVATAR_SET"->{Player player=student(p);waiting();if(player.ready)throw fail("ALREADY_READY","Bỏ sẵn sàng trước khi đổi linh vật.");
                    try{avatars.select(player.id,text(payload,"avatar_id",30));}catch(IllegalArgumentException invalid){throw fail("INVALID_AVATAR","Linh vật không hợp lệ.");}catch(IllegalStateException taken){throw new Failure("AVATAR_TAKEN","Linh vật đã có người chọn.",state(p,false));}
                    player.avatar=avatars.snapshot().get(player.id);proximity.onPlayerUpdateAvatar(player.id,player.avatar);p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();emitProximityUpdate();}
                case "READY_SET"->{Player player=student(p);waiting();boolean wantReady=bool(payload,"ready",false);
                    if(wantReady){var failure=proximity.checkReadyEligibility(player.id,System.nanoTime());if(failure!=null)throw failure;}
                    player.ready=wantReady;p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();emitProximityUpdate();}
                case "START_GAME"->{host(p);waiting();
                    if(proximity.isEnabled()){
                        long nowNano=System.nanoTime();boolean lostReady=false;
                        for(var v:players.values())if(v.ready&&!proximity.isReadyEligible(v.id,nowNano)){v.ready=false;lostReady=true;}
                        if(lostReady){broadcastState();emitProximityUpdate();throw fail("NOT_READY","Có người chơi mất điều kiện giám sát Wi-Fi hoặc số đo đã quá hạn. Hãy kiểm tra bảng Giám sát.");}
                    }
                    if(players.size()<2||players.values().stream().anyMatch(v->!v.ready||v.peer==null||v.peer.gone.get()))throw fail("NOT_READY","Cần ít nhất hai người và tất cả sẵn sàng.");
                    questions=historySessionId.isEmpty()?store.gameQuestions(owner,questionCount,questionSetId):store.historyQuestions(owner,historySessionId,questionCount);
                    session=UUID.randomUUID().toString();started=Instant.now().toString();questionIndex=0;resultSaved=false;aborted=false;
                    players.values().forEach(Player::resetGame);phase=GamePhase.COUNTDOWN;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.countdownMillis());
                    proximity.onMatchStart(session,owner,roomName,roomCode,started,proximityStore,System.nanoTime());
                    p.send(envelope("START_GAME_OK",id,obj()));emitAll("GAME_START",obj().put("session_id",session).put("remaining_ms",remaining()));broadcastState();emitProximityUpdate();}
                case "ANSWER"->answer(p,id,request);
                case "ABORT_GAME"->{host(p);if(!running())throw fail("INVALID_STATE","Không có ván đang chạy.");p.send(envelope(type+"_OK",id,obj()));abort();}
                case "ROOM_RESET","ROOM_CLOSE"->{host(p);if(running())throw fail("INVALID_STATE","Hãy kết thúc ván trước.");
                    if(type.equals("ROOM_CLOSE"))closeRoom();else resetRoom();
                    p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();emitProximityUpdate();}
                default->throw fail("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.");
            }
        }catch(Failure error){p.send(envelope("ERROR",id,error.payload()));}
        catch(Exception error){System.err.println("Request failed: "+type+" / "+error.getClass().getSimpleName());p.send(envelope("ERROR",id,fail("SERVER_ERROR","Máy chủ chưa hoàn tất yêu cầu. Hãy thử lại.").payload()));}
    }
    private ObjectNode publicDirectory()throws SQLException{
        var data=obj().put("server_id",serverId).put("server_name","Quiz Battle · "+serverId.substring(0,4).toUpperCase(Locale.ROOT));
        data.putArray("capabilities").add(ProximityRules.CAPABILITY).add(ResumeRules.CAPABILITY).add(WifiSurvey.CAPABILITY).add(AutomaticApDistance.CAPABILITY);
        var rooms=array();
        if(owner!=0){
            int count=players.size(); // Includes temporarily reserved seats.
            var host=store.account(owner);
            rooms.add(obj().put("room_code",roomCode).put("room_name",roomName).put("teacher_name",host==null?"Giảng viên":host.name()).put("phase",phase.name()).put("player_count",count).put("max_players",config.maxPlayers()).put("joinable",phase==GamePhase.WAITING&&count<config.maxPlayers()).put("proximity_enabled",proximity.isEnabled()).put("proximity_supported",true));
        }
        return data.set("rooms",rooms);
    }
    private void manager(Peer p){if(p.userId==0)throw fail("FORBIDDEN","Chỉ tài khoản quản lý được sử dụng.");}
    private void admin(Peer p){manager(p);if(!p.role.equals("ADMIN"))throw fail("FORBIDDEN","Chỉ Admin được quản lý tài khoản.");}
    private void host(Peer p){manager(p);if(owner==0||owner!=p.userId)throw fail("FORBIDDEN","Chỉ chủ phòng được điều khiển.");}
    private Player student(Peer p){var v=players.get(p.playerId);if(v==null||v.peer!=p)throw fail("FORBIDDEN","Bạn chưa tham gia phòng.");return v;}
    private void waiting(){if(phase!=GamePhase.WAITING)throw fail("INVALID_STATE","Chỉ thực hiện khi phòng đang chờ.");}
    private boolean running(){return phase!=GamePhase.WAITING&&phase!=GamePhase.FINISHED;}
    private void unauthenticated(Peer p){if(p.userId!=0||!p.playerId.isEmpty())throw fail("ALREADY_JOINED","Kết nối đã có danh tính.");}
    private void register(Peer p,String id,JsonNode payload)throws SQLException{
        unauthenticated(p);
        if(!config.registrationEnabled())throw fail("REGISTRATION_DISABLED","Máy chủ đang tắt đăng ký. Liên hệ Admin để được cấp tài khoản.");
        if(payload.has("role")&&!payload.path("role").asText().equals("TEACHER"))throw fail("FORBIDDEN","Chỉ được đăng ký tài khoản giảng viên.");
        String ip=p.socket.getInetAddress().getHostAddress();long now=System.nanoTime();var attempts=registrationLimits.get(ip);
        if(attempts==null||now-attempts.start>TimeUnit.MINUTES.toNanos(1))attempts=new Attempts(now,0);
        if(attempts.count>=5)throw fail("RATE_LIMITED","Đăng ký quá nhiều lần. Chờ một phút.");
        registrationLimits.put(ip,new Attempts(attempts.start,attempts.count+1));if(registrationLimits.size()>1024)registrationLimits.remove(registrationLimits.keySet().iterator().next());
        String username=text(payload,"username",40).toLowerCase(Locale.ROOT),name=text(payload,"display_name",80);
        if(!username.matches("[a-z0-9_.-]{3,40}"))throw fail("INVALID_INPUT","Tên đăng nhập cần 3–40 ký tự a–z, 0–9, _, ., -.");
        if(store.account(username)!=null)throw fail("USERNAME_TAKEN","Tên đăng nhập đã được sử dụng.");
        char[] secret=password(payload,"password").toCharArray();
        asyncAuth(p,id,()->PasswordHasher.hash(secret),hash->p.send(envelope("REGISTER_OK",id,store.createUser(username,name,"TEACHER",hash,false))),()->Arrays.fill(secret,'\0'));
    }
    private void login(Peer p,String id,JsonNode payload)throws SQLException{
        unauthenticated(p);String username=text(payload,"username",40).toLowerCase(Locale.ROOT);String secret=password(payload,"password");
        String ip=p.socket.getInetAddress().getHostAddress();long now=System.nanoTime();var attempts=loginLimits.get(ip);
        if(attempts==null||now-attempts.start>TimeUnit.MINUTES.toNanos(1))attempts=new Attempts(now,0);
        if(attempts.count>=20)throw fail("RATE_LIMITED","Thử đăng nhập quá nhiều lần. Chờ một phút.");loginLimits.put(ip,new Attempts(attempts.start,attempts.count+1));if(loginLimits.size()>1024)loginLimits.remove(loginLimits.keySet().iterator().next());
        var snapshot=store.account(username);char[] chars=secret.toCharArray();
        asyncAuth(p,id,()->{try{return PasswordHasher.verify(chars,snapshot==null?dummy:snapshot.hash());}finally{Arrays.fill(chars,'\0');}},ok->{
            var current=snapshot==null?null:store.account(snapshot.id());
            if(!ok||current==null||!current.active()||current.version()!=snapshot.version()) {p.send(envelope("LOGIN_ERROR",id,fail("AUTH_FAILED","Tên đăng nhập hoặc mật khẩu không đúng.").payload()));return;}
            if(peers.stream().anyMatch(other->other!=p&&!other.gone.get()&&!other.closeAfterWrite&&other.userId==current.id()))throw fail("ALREADY_LOGGED_IN","Tài khoản đang đăng nhập ở máy khác.");
            p.userId=current.id();p.credentialVersion=current.version();p.role=current.role();p.mustChange=current.change();p.send(envelope("LOGIN_OK",id,current.publicData()));
        },()->Arrays.fill(chars,'\0'));
    }
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
    @FunctionalInterface private interface Complete<T>{void accept(T value)throws Exception;}
    private <T>void asyncAuth(Peer p,String id,Work<T> job,Complete<T> done,Runnable cleanup){
        p.authBusy=true;
        try{auth.execute(()->{
            T result=null;Exception failure=null;try{result=job.run();}catch(Exception e){failure=e;}finally{cleanup.run();}
            T value=result;Exception problem=failure;
            try{worker.execute(()->{p.authBusy=false;if(p.gone.get()||p.closeAfterWrite)return;
                try{if(problem!=null)throw problem;done.accept(value);}catch(Failure e){p.send(envelope("ERROR",id,e.payload()));}catch(Exception e){p.send(envelope("ERROR",id,fail("SERVER_ERROR","Không hoàn tất xác thực.").payload()));}
            });}catch(RejectedExecutionException busy){p.close();}
        });}catch(RejectedExecutionException busy){p.authBusy=false;cleanup.run();throw fail("SERVER_BUSY","Máy chủ đang bận xác thực. Hãy thử lại.");}
    }
    private void credentialGuard(int userId){if(owner==userId&&running())throw fail("INVALID_STATE","Không thay đổi tài khoản chủ phòng trong lúc thi.");}
    private void changePassword(Peer p,String id,JsonNode data)throws SQLException{
        credentialGuard(p.userId);var snapshot=store.account(p.userId);char[] old=password(data,"current_password").toCharArray(),next=password(data,"new_password").toCharArray();
        if(Arrays.equals(old,next)){Arrays.fill(old,'\0');Arrays.fill(next,'\0');throw fail("INVALID_INPUT","Mật khẩu mới phải khác mật khẩu cũ.");}
        asyncAuth(p,id,()->{if(!PasswordHasher.verify(old,snapshot.hash()))throw fail("AUTH_FAILED","Mật khẩu hiện tại không đúng.");return PasswordHasher.hash(next);},hash->{
            var current=store.account(p.userId);if(current.version()!=snapshot.version()||!current.active())throw fail("AUTH_FAILED","Phiên đăng nhập đã thay đổi.");credentialGuard(p.userId);store.password(p.userId,hash,false);
            p.send(envelope("PASSWORD_CHANGE_OK",id,obj()));revoke(p.userId);if(owner==p.userId){closeRoom();broadcastState();}
        },()->{Arrays.fill(old,'\0');Arrays.fill(next,'\0');});
    }
    private void users(Peer p,String id,String type,JsonNode data)throws SQLException{
        if(type.equals("USER_LIST")){p.send(envelope(type+"_OK",id,store.users(data)));return;}
        if(type.equals("USER_CREATE")){
            if(data.has("role")&&!data.path("role").asText().equals("TEACHER"))throw fail("FORBIDDEN","Chỉ tạo tài khoản giảng viên.");
            String username=text(data,"username",40),name=text(data,"display_name",80);char[] secret=password(data,"temporary_password").toCharArray();
            asyncAuth(p,id,()->PasswordHasher.hash(secret),hash->p.send(envelope(type+"_OK",id,store.createUser(username,name,"TEACHER",hash,true))),()->Arrays.fill(secret,'\0'));return;
        }
        int target=integer(data,"user_id",0,1,Integer.MAX_VALUE);var targetUser=store.account(target);
        if(targetUser==null)throw fail("NOT_FOUND","Không tìm thấy tài khoản.");if(!targetUser.role().equals("TEACHER"))throw fail("FORBIDDEN","Không sửa Admin ở đây.");
        if(type.equals("USER_RESET_PASSWORD")){
            credentialGuard(target);char[] secret=password(data,"temporary_password").toCharArray();
            asyncAuth(p,id,()->PasswordHasher.hash(secret),hash->{credentialGuard(target);store.password(target,hash,true);revoke(target);if(owner==target)closeRoom();p.send(envelope(type+"_OK",id,obj()));broadcastState();},()->Arrays.fill(secret,'\0'));return;
        }
        if(!Set.of("USER_UPDATE","USER_SET_ACTIVE").contains(type))throw fail("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.");
        if(type.equals("USER_SET_ACTIVE"))credentialGuard(target);
        var result=store.editUser(type,data);if(type.equals("USER_SET_ACTIVE")){revoke(target);if(owner==target)closeRoom();broadcastState();}p.send(envelope(type+"_OK",id,result));
    }
    private void revoke(int id){for(var p:peers)if(p.userId==id){p.closeAfterWrite=true;p.cache.clear();}}
    private void join(Peer p,String id,JsonNode request){
        unauthenticated(p);waiting();if(owner==0||!roomCode.equals(request.path("room_code").asText()))throw fail("WRONG_ROOM","Mã phòng không đúng hoặc phòng đã đóng.");
        String nickname=text(request.path("payload"),"nickname",24);String key=TextNormalizer.key(nickname);
        if(nickname.codePoints().anyMatch(Character::isISOControl))throw fail("INVALID_INPUT","Biệt danh phải nằm trên một dòng.");
        if(players.values().stream().anyMatch(v->TextNormalizer.key(v.nickname).equals(key)))throw fail("NICKNAME_TAKEN","Biệt danh đã có người dùng.");
        if(players.size()>=config.maxPlayers())throw fail("ROOM_FULL","Phòng đã đủ người.");
        String playerId=UUID.randomUUID().toString(),avatar=avatars.join(playerId);players.put(playerId,new Player(playerId,nickname,avatar,p));p.playerId=playerId;
        proximity.onPlayerJoin(playerId,nickname,avatar);
        boolean supports=false;for(var cap:request.path("payload").path("client_capabilities"))if(cap.asText().equals(LanTelemetryRules.CAPABILITY))supports=true;
        var player=players.get(playerId);player.telemetrySupported=supports;telemetry.join(playerId,supports);
        for(var cap:request.path("payload").path("client_capabilities"))if(cap.asText().equals(ResumeRules.CAPABILITY))player.resumable=true;
        var joined=obj().put("player_id",playerId).put("nickname",nickname).put("avatar_id",avatar).put("room_code",roomCode);
        if(player.resumable)joined.set("resume",issueCredential(player));
        p.send(envelope("JOIN_OK",id,joined));broadcastState();emitProximityUpdate();
    }
    private ObjectNode issueCredential(Player player){
        String token=ResumeRules.token();player.resumeHash=ResumeRules.hash(token);player.resumeUntil=0;
        return obj().put("token",token).put("player_id",player.id).put("room_generation",roomGeneration).put("room_code",roomCode).put("grace_seconds",config.resumeGraceSeconds());
    }
    private void resume(Peer p,String id,JsonNode request){
        unauthenticated(p);var body=request.path("payload");var player=players.get(body.path("player_id").asText());long now=System.nanoTime();
        String token=body.path("token").asText(),next=body.path("next_token").asText();
        if(player==null||!roomGeneration.equals(body.path("room_generation").asText())||!roomCode.equals(request.path("room_code").asText())
            ||!ResumeRules.matches(player.resumeHash,token)||(player.resumeUntil!=0&&now>=player.resumeUntil)||!ResumeRules.valid(next)||next.equals(token))
            throw fail("RESUME_REJECTED","Phiên giữ chỗ không còn hợp lệ. Hãy tham gia lại khi phòng cho phép.");
        var previous=player.peer;
        if(previous!=null){previous.playerId="";previous.close();}
        player.peer=p;p.playerId=player.id;player.ready=false;player.resumeUntil=0;player.resumeHash=ResumeRules.hash(next);
        player.ipAddress=p.socket.getInetAddress().getHostAddress();
        telemetry.join(player.id,player.telemetrySupported);proximity.onPlayerResume(player.id,player.nickname,player.avatar);
        var restored=state(p,true);restored.put("player_id",player.id);
        p.send(envelope("RESUME_OK",id,restored));broadcastState();emitProximityUpdate();
    }
    private String newCode(){String chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";var r=new java.security.SecureRandom();var b=new StringBuilder();for(int i=0;i<6;i++)b.append(chars.charAt(r.nextInt(chars.length())));return b.toString();}
    private void answer(Peer p,String id,JsonNode request){
        Player player=student(p);JsonNode data=request.path("payload");int qid=integer(data,"question_id",0,1,Integer.MAX_VALUE);String choice=text(data,"answer",1);
        String reason=null;long now=System.nanoTime();
        if(!session.equals(request.path("session_id").asText()))reason="WRONG_SESSION";
        else if(questions.isEmpty()||questions.get(questionIndex).path("question_id").asInt()!=qid)reason="WRONG_QUESTION";
        else if(phase!=GamePhase.QUESTION_ACTIVE)reason="INVALID_STATE";
        else if(now>=deadline)reason="TIME_EXPIRED";
        else if(!choice.matches("[ABCD]"))reason="INVALID_ANSWER";
        else if(!player.answer.isEmpty())reason="DUPLICATE_ANSWER";
        if(reason==null){player.answer=choice;player.answerElapsedMs=Math.max(0,TimeUnit.NANOSECONDS.toMillis(now-questionOpenedAt));}
        var ack=obj().put("session_id",session).put("question_id",qid).put("accepted",reason==null);if(reason!=null)ack.put("reason",reason);
        if(reason==null)ack.put("answer_elapsed_ms",player.answerElapsedMs);
        p.send(envelope("ANSWER_ACK",id,ack));if(reason==null)broadcastState();
    }
    private long remaining(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
    private ObjectNode state(Peer viewer,boolean includeGame){
        var list=array();int ready=0,connected=0,answered=0;
        boolean canTrack=viewer.userId!=0&&(viewer.userId==owner||viewer.role.equals("ADMIN"));
        for(var v:players.values()){boolean live=v.peer!=null&&!v.peer.gone.get();if(live){connected++;if(v.ready)ready++;if(!v.answer.isEmpty())answered++;}
            var entry=obj().put("player_id",v.id).put("nickname",v.nickname).put("avatar_id",v.avatar).put("ready",v.ready).put("connected",live);
            entry.put("reconnecting",!live&&v.resumeHash!=null&&v.resumeUntil>System.nanoTime());
            if(canTrack)entry.put("resume_remaining_ms",Math.max(0,TimeUnit.NANOSECONDS.toMillis(v.resumeUntil-System.nanoTime())));
            if(canTrack)entry.put("ip_address",v.ipAddress).put("answered",!v.answer.isEmpty()).put("score",v.score).put("correct_count",v.correct);
            if(canTrack||v.id.equals(viewer.playerId))entry.put("answer_elapsed_ms",v.answerElapsedMs).put("streak",v.streak).put("best_streak",v.bestStreak);
            if(canTrack||v.id.equals(viewer.playerId))telemetry.decorate(v.id,entry,live,System.nanoTime());
            list.add(entry);}
        var state=obj().put("phase",phase.name()).put("room_code",roomCode).put("room_name",roomName).put("session_id",session).put("is_owner",owner!=0&&owner==viewer.userId)
            .put("question_count",questionCount).put("duration_seconds",questionSeconds).put("connected_player_count",connected).put("ready_count",ready).put("answered_count",answered).put("remaining_ms",remaining());
        state.put("question_set_id",questionSetId).put("question_set_name",questionSetName).put("question_source_name",historySessionId.isEmpty()?(questionSetId==0?"Toàn bộ ngân hàng":questionSetName):"Từ lịch sử: "+historyRoomName);
        if(viewer.userId==owner&&owner!=0)state.put("history_session_id",historySessionId).put("history_room_name",historyRoomName);
        state.putArray("capabilities").add(ProximityRules.CAPABILITY).add("QUESTION_SETS_V1").add("HISTORY_QUESTIONS_V1").add(LanTelemetryRules.CAPABILITY).add(ResumeRules.CAPABILITY).add(WifiSurvey.CAPABILITY);
        state.withArray("capabilities").add(FloorTracker.CAPABILITY);
        state.withArray("capabilities").add(AutomaticApDistance.CAPABILITY);
        state.set("floor",(viewer.userId==owner&&owner!=0)||players.containsKey(viewer.playerId)?floor.config():obj().put("enabled",false));
        state.put("scoring_version",AnswerScoring.VERSION);
        state.set("proximity",proximity.buildRoomStateSection(viewer.playerId,running(),System.nanoTime()));
        if(participant(viewer))((ObjectNode)state.path("proximity")).set("server_ap_distance",serverWifi.snapshot(System.nanoTime()));
        state.set("players",list);
        var self=players.get(viewer.playerId);
        if(self!=null)state.set("self_answer",obj().put("answer",self.answer).put("recorded",!self.answer.isEmpty()).put("answer_elapsed_ms",self.answerElapsedMs));
        if(includeGame&&(viewer.userId==owner||!viewer.playerId.isEmpty())){
            if(!questions.isEmpty()){state.set("question",publicQuestion());if(phase==GamePhase.SHOW_RESULT||phase==GamePhase.SHOW_RANKING)state.set("result",roundResult(viewer));if(phase==GamePhase.FINISHED)state.set("result",finalResult(viewer));}
        }return state;
    }
    private ObjectNode publicQuestion(){var q=questions.get(questionIndex).deepCopy();q.remove(List.of("correct_answer","is_active","updated_at_utc"));return q.put("session_id",session).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());}
    private ArrayNode ranking(){
        var values=new ArrayList<>(players.values());values.sort(Comparator.comparingInt((Player p)->p.score).reversed().thenComparing(p->p.id));var ranks=array();int rank=0,previous=-1;
        for(int i=0;i<values.size();i++){var v=values.get(i);if(v.score!=previous)rank=i+1;previous=v.score;ranks.add(obj().put("player_id",v.id).put("nickname",v.nickname).put("avatar_id",v.avatar).put("score",v.score).put("correct_count",v.correct).put("rank",rank).put("connected",v.peer!=null&&!v.peer.gone.get()).put("answer_elapsed_ms",v.answerElapsedMs).put("streak",v.streak).put("best_streak",v.bestStreak).put("points",v.lastPoints));}return ranks;
    }
    private ObjectNode roundResult(Peer viewer){
        var q=questions.get(questionIndex);var data=obj().put("session_id",session).put("question_id",q.path("question_id").asInt()).put("correct_answer",q.path("correct_answer").asText()).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());data.set("ranking",ranking());
        var counts=obj();for(String a:List.of("A","B","C","D"))counts.put(a,players.values().stream().filter(v->v.answer.equals(a)).count());data.set("answer_counts",counts);
        var v=players.get(viewer.playerId);if(v!=null){boolean correct=v.answer.equals(q.path("correct_answer").asText());data.put("your_answer",v.answer).put("correct",correct).put("points",v.lastPoints).put("score",v.score).put("answer_elapsed_ms",v.answerElapsedMs).put("streak",v.streak).put("best_streak",v.bestStreak).put("speed_bonus",v.speedBonus).put("streak_bonus",v.streakBonus);}return data;
    }
    private ObjectNode finalResult(Peer viewer){var data=obj().put("session_id",session).put("total",questionCount).put("result_saved",resultSaved).put("aborted",aborted);data.set("ranking",ranking());var v=players.get(viewer.playerId);if(v!=null)data.put("score",v.score).put("correct_count",v.correct);return data;}
    private boolean participant(Peer p){return !p.gone.get()&&!p.closeAfterWrite&&((owner!=0&&p.userId==owner)||(!p.playerId.isEmpty()&&players.containsKey(p.playerId)));}
    private void emitAll(String type,ObjectNode value){for(var p:peers)if(participant(p))p.send(envelope(type,null,value));}
    private void emitProximityUpdate(){
        if(owner==0)return;
        var snap=monitoringSnapshot();
        for(var p:peers)if(!p.gone.get()&&!p.closeAfterWrite&&!p.mustChange&&(p.role.equals("ADMIN")||p.userId==owner))p.send(envelope("PROXIMITY_UPDATE",null,snap));
    }
    private void broadcastState(){for(var p:peers)if(!p.gone.get()&&!p.closeAfterWrite&&!p.mustChange&&(p.userId!=0||!p.playerId.isEmpty()))p.send(envelope("ROOM_STATE",null,state(p,false)));}
    private ObjectNode floorSnapshot(Peer viewer){
        var snapshot=floor.config();snapshot.put("room_code",roomCode);var entries=snapshot.putArray("players");long now=System.nanoTime();
        snapshot.set("server_ap_distance",serverWifi.snapshot(now));
        for(var player:players.values())if(viewer.userId==owner||player.id.equals(viewer.playerId))
            entries.add(floor.player(player.id,player.nickname,player.peer!=null&&!player.peer.gone.get(),now).set("ap_distance",proximity.apDistance(player.id,now)));
        return snapshot;
    }
    private void emitFloorUpdate(){
        if(owner==0)return;lastFloorBroadcast=System.nanoTime();
        for(var p:peers)if(participant(p)&&!p.mustChange)p.send(envelope("FLOOR_UPDATE",null,floorSnapshot(p)));
    }
    private ObjectNode monitoringSnapshot(){
        syncTelemetryContext();long now=System.nanoTime();
        var snapshot=proximity.buildMonitoringSnapshot(roomCode,roomName,phase.name(),session,running(),System.nanoTime());
        snapshot.put("topology_supported",true);snapshot.set("refresh",telemetry.refresh(now));
        snapshot.set("server_ap_distance",serverWifi.snapshot(now));
        for(var value:snapshot.path("players")){var player=players.get(value.path("player_id").asText());if(player!=null&&value instanceof ObjectNode node){node.put("ip_address",player.ipAddress).put("ready",player.ready).put("reconnecting",player.peer==null&&player.resumeHash!=null&&player.resumeUntil>now);telemetry.decorate(player.id,node,player.peer!=null&&!player.peer.gone.get(),now);}}
        return snapshot;
    }
    private void syncTelemetryContext(){telemetry.context(roomCode+"|"+proximity.configEpoch()+"|"+(running()?session:""));}
    private void detach(Peer p){
        var v=players.get(p.playerId);if(v==null||v.peer!=p)return;v.peer=null;v.ready=false;
        telemetry.disconnect(v.id);
        floor.disconnect(v.id);
        long now=System.nanoTime();if(v.resumeHash!=null)v.resumeUntil=now+TimeUnit.SECONDS.toNanos(config.resumeGraceSeconds());
        proximity.onPlayerDisconnect(v.id,running(),session,now,v.resumeHash!=null);
        if(!running()&&v.resumeHash==null){players.remove(v.id);avatars.leave(v.id);telemetry.remove(v.id);floor.remove(v.id);}p.playerId="";
        if(running()&&!hasLiveOrReserved(now))abort();else{broadcastState();emitProximityUpdate();}
    }
    private boolean hasLiveOrReserved(long now){return players.values().stream().anyMatch(v->(v.peer!=null&&!v.peer.gone.get())||(v.resumeHash!=null&&(v.peer!=null||v.resumeUntil>now)));}
    private void tick()throws SQLException{
        long now=System.nanoTime();boolean changed=false;
        syncTelemetryContext();boolean netChanged=telemetry.tick(now);
        if(owner!=0&&now-lastWifiBroadcast>TimeUnit.SECONDS.toNanos(5)){lastWifiBroadcast=now;emitProximityUpdate();if(!floor.enabled())emitFloorUpdate();}
        if(floor.enabled()&&now-lastFloorBroadcast>TimeUnit.SECONDS.toNanos(2))emitFloorUpdate();
        for(var v:players.values())if(v.peer!=null&&!v.peer.gone.get()){var probe=telemetry.probe(v.id,now);if(probe!=null)v.peer.send(envelope("NETWORK_PROBE",null,probe));}
        for(var peer:peers)if(peer.questionImport!=null&&peer.questionImport.expired())peer.questionImport=null;
        for(var p:peers){if(now-p.lastSeen>TimeUnit.SECONDS.toNanos(20)||(p.userId==0&&p.playerId.isEmpty()&&now-p.connected>TimeUnit.SECONDS.toNanos(10)))p.close();if(p.gone.get()){detach(p);peers.remove(p);changed=true;}}
        for(var it=players.values().iterator();it.hasNext();){var v=it.next();if(v.peer==null&&v.resumeHash!=null&&now>=v.resumeUntil){
            v.resumeHash=null;v.resumeUntil=0;changed=true;
            if(!running()){it.remove();avatars.leave(v.id);telemetry.remove(v.id);floor.remove(v.id);proximity.onPlayerDisconnect(v.id,false,session,now);}
        }}
        if(running()&&!hasLiveOrReserved(now)){abort();return;}
        boolean proxChanged=proximity.tick(running(),session,owner,roomName,roomCode,started,proximityStore,now);
        if(phase==GamePhase.WAITING&&proximity.isEnabled()){
            for(var v:players.values())if(v.ready&&!proximity.isReadyEligible(v.id,now)){v.ready=false;changed=true;proxChanged=true;}
        }
        if(changed)broadcastState();
        if(proxChanged||netChanged)emitProximityUpdate();
        if(!running()||now<deadline)return;
        switch(phase){
            case COUNTDOWN->openQuestion();
            case QUESTION_ACTIVE->{
                String correct=questions.get(questionIndex).path("correct_answer").asText();
                for(var v:players.values()){
                    var earned=AnswerScoring.calculate(v.answer.equals(correct),v.answerElapsedMs,questionSeconds*1000L,v.streak);
                    v.lastPoints=earned.points();v.speedBonus=earned.speedBonus();v.streakBonus=earned.streakBonus();v.streak=earned.streak();v.bestStreak=Math.max(v.bestStreak,v.streak);
                    v.score+=earned.points();if(v.answer.equals(correct))v.correct++;
                }
                phase=GamePhase.SHOW_RESULT;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.resultMillis());
                for(var p:peers)if(participant(p))p.send(envelope("QUESTION_RESULT",null,roundResult(p)));broadcastState();
            }
            case SHOW_RESULT->{phase=GamePhase.SHOW_RANKING;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.rankingMillis());var data=obj().put("session_id",session).put("question_id",questions.get(questionIndex).path("question_id").asInt()).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());data.set("ranking",ranking());emitAll("ROUND_RANKING",data);broadcastState();}
            case SHOW_RANKING->{if(questionIndex+1<questionCount){questionIndex++;openQuestion();}else{
                phase=GamePhase.FINISHED;try{store.saveMatch(session,owner,roomName,questionCount,started,ranking(),questionSeconds,questions);resultSaved=true;}catch(SQLException failure){resultSaved=false;System.err.println("Match persistence failed; clients notified. Session: "+session);}
                proximity.onMatchEnd(session,owner,roomName,roomCode,started,false,proximityStore,System.nanoTime());
                for(var p:peers)if(participant(p))p.send(envelope("GAME_OVER",null,finalResult(p)));broadcastState();emitProximityUpdate();}}
            default->{}
        }
    }
    private void openQuestion(){phase=GamePhase.QUESTION_ACTIVE;players.values().forEach(Player::resetRound);questionOpenedAt=System.nanoTime();deadline=questionOpenedAt+TimeUnit.SECONDS.toNanos(questionSeconds);emitAll("QUESTION",publicQuestion());broadcastState();}
    private void abort(){phase=GamePhase.FINISHED;aborted=true;resultSaved=false;proximity.onMatchEnd(session,owner,roomName,roomCode,started,true,proximityStore,System.nanoTime());emitAll("GAME_ABORTED",obj().put("session_id",session).put("message","Ván thi đã hủy."));broadcastState();emitProximityUpdate();}
    private void resetRoom(){
        players.values().removeIf(v->{if(v.peer!=null&&!v.peer.gone.get()&&!v.peer.closeAfterWrite)return false;
            if(v.peer!=null){v.peer.playerId="";v.peer=null;}avatars.leave(v.id);telemetry.remove(v.id);floor.remove(v.id);proximity.onPlayerDisconnect(v.id,false,"",System.nanoTime());return true;});
        phase=GamePhase.WAITING;roomCode=newCode();roomGeneration=UUID.randomUUID().toString();session="";questions=List.of();proximity.resetForNewMatchInSameRoom();
        players.values().forEach(v->{v.ready=false;v.resetGame();if(v.resumable)v.peer.send(envelope("SESSION_CREDENTIALS",null,issueCredential(v)));});
    }
    private void closeRoom(){
        roomGeneration=UUID.randomUUID().toString();
        for(var v:players.values())if(v.peer!=null){v.peer.send(envelope("ROOM_CLOSED",null,obj().put("message","Giảng viên đã đóng phòng.")));v.peer.closeAfterWrite=true;}
        players.clear();avatars.clear();proximity.resetAll();telemetry.clear();floor.clear();floorVersion++;owner=0;phase=GamePhase.WAITING;roomCode="";roomName="";session="";questions=List.of();questionSetId=0;questionSetName="";historySessionId="";historyRoomName="";
    }
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;serverWifi.close();discovery.close();try{listener.close();}catch(Exception ignored){}clock.shutdownNow();peers.forEach(Peer::close);handshakes.shutdownNow();auth.shutdownNow();
        worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();proximityStore.close();surveyStore.close();store.close();}catch(Exception ignored){}
    }
}
