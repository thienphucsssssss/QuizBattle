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
import java.util.logging.Logger;
import static vn.edu.hutech.nqb.server.Wire.*;

/** One bounded game worker owns all room and JDBC state; one writer per TLS socket. */
public final class QuizServer implements AutoCloseable {
    private static final Logger LOG=Logger.getLogger(QuizServer.class.getName());
    private final ServerConfig config;
    private final QuizStore store;
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
    private String questionSetName="";
    private String roomCode="",roomName="",session="",started="";
    private long deadline,questionOpenedAtNanos;
    private List<ObjectNode> questions=List.of();
    private boolean resultSaved, aborted;
    private static ThreadPoolExecutor pool(String name,int count,int size) {return new ThreadPoolExecutor(count,count,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(size),Thread.ofPlatform().daemon().name(name+"-",0).factory(),new ThreadPoolExecutor.AbortPolicy());}
    private record Attempts(long start,int count) { }
    private static final class Player {
        final String id,nickname,avatarAtJoin,ipAddress; Peer peer; boolean ready; String answer="",avatar; int score,correct,roundPoints; long answerElapsedNanos=-1;
        Player(String id,String nickname,String avatar,Peer peer){this.id=id;this.nickname=nickname;this.avatar=this.avatarAtJoin=avatar;this.peer=peer;this.ipAddress=peer.socket.getInetAddress().getHostAddress();}
    }
    private record Cached(String signature,ObjectNode response) { }
    public QuizServer(ServerConfig config,SSLContext tls,QuizStore store) throws Exception {
        this.config=config;this.store=store;connectionSlots=new Semaphore(config.maxConnections());
        listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();listener.setReuseAddress(true);
        listener.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});listener.bind(new InetSocketAddress(config.bind(),config.port()));
        try{discovery=new LanDiscoveryServer(config.bind(),config.discoveryPort(),port());}catch(Exception error){listener.close();throw error;}
    }
    public int port(){return listener.getLocalPort();}
    public int discoveryPort(){return discovery.port();}
    public void start(){
        discovery.start();
        Thread.ofPlatform().daemon().name("tls-accept").start(this::accept);
        clock.scheduleAtFixedRate(()-> {if(!closed.get()&&tickPending.compareAndSet(false,true)){try{worker.execute(()->{try{tick();}catch(Exception e){System.err.println("Game tick failed; aborting current match.");if(running())abort();}finally{tickPending.set(false);}});}catch(RejectedExecutionException busy){tickPending.set(false);}}},0,50,TimeUnit.MILLISECONDS);
    }
    private void accept(){
        while(!closed.get())try{
            var socket=(SSLSocket)listener.accept();socket.setSoTimeout(5000);socket.setTcpNoDelay(true);
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
            if(type.equals("LOGOUT")||type.equals("LEAVE")){p.send(envelope(type+"_OK",id,obj()));p.closeAfterWrite=true;detach(p,"LEFT");return;}
            if(p.authBusy)throw fail("SERVER_BUSY","Đang xử lý xác thực. Hãy chờ.");
            if(type.equals("LOGIN")){login(p,id,payload);return;}
            if(type.equals("REGISTER")){register(p,id,payload);return;}
            if(type.equals("JOIN")){join(p,id,request);return;}
            if(p.userId==0&&p.playerId.isEmpty())throw fail("AUTH_REQUIRED","Hãy đăng nhập hoặc tham gia phòng.");
            if(type.equals("PASSWORD_CHANGE")){manager(p);changePassword(p,id,payload);return;}
            if(p.mustChange)throw fail("PASSWORD_CHANGE_REQUIRED","Hãy đổi mật khẩu tạm trước khi tiếp tục.");
            if(type.equals("ROOM_STATE")){p.send(envelope("ROOM_STATE_OK",id,state(p,true)));return;}
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
                    case "QUESTION_SET_DELETE"->{int set=integer(payload,"question_set_id",0,1,Integer.MAX_VALUE);if(owner==p.userId&&questionSetId==set)throw fail("QUESTION_SET_IN_USE","Phòng đang dùng bộ này. Đổi bộ trong cấu hình phòng trước khi xóa.");store.deleteQuestionSet(p.userId,set);yield obj();}
                    case "QUESTION_CHECK_DUPLICATE"->store.duplicates(p.userId,text(payload,"content",2000),payload.hasNonNull("exclude_question_id")?integer(payload,"exclude_question_id",0,1,Integer.MAX_VALUE):0);
                    case "QUESTION_CREATE","QUESTION_UPDATE"->store.saveQuestion(p.userId,payload,type.equals("QUESTION_UPDATE"));
                    case "QUESTION_IMPORT_COMMIT"->{if(p.questionImport==null)throw fail("IMPORT_INVALID_STATE","Hãy kiểm tra lại tệp trước khi nhập.");var imported=p.questionImport.commit(store,p.userId,payload);p.questionImport=null;yield imported;}
                    case "QUESTION_DELETE","QUESTION_SET_ACTIVE"->{store.mutateQuestion(p.userId,type,payload);yield obj();}
                    default->throw fail("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.");};
                var response=envelope(type+"_OK",id,value);if(mutation){p.cache.put(id,new Cached(signature,response.deepCopy()));if(p.cache.size()>128)p.cache.remove(p.cache.keySet().iterator().next());}p.send(response);return;
            }
            switch(type){
                case "HISTORY_LIST"->{manager(p);p.send(envelope(type+"_OK",id,store.history(p.userId,payload)));}
                case "HISTORY_GET"->{manager(p);p.send(envelope(type+"_OK",id,store.historyDetail(p.userId,text(payload,"session_id",80))));}
                case "ROOM_CONFIGURE"->{manager(p);waiting();if(owner!=0&&owner!=p.userId)throw fail("ROOM_BUSY","Phòng đang do giảng viên khác tổ chức.");
                    String name=text(payload,"room_name",80);int count=integer(payload,"question_count",10,1,20),seconds=integer(payload,"duration_seconds",15,5,120);
                    int selectedSet=integer(payload,"question_set_id",questionSetId,0,Integer.MAX_VALUE);String selectedName="";
                    if(selectedSet>0){var set=store.questionSet(p.userId,selectedSet);if(set.path("active_count").asInt()<count)throw fail("NOT_ENOUGH_QUESTIONS","Bộ câu hỏi chỉ có "+set.path("active_count").asInt()+" câu đang bật. Hãy giảm số câu hoặc bật thêm câu.");selectedName=set.path("name").asText();}
                    boolean renamed=!roomName.equals(name);
                    if(owner==0){owner=p.userId;roomCode=newCode();}roomName=name;questionCount=count;questionSeconds=seconds;questionSetId=selectedSet;questionSetName=selectedName;
                    if(renamed)for(var v:players.values())track("ROOM_UPDATED",v,v.peer!=null&&!v.peer.gone.get());
                    p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();}
                case "AVATAR_SET"->{Player player=student(p);waiting();if(player.ready)throw fail("ALREADY_READY","Bỏ sẵn sàng trước khi đổi linh vật.");
                    try{avatars.select(player.id,text(payload,"avatar_id",30));}catch(IllegalArgumentException invalid){throw fail("INVALID_AVATAR","Linh vật không hợp lệ.");}catch(IllegalStateException taken){throw new Failure("AVATAR_TAKEN","Linh vật đã có người chọn.",state(p,false));}
                    player.avatar=avatars.snapshot().get(player.id);p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();}
                case "READY_SET"->{Player player=student(p);waiting();player.ready=bool(payload,"ready",false);p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();}
                case "START_GAME"->{host(p);waiting();if(players.size()<2||players.values().stream().anyMatch(v->!v.ready||v.peer==null||v.peer.gone.get()))throw fail("NOT_READY","Cần ít nhất hai người và tất cả sẵn sàng.");
                    questions=store.gameQuestions(owner,questionCount,questionSetId);session=UUID.randomUUID().toString();started=Instant.now().toString();questionIndex=0;resultSaved=false;aborted=false;
                    players.values().forEach(v->{v.score=0;v.correct=0;v.answer="";v.answerElapsedNanos=-1;v.roundPoints=0;});phase=GamePhase.COUNTDOWN;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.countdownMillis());
                    p.send(envelope("START_GAME_OK",id,obj()));emitAll("GAME_START",obj().put("session_id",session).put("remaining_ms",remaining()));broadcastState();}
                case "ANSWER"->answer(p,id,request);
                case "ABORT_GAME"->{host(p);if(!running())throw fail("INVALID_STATE","Không có ván đang chạy.");p.send(envelope(type+"_OK",id,obj()));abort();}
                case "ROOM_RESET","ROOM_CLOSE"->{host(p);if(running())throw fail("INVALID_STATE","Hãy kết thúc ván trước.");
                    if(type.equals("ROOM_CLOSE"))closeRoom();else{
                        // A socket may already be closed before the next tick detaches it.
                        // Remove it while the old room code still identifies its departure.
                        players.values().removeIf(v->{
                            if(v.peer!=null&&!v.peer.gone.get()&&!v.peer.closeAfterWrite)return false;
                            if(v.peer!=null){track("DISCONNECTED",v,false);v.peer.playerId="";v.peer=null;}
                            avatars.leave(v.id);return true;
                        });
                        phase=GamePhase.WAITING;roomCode=newCode();session="";questions=List.of();
                        players.values().forEach(v->{v.ready=false;v.score=0;v.correct=0;v.answer="";v.answerElapsedNanos=-1;v.roundPoints=0;track("ROOM_RESET",v,!v.peer.gone.get());});
                    }
                    p.send(envelope(type+"_OK",id,state(p,false)));broadcastState();}
                default->throw fail("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.");
            }
        }catch(Failure error){p.send(envelope("ERROR",id,error.payload()));}
        catch(Exception error){System.err.println("Request failed: "+type+" / "+error.getClass().getSimpleName());p.send(envelope("ERROR",id,fail("SERVER_ERROR","Máy chủ chưa hoàn tất yêu cầu. Hãy thử lại.").payload()));}
    }
    private ObjectNode publicDirectory()throws SQLException{
        var data=obj().put("server_id",serverId).put("server_name","Quiz Battle · "+serverId.substring(0,4).toUpperCase(Locale.ROOT));var rooms=array();
        if(owner!=0){
            int count=(int)players.values().stream().filter(v->v.peer!=null&&!v.peer.gone.get()).count();
            var host=store.account(owner);
            rooms.add(obj().put("room_code",roomCode).put("room_name",roomName).put("teacher_name",host==null?"Giảng viên":host.name()).put("phase",phase.name()).put("player_count",count).put("max_players",config.maxPlayers()).put("joinable",phase==GamePhase.WAITING&&count<config.maxPlayers()));
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
        String ip=p.socket.getInetAddress().getHostAddress();
        if(players.values().stream().anyMatch(v->v.ipAddress.equals(ip)&&v.peer!=null&&!v.peer.gone.get()&&!v.peer.closeAfterWrite))
            throw fail("IP_ALREADY_IN_USE","Máy/IP này đã có một học sinh đang hoạt động. Hãy rời phiên học sinh hiện tại trước khi tham gia lại.");
        if(players.values().stream().anyMatch(v->TextNormalizer.key(v.nickname).equals(key)))throw fail("NICKNAME_TAKEN","Biệt danh đã có người dùng.");
        if(players.size()>=config.maxPlayers())throw fail("ROOM_FULL","Phòng đã đủ người.");
        String playerId=UUID.randomUUID().toString(),avatar=avatars.join(playerId);var player=new Player(playerId,nickname,avatar,p);players.put(playerId,player);p.playerId=playerId;
        track("JOINED",player,true);
        p.send(envelope("JOIN_OK",id,obj().put("player_id",playerId).put("nickname",nickname).put("avatar_id",avatar).put("room_code",roomCode)));broadcastState();
    }
    private String newCode(){String chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";var r=new java.security.SecureRandom();var b=new StringBuilder();for(int i=0;i<6;i++)b.append(chars.charAt(r.nextInt(chars.length())));return b.toString();}
    private void answer(Peer p,String id,JsonNode request){
        Player player=student(p);JsonNode data=request.path("payload");int qid=integer(data,"question_id",0,1,Integer.MAX_VALUE);String choice=text(data,"answer",1);
        long acceptedAtNanos=System.nanoTime();String reason=null;
        if(!session.equals(request.path("session_id").asText()))reason="WRONG_SESSION";
        else if(questions.isEmpty()||questions.get(questionIndex).path("question_id").asInt()!=qid)reason="WRONG_QUESTION";
        else if(phase!=GamePhase.QUESTION_ACTIVE)reason="INVALID_STATE";
        else if(acceptedAtNanos>=deadline)reason="TIME_EXPIRED";
        else if(!choice.matches("[ABCD]"))reason="INVALID_ANSWER";
        else if(!player.answer.isEmpty())reason="DUPLICATE_ANSWER";
        if(reason==null){player.answer=choice;player.answerElapsedNanos=acceptedAtNanos-questionOpenedAtNanos;}
        var ack=obj().put("session_id",session).put("question_id",qid).put("accepted",reason==null);if(reason!=null)ack.put("reason",reason);
        p.send(envelope("ANSWER_ACK",id,ack));if(reason==null)broadcastState();
    }
    private long remaining(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
    private ObjectNode state(Peer viewer,boolean includeGame){
        var list=array();int ready=0,connected=0,answered=0;
        boolean canTrack=viewer.userId!=0&&(viewer.userId==owner||viewer.role.equals("ADMIN"));
        for(var v:players.values()){
            boolean live=v.peer!=null&&!v.peer.gone.get();if(live){connected++;if(v.ready)ready++;if(!v.answer.isEmpty())answered++;}
            var player=obj().put("player_id",v.id).put("nickname",v.nickname).put("avatar_id",v.avatar).put("ready",v.ready).put("connected",live);
            if(viewer.userId!=0)player.put("answered",!v.answer.isEmpty()).put("score",v.score).put("correct_count",v.correct);
            if(canTrack)player.put("ip_address",v.ipAddress);
            list.add(player);
        }
        var state=obj().put("phase",phase.name()).put("room_code",roomCode).put("room_name",roomName).put("session_id",session).put("is_owner",owner!=0&&owner==viewer.userId)
            .put("question_count",questionCount).put("duration_seconds",questionSeconds).put("question_set_id",questionSetId).put("question_set_name",questionSetName).put("connected_player_count",connected).put("ready_count",ready).put("answered_count",answered).put("remaining_ms",remaining());state.set("players",list);
        state.put("question_number",questions.isEmpty()||phase==GamePhase.COUNTDOWN?0:questionIndex+1);
        if(viewer.userId!=0)state.set("ranking",phase==GamePhase.WAITING?array():ranking());
        if(includeGame&&(viewer.userId==owner||!viewer.playerId.isEmpty())){
            if(!questions.isEmpty()){state.set("question",publicQuestion());if(phase==GamePhase.SHOW_RESULT||phase==GamePhase.SHOW_RANKING)state.set("result",roundResult(viewer));if(phase==GamePhase.FINISHED)state.set("result",finalResult(viewer));}
        }return state;
    }
    private ObjectNode publicQuestion(){var q=questions.get(questionIndex).deepCopy();q.remove(List.of("correct_answer","is_active","updated_at_utc"));return q.put("session_id",session).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());}
    private ArrayNode ranking(){
        var values=new ArrayList<>(players.values());values.sort(Comparator.comparingInt((Player p)->p.score).reversed().thenComparing(p->p.id));var ranks=array();int rank=0,previous=-1;
        for(int i=0;i<values.size();i++){var v=values.get(i);if(v.score!=previous)rank=i+1;previous=v.score;ranks.add(obj().put("player_id",v.id).put("nickname",v.nickname).put("avatar_id",v.avatar).put("score",v.score).put("correct_count",v.correct).put("rank",rank).put("connected",v.peer!=null&&!v.peer.gone.get()));}return ranks;
    }
    private ObjectNode roundResult(Peer viewer){
        var q=questions.get(questionIndex);var data=obj().put("session_id",session).put("question_id",q.path("question_id").asInt()).put("correct_answer",q.path("correct_answer").asText()).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());data.set("ranking",ranking());
        var counts=obj();for(String a:List.of("A","B","C","D"))counts.put(a,players.values().stream().filter(v->v.answer.equals(a)).count());data.set("answer_counts",counts);
        var v=players.get(viewer.playerId);if(v!=null){boolean correct=v.answer.equals(q.path("correct_answer").asText());data.put("your_answer",v.answer).put("correct",correct).put("points",v.roundPoints).put("score",v.score);}return data;
    }
    private ObjectNode finalResult(Peer viewer){var data=obj().put("session_id",session).put("total",questionCount).put("result_saved",resultSaved).put("aborted",aborted);data.set("ranking",ranking());var v=players.get(viewer.playerId);if(v!=null)data.put("score",v.score).put("correct_count",v.correct);return data;}
    private boolean participant(Peer p){return !p.gone.get()&&!p.closeAfterWrite&&((owner!=0&&p.userId==owner)||(!p.playerId.isEmpty()&&players.containsKey(p.playerId)));}
    private void emitAll(String type,ObjectNode value){for(var p:peers)if(participant(p))p.send(envelope(type,null,value));}
    private void broadcastState(){for(var p:peers)if(!p.gone.get()&&!p.closeAfterWrite&&!p.mustChange&&(p.userId!=0||!p.playerId.isEmpty()))p.send(envelope("ROOM_STATE",null,state(p,false)));}
    private void track(String event,Player player,boolean connected){
        var entry=obj().put("timestamp_utc",Instant.now().toString()).put("event",event).put("server_id",serverId)
            .put("player_id",player.id).put("nickname",player.nickname).put("room_code",roomCode).put("room_name",roomName)
            .put("ip_address",player.ipAddress).put("connected",connected);
        LOG.info("NQB_STUDENT_TRACKING "+entry);
    }
    private void detach(Peer p,String event){
        var v=players.get(p.playerId);if(v==null||v.peer!=p)return;v.peer=null;v.ready=false;
        track(event,v,false);
        if(!running()){players.remove(v.id);avatars.leave(v.id);}p.playerId="";
        if(running()&&players.values().stream().noneMatch(player->player.peer!=null&&!player.peer.gone.get()))abort();else broadcastState();
    }
    private void tick()throws SQLException{
        long now=System.nanoTime();boolean changed=false;
        for(var peer:peers)if(peer.questionImport!=null&&peer.questionImport.expired())peer.questionImport=null;
        for(var p:peers){if(now-p.lastSeen>TimeUnit.SECONDS.toNanos(20)||(p.userId==0&&p.playerId.isEmpty()&&now-p.connected>TimeUnit.SECONDS.toNanos(10)))p.close();if(p.gone.get()){detach(p,"DISCONNECTED");peers.remove(p);changed=true;}}
        if(changed)broadcastState();
        if(!running()||now<deadline)return;
        switch(phase){
            case COUNTDOWN->openQuestion();
            case QUESTION_ACTIVE->{
                String correct=questions.get(questionIndex).path("correct_answer").asText();
                for(var v:players.values()){
                    v.roundPoints=SpeedScoring.points(v.answer.equals(correct),v.answerElapsedNanos,TimeUnit.SECONDS.toNanos(questionSeconds));
                    if(v.roundPoints>0){v.score+=v.roundPoints;v.correct++;}
                }
                phase=GamePhase.SHOW_RESULT;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.resultMillis());
                for(var p:peers)if(participant(p))p.send(envelope("QUESTION_RESULT",null,roundResult(p)));broadcastState();
            }
            case SHOW_RESULT->{phase=GamePhase.SHOW_RANKING;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.rankingMillis());var data=obj().put("session_id",session).put("question_id",questions.get(questionIndex).path("question_id").asInt()).put("number",questionIndex+1).put("total",questionCount).put("remaining_ms",remaining());data.set("ranking",ranking());emitAll("ROUND_RANKING",data);broadcastState();}
            case SHOW_RANKING->{if(questionIndex+1<questionCount){questionIndex++;openQuestion();}else{
                phase=GamePhase.FINISHED;try{store.saveMatch(session,owner,roomName,questionCount,started,ranking());resultSaved=true;}catch(SQLException failure){resultSaved=false;System.err.println("Match persistence failed; clients notified. Session: "+session);}
                for(var p:peers)if(participant(p))p.send(envelope("GAME_OVER",null,finalResult(p)));broadcastState();}}
            default->{}
        }
    }
    private void openQuestion(){phase=GamePhase.QUESTION_ACTIVE;players.values().forEach(v->{v.answer="";v.answerElapsedNanos=-1;v.roundPoints=0;});questionOpenedAtNanos=System.nanoTime();deadline=questionOpenedAtNanos+TimeUnit.SECONDS.toNanos(questionSeconds);emitAll("QUESTION",publicQuestion());broadcastState();}
    private void abort(){phase=GamePhase.FINISHED;aborted=true;resultSaved=false;emitAll("GAME_ABORTED",obj().put("session_id",session).put("message","Ván thi đã hủy."));broadcastState();}
    private void closeRoom(){
        for(var v:players.values())track("ROOM_CLOSED",v,false);
        for(var v:players.values())if(v.peer!=null){v.peer.send(envelope("ROOM_CLOSED",null,obj().put("message","Giảng viên đã đóng phòng.")));v.peer.closeAfterWrite=true;}
        players.clear();avatars.clear();owner=0;phase=GamePhase.WAITING;roomCode="";roomName="";session="";questions=List.of();questionSetId=0;questionSetName="";
    }
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;discovery.close();try{listener.close();}catch(Exception ignored){}clock.shutdownNow();peers.forEach(Peer::close);handshakes.shutdownNow();auth.shutdownNow();
        worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();store.close();}catch(Exception ignored){}
    }
}
