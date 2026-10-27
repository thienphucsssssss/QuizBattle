package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

/** Owns one connection's diagnostic runs; no JavaFX and no cross-machine timestamp subtraction. */
public final class LanRangeClient implements AutoCloseable {
    private final QuizGateway gateway;
    private final Path directory;
    private final Consumer<JsonNode> observer;
    private final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("lan-range-client").factory());
    private volatile JsonNode latest=object().put("active",false).put("phase","IDLE");
    private Run current;
    private RemoteRun remote;
    private volatile boolean closed;
    private static final long SECOND=1_000_000_000L;
    private static final class Run {
        final Options options;
        final String controlId;
        final CompletableFuture<JsonNode> started=new CompletableFuture<>();
        final CompletableFuture<Void> complete=new CompletableFuture<>();
        final Map<Integer,Sample> client=new LinkedHashMap<>(),server=new LinkedHashMap<>();
        final Map<Integer,Long> waiting=new LinkedHashMap<>();
        ObjectNode metadata;
        LanRangeJournal journal;
        ScheduledFuture<?> ticker;
        int next=1;long t0;
        boolean measuring,finishing,finished,clientDone,serverReceived,serverHealthy,clientHealthy=true,saved,missed;
        EndReason reason;
        String error="";
        Run(Options options,String controlId){this.options=options;this.controlId=controlId;metadata=optionsPayload(options);if(!controlId.isBlank())metadata.put("control_id",controlId);}
        String id(){return metadata.path("run_id").asText();}
    }
    public LanRangeClient(QuizGateway gateway,Path journalDirectory,Consumer<JsonNode> onSnapshot){
        this.gateway=Objects.requireNonNull(gateway);directory=journalDirectory;observer=onSnapshot;
    }
    public JsonNode snapshot(){return latest.deepCopy();}
    private void execute(Runnable action){try{loop.execute(action);}catch(RejectedExecutionException ignored){/* A closed connection cannot revive a run. */}}
    public CompletableFuture<JsonNode> start(Options options){
        return startLocal(options,"");
    }
    private CompletableFuture<JsonNode> startLocal(Options options,String controlId){
        if(closed||loop.isShutdown())return CompletableFuture.failedFuture(new IllegalStateException("Kết nối đo đã đóng."));
        var result=new CompletableFuture<JsonNode>();
        execute(()->{
            if(closed||(current!=null&&!current.finished)||(remote!=null&&!remote.finished)){
                result.completeExceptionally(new IllegalStateException("Lượt đo chưa kết thúc hoặc kết nối đã đóng."));return;
            }
            var run=new Run(options,controlId);current=run;remote=null;run.started.whenComplete((v,e)->{if(e!=null)result.completeExceptionally(e);else result.complete(v);});
            publish(run);
            try{
                Files.createDirectories(directory);
                Path probe=Files.createTempFile(directory,"write-check-",".tmp");Files.delete(probe);
            }catch(Exception error){startFailed(run,new GatewayException("STORAGE_ERROR","Không ghi được thư mục nhật ký: "+directory,object()));return;}
            gateway.request("LAN_RANGE_START",run.metadata.deepCopy()).whenComplete((data,error)->execute(()->{
                if(run.finished){if(data!=null)gateway.request("LAN_RANGE_STOP",object().put("run_id",data.path("run_id").asText()));return;}
                if(error!=null){startFailed(run,unwrap(error));return;}
                run.metadata=((ObjectNode)data).deepCopy();
                try{run.journal=new LanRangeJournal(directory,"client",run.id());}
                catch(Exception invalid){startFailed(run,invalid);return;}
                run.journal.append(run.metadata.deepCopy().put("record_type","RUN_STARTED")).whenComplete((v,writeError)->execute(()->{
                    if(run.finishing||run.finished)return;
                    if(writeError!=null){storageError(run,writeError);return;}
                    gateway.request("LAN_RANGE_READY",object().put("run_id",run.id())).whenComplete((ready,readyError)->execute(()->{
                        if(run.finishing||run.finished)return;
                        if(readyError!=null){finish(run,EndReason.CONTROL_TIMEOUT);run.started.completeExceptionally(unwrap(readyError));return;}
                        run.measuring=true;run.t0=System.nanoTime();
                        log(run,object().put("record_type","RUN_READY").put("started_at",Instant.now().toString()));
                        run.ticker=loop.scheduleAtFixedRate(()->tick(run),0,50,TimeUnit.MILLISECONDS);
                        publish(run);run.started.complete(snapshot());
                    }));
                }));
            }));
        });
        return result;
    }
    private void startFailed(Run run,Throwable error){
        run.finished=true;run.error=error.getMessage();run.started.completeExceptionally(error);publish(run);run.complete.complete(null);
    }
    private void tick(Run run){
        if(run!=current||run.finishing||run.finished)return;
        long now=System.nanoTime();
        while(run.next<=run.options.durationSeconds()&&now>=run.t0+(run.next-1)*SECOND){
            int seq=run.next++;
            if(now-(run.t0+(seq-1)*SECOND)>=SECOND){run.missed=true;add(run,new Sample(Direction.CLIENT_TO_SERVER,seq,Status.CANCELLED,null));continue;}
            long sent=System.nanoTime();run.waiting.put(seq,sent);
            gateway.request("LAN_RANGE_PROBE",object().put("run_id",run.id()).put("sequence",seq)).whenComplete((reply,error)->{
                long received=System.nanoTime();
                execute(()->{
                    if(run.finishing||run.finished||run!=current||run.waiting.remove(seq)==null)return;
                    long elapsed=received-sent;Status status;
                    if(error!=null){
                        Throwable root=unwrap(error);String code=root instanceof GatewayException e?e.code():"SEND_FAILED";
                        status=code.equals("TIMEOUT")?Status.TIMEOUT:code.equals("DISCONNECTED")?Status.DISCONNECTED:Status.SEND_FAILED;
                    }else if(!run.id().equals(reply.path("run_id").asText())||reply.path("sequence").asInt()!=seq)status=Status.SEND_FAILED;
                    else status=elapsed<=SECOND?Status.ON_TIME:Status.TIMEOUT;
                    add(run,new Sample(Direction.CLIENT_TO_SERVER,seq,status,status==Status.ON_TIME?elapsed:null));
                    clientCompleted(run);
                });
            });
            break;
        }
        clientCompleted(run);
        if(now-run.t0>(run.options.durationSeconds()+6L)*SECOND){gateway.request("LAN_RANGE_STOP",object().put("run_id",run.id()));finish(run,EndReason.CONTROL_TIMEOUT);}
    }
    private void clientCompleted(Run run){
        if(run.finishing||run.finished||run.clientDone||run.client.size()!=run.options.durationSeconds())return;
        run.clientDone=true;
        gateway.request("LAN_RANGE_COMPLETE",object().put("run_id",run.id())).whenComplete((v,e)->{
            if(e!=null)execute(()->{if(!run.finishing&&!run.finished)finish(run,EndReason.CONTROL_TIMEOUT);});
        });
    }
    private void add(Run run,Sample sample){
        var map=sample.direction()==Direction.CLIENT_TO_SERVER?run.client:run.server;
        if(map.putIfAbsent(sample.sequence(),sample)!=null)return;
        log(run,samplePayload(sample).put("record_type","SAMPLE"));publish(run);
    }
    private void log(Run run,ObjectNode record){
        if(run.journal==null)return;
        run.journal.append(record.put("survey_id",run.options.surveyId())).whenComplete((v,error)->{
            if(error!=null)execute(()->storageError(run,error));
        });
    }
    private void storageError(Run run,Throwable error){
        run.clientHealthy=false;run.error="Không lưu đủ nhật ký: "+directory;
        if(!run.finishing&&!run.finished){if(!run.id().isEmpty())gateway.request("LAN_RANGE_STOP",object().put("run_id",run.id()));finish(run,EndReason.STORAGE_ERROR);}
    }
    public void onEvent(JsonNode event){
        JsonNode copy=event.deepCopy();
        if(copy.path("type").asText().equals("LAN_RANGE_REMOTE_REQUEST")){
            var request=copy.path("payload");String control=request.path("control_id").asText();
            try{startLocal(parseOptions(request),control).whenComplete((v,error)->{if(error!=null)gateway.request("LAN_RANGE_REMOTE_REPORT",object().put("control_id",control).set("snapshot",object().put("error",unwrap(error).getMessage())));});}
            catch(IllegalArgumentException invalid){gateway.request("LAN_RANGE_REMOTE_REPORT",object().put("control_id",control).set("snapshot",object().put("error",invalid.getMessage())));}return;
        }
        execute(()->{
            String remoteType=copy.path("type").asText();var remotePayload=copy.path("payload");
            if(remote!=null&&!remote.finished){
                var watched=remote;
                if(remoteType.equals("DISCONNECTED")){endRemote(watched,EndReason.DISCONNECTED,"Mất kết nối với Server.");return;}
                if(watched.id.equals(remotePayload.path("control_id").asText())){
                    if(remoteType.equals("LAN_RANGE_REMOTE_STATE"))receiveRemote(watched,(ObjectNode)remotePayload.path("snapshot"));
                    else if(remoteType.equals("LAN_RANGE_REMOTE_FAILED"))endRemote(watched,EndReason.CONTROL_TIMEOUT,remotePayload.path("error").asText());
                    return;
                }
            }
            var run=current;if(run==null||run.finished||run.finishing)return;
            String type=copy.path("type").asText();var p=copy.path("payload");
            if(type.equals("LAN_RANGE_REMOTE_CANCEL")&&run.controlId.equals(p.path("control_id").asText())){EndReason why;try{why=EndReason.valueOf(p.path("end_reason").asText());}catch(IllegalArgumentException invalid){why=EndReason.STOPPED;}stop(why);return;}
            if(type.equals("DISCONNECTED")){finish(run,EndReason.DISCONNECTED);return;}
            if(type.equals("GAME_START")){finish(run,EndReason.GAME_STARTED);return;}
            if(type.equals("ROOM_CLOSED")){finish(run,EndReason.ROOM_CHANGED);return;}
            if(!run.id().equals(p.path("run_id").asText())||run.id().isEmpty())return;
            if(type.equals("LAN_RANGE_SERVER_SAMPLE")){
                try{var sample=parseSample(p);if(sample.direction()==Direction.SERVER_TO_CLIENT&&sample.sequence()<=run.options.durationSeconds())add(run,sample);}
                catch(IllegalArgumentException invalid){finish(run,EndReason.CONTROL_TIMEOUT);}
            }else if(type.equals("LAN_RANGE_FINISHED")){
                run.serverReceived=true;run.serverHealthy=p.path("server_journal_healthy").asBoolean();
                EndReason reason;
                try{reason=EndReason.valueOf(p.path("end_reason").asText());}catch(IllegalArgumentException invalid){reason=EndReason.CONTROL_TIMEOUT;}
                if(reason==EndReason.COMPLETED&&(!run.clientDone||run.client.size()!=run.options.durationSeconds()
                    ||run.server.size()!=run.options.durationSeconds()||run.missed))reason=EndReason.CONTROL_TIMEOUT;
                finish(run,reason);
            }
        });
    }
    public CompletableFuture<Void> stop(EndReason reason){
        var result=new CompletableFuture<Void>();
        if(loop.isShutdown())return CompletableFuture.completedFuture(null);
        execute(()->{
            if(remote!=null&&!remote.finished){var watched=remote;
                gateway.request("LAN_RANGE_REMOTE_STOP",object().put("control_id",watched.id));
                loop.schedule(()->{if(!watched.finished&&!watched.finishing)endRemote(watched,reason,"Chưa nhận được tổng kết Client.");},3,TimeUnit.SECONDS);
                watched.complete.whenComplete((v,e)->{if(e!=null)result.completeExceptionally(e);else result.complete(null);});return;
            }
            var run=current;
            if(run==null){result.complete(null);return;}
            if(!run.finishing&&!run.finished){
                if(!run.id().isEmpty())gateway.request("LAN_RANGE_STOP",object().put("run_id",run.id()));
                finish(run,reason);
            }
            run.complete.whenComplete((v,e)->{if(e!=null)result.completeExceptionally(e);else result.complete(null);});
        });
        return result;
    }
    private void finish(Run run,EndReason reason){
        if(run.finishing||run.finished)return;
        run.finishing=true;run.measuring=false;run.reason=reason;
        if(run.ticker!=null)run.ticker.cancel(false);
        for(int seq:new ArrayList<>(run.waiting.keySet()))add(run,new Sample(Direction.CLIENT_TO_SERVER,seq,
            reason==EndReason.DISCONNECTED?Status.DISCONNECTED:Status.CANCELLED,null));
        run.waiting.clear();
        if(!run.started.isDone())run.started.completeExceptionally(new CancellationException("Lượt đo đã dừng."));
        publish(run);
        if(run.journal==null){run.finished=true;run.finishing=false;publish(run);run.complete.complete(null);return;}
        var finalRecord=build(run,true).put("record_type","RUN_FINISHED");
        run.journal.append(finalRecord).handle((v,error)->{
            if(error!=null)run.clientHealthy=false;return null;
        }).thenCompose(v->run.journal.closeAsync()).whenComplete((v,error)->execute(()->{
            run.saved=error==null&&run.clientHealthy;
            if(!run.saved){run.reason=EndReason.STORAGE_ERROR;run.clientHealthy=false;run.error="Không lưu đủ nhật ký: "+directory;}
            run.finished=true;run.finishing=false;publish(run);run.complete.complete(null);
        }));
    }
    private ObjectNode build(Run run,boolean terminal){
        var result=run.metadata.deepCopy().put("active",!terminal&&!run.finished)
            .put("phase",terminal||run.finished?"FINISHED":run.finishing?"SAVING":run.measuring?"MEASURING":"PREPARING")
            .put("saved",terminal||run.saved).put("server_summary_received",run.serverReceived)
            .put("server_journal_healthy",run.serverHealthy).put("client_journal_healthy",run.clientHealthy).put("error",run.error);
        if(run.reason!=null)result.put("end_reason",run.reason.name());else result.putNull("end_reason");
        if(run.reason!=null)result.put("ended_at",Instant.now().toString());
        if(run.journal!=null)result.put("journal_path",run.journal.path().toAbsolutePath().toString());
        result.set("client_summary",LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,run.options.durationSeconds(),run.client.values()));
        result.set("server_summary",LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,run.options.durationSeconds(),run.server.values()));
        var samples=result.putArray("samples");run.client.values().forEach(s->samples.add(samplePayload(s)));run.server.values().forEach(s->samples.add(samplePayload(s)));
        result.put("passed",(terminal||run.saved)&&run.serverReceived&&run.serverHealthy&&
            LanRangeStatistics.passes(result.path("client_summary"),result.path("server_summary"),run.reason,run.clientHealthy));
        return result;
    }
    private void publish(Run run){
        latest=build(run,false);
        try{observer.accept(latest.deepCopy());}catch(RuntimeException ignored){/* UI observers do not own the network run. */}
        if(!run.controlId.isBlank()&&(latest.has("run_id")||!run.error.isBlank()))gateway.request("LAN_RANGE_REMOTE_REPORT",object().put("control_id",run.controlId).set("snapshot",latest.deepCopy()));
    }
    private static final class RemoteRun {
        final String id=UUID.randomUUID().toString();final CompletableFuture<Void> complete=new CompletableFuture<>();
        final Set<String> recorded=new HashSet<>();ObjectNode state;LanRangeJournal journal;boolean finishing,finished,healthy=true;
        RemoteRun(String player,Options options){state=optionsPayload(options).put("control_id",id).put("player_id",player).put("active",true).put("phase","PREPARING").put("saved",false);}
    }
    /** Controls a real student socket; probes never originate on the manager connection. */
    public CompletableFuture<JsonNode> startFor(String playerId,Options options){
        if(closed||loop.isShutdown())return CompletableFuture.failedFuture(new IllegalStateException("Kết nối đo đã đóng."));
        var result=new CompletableFuture<JsonNode>();execute(()->{
            if(closed||(current!=null&&!current.finished)||(remote!=null&&!remote.finished)){result.completeExceptionally(new IllegalStateException("Hãy chờ lượt đang đo kết thúc."));return;}
            if(playerId==null||playerId.isBlank()){result.completeExceptionally(new IllegalArgumentException("Chọn client cần đo."));return;}
            var watched=new RemoteRun(playerId,options);remote=watched;publishRemote(watched);
            gateway.request("LAN_RANGE_REMOTE_START",watched.state.deepCopy()).whenComplete((reply,error)->execute(()->{
                if(error!=null){endRemote(watched,EndReason.CONTROL_TIMEOUT,unwrap(error).getMessage());result.completeExceptionally(unwrap(error));return;}
                if(!watched.finishing&&!watched.finished&&!watched.state.has("run_id")){watched.state.setAll((ObjectNode)reply);publishRemote(watched);}
                result.complete(reply);
            }));
        });return result;
    }
    private void publishRemote(RemoteRun watched){if(remote!=watched)return;latest=watched.state.deepCopy();try{observer.accept(latest.deepCopy());}catch(RuntimeException ignored){}}
    private void endRemote(RemoteRun watched,EndReason reason,String error){
        if(watched.finishing||watched.finished)return;
        receiveRemote(watched,watched.state.deepCopy().put("active",false).put("saved",false).put("phase","FINISHED").put("passed",false)
            .put("server_summary_received",false).put("end_reason",reason.name()).put("error",error==null?"Lượt đo không hoàn tất.":error));
    }
    private void receiveRemote(RemoteRun watched,ObjectNode snapshot){
        if(watched.finishing||watched.finished||remote!=watched)return;
        watched.state=snapshot.deepCopy().put("control_id",watched.id);
        try{
            if(snapshot.has("run_id")){
                if(watched.journal==null){watched.journal=new LanRangeJournal(directory.resolve("controlled"),"client",snapshot.path("run_id").asText());remoteRecord(watched,snapshot.deepCopy().put("record_type","RUN_STARTED"));}
                for(var sample:snapshot.path("samples")){var parsed=parseSample(sample);if(watched.recorded.add(parsed.direction()+":"+parsed.sequence()))remoteRecord(watched,samplePayload(parsed).put("record_type","SAMPLE"));}
            }
        }catch(RuntimeException failure){watched.healthy=false;watched.state.put("active",false).put("passed",false).put("end_reason","STORAGE_ERROR").put("error","Không lưu được nhật ký máy quản lý.");gateway.request("LAN_RANGE_REMOTE_STOP",object().put("control_id",watched.id));}
        if(watched.state.path("active").asBoolean()){watched.state.put("saved",false);publishRemote(watched);return;}
        boolean reportedPass=watched.state.path("passed").asBoolean();
        watched.finishing=true;watched.state.put("active",true).put("phase","SAVING").put("saved",false).put("passed",false);publishRemote(watched);
        var finishing=snapshot.deepCopy().put("record_type","RUN_FINISHED");
        if(watched.journal==null){watched.state.put("active",false).put("phase","FINISHED");watched.finished=true;watched.finishing=false;publishRemote(watched);watched.complete.complete(null);return;}
        remoteRecord(watched,finishing);
        watched.journal.closeAsync().whenComplete((v,error)->execute(()->{
            watched.finished=true;watched.finishing=false;watched.state.put("active",false).put("phase","FINISHED").put("saved",error==null&&watched.healthy).put("passed",reportedPass&&error==null&&watched.healthy);
            if(error!=null||!watched.healthy)watched.state.put("passed",false).put("end_reason","STORAGE_ERROR").put("error","Không lưu đủ nhật ký máy quản lý.");
            publishRemote(watched);watched.complete.complete(null);
        }));
    }
    private void remoteRecord(RemoteRun watched,ObjectNode record){watched.journal.append(record).whenComplete((v,error)->{if(error!=null)execute(()->{
        watched.healthy=false;watched.state.put("passed",false).put("error","Không lưu đủ nhật ký máy quản lý.");
        if(!watched.finished&&!watched.finishing){gateway.request("LAN_RANGE_REMOTE_STOP",object().put("control_id",watched.id));endRemote(watched,EndReason.STORAGE_ERROR,"Không lưu đủ nhật ký máy quản lý.");}
    });});}
    private static Throwable unwrap(Throwable e){while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();return e;}
    @Override public void close(){
        closed=true;stop(EndReason.APP_CLOSED).whenComplete((v,e)->loop.shutdown());
    }
}
