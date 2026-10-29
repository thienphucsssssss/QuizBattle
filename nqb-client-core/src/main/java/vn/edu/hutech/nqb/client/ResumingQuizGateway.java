package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.LanTelemetryRules;
import vn.edu.hutech.nqb.shared.ResumeRules;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Student-session recovery. Never buffers or replays application commands/answers. */
public final class ResumingQuizGateway implements QuizGateway {
    @FunctionalInterface interface Connector { QuizGateway connect() throws Exception; }
    private final Connector connector;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("session-resume").factory());
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Consumer<JsonNode> listener=ignored->{};
    private volatile QuizGateway transport;
    private CompletableFuture<JsonNode> leaving;
    private JsonNode credential;
    private String token="",candidate="";
    private volatile boolean recovering;
    private int attempt;
    private long until;
    private volatile long transportGeneration;

    public static ResumingQuizGateway connect(Endpoint endpoint,ClientConnectionProfile profile)throws Exception {
        Connector connector=()->{char[] password=profile.truststorePassword().toCharArray();
            try{return TlsQuizGateway.connect(endpoint,profile.truststore(),password);}finally{Arrays.fill(password,'\0');}};
        return new ResumingQuizGateway(connector.connect(),connector);
    }
    ResumingQuizGateway(QuizGateway initial,Connector connector){this.connector=connector;attach(initial);}
    private void enqueue(Runnable action){try{worker.execute(action);}catch(RejectedExecutionException ignored){}}
    private void attach(QuizGateway next){
        transport=next;long generation=++transportGeneration;
        next.onEvent(event->enqueue(()->{if(!closed.get()&&generation==transportGeneration)receive(event);}));
    }
    @Override public CompletableFuture<JsonNode> request(String type,ObjectNode payload){
        var result=new CompletableFuture<JsonNode>();
        var requestedTransport=transport;long requestedGeneration=transportGeneration;
        if(closed.get()||recovering||requestedTransport==null)return CompletableFuture.failedFuture(disconnected());
        var body=payload.deepCopy();
        enqueue(()->{
            if(closed.get()){result.completeExceptionally(disconnected());return;}
            if(type.equals("LEAVE")||type.equals("LOGOUT")){credential=null;token=candidate="";recovering=false;}
            if(transport==null||recovering||transport!=requestedTransport||transportGeneration!=requestedGeneration){result.completeExceptionally(disconnected());return;}
            if(type.equals("JOIN"))body.putArray("client_capabilities").add(LanTelemetryRules.CAPABILITY).add(ResumeRules.CAPABILITY);
            var active=transport;
            var sent=active.request(type,body);
            if(type.equals("LEAVE")||type.equals("LOGOUT"))leaving=sent;
            sent.whenComplete((value,error)->enqueue(()->{
                if(error!=null)result.completeExceptionally(error);
                else {
                    if(type.equals("JOIN")&&active==transport&&!closed.get()&&value.has("resume"))setCredential(value.path("resume"));
                    result.complete(value);
                }
            }));
        });
        // Also bounds futures when close races with a scheduled completion.
        return result.orTimeout(12,TimeUnit.SECONDS);
    }
    private void setCredential(JsonNode data){
        credential=data.deepCopy();token=data.path("token").asText();candidate="";
        if(!ResumeRules.valid(token))credential=null;
    }
    private void receive(JsonNode event){
        String type=event.path("type").asText();
        if(type.equals("SESSION_CREDENTIALS")){setCredential(event.path("payload"));return;}
        if(type.equals("ROOM_CLOSED")){credential=null;token=candidate="";}
        if(type.equals("DISCONNECTED")){
            transport=null;
            if(credential==null){emit("DISCONNECTED",object());return;}
            if(!recovering){recovering=true;attempt=0;until=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.max(1,Math.min(300,credential.path("grace_seconds").asInt(60))));emit("RECONNECTING",object().put("grace_seconds",credential.path("grace_seconds").asInt(60)));}
            worker.schedule(this::retry,500,TimeUnit.MILLISECONDS);return;
        }
        if(!recovering)listener.accept(event);
    }
    private void retry(){
        if(closed.get()||!recovering||credential==null)return;
        if(System.nanoTime()>=until){giveUp();return;}
        QuizGateway next=null;
        try {
            next=connector.connect();
            if(closed.get()||System.nanoTime()>=until){next.close();if(!closed.get())giveUp();return;}
            attach(next); // Queue state transitions until RESUME has restored the snapshot.
            // The client chooses the successor before sending. If the ACK was lost,
            // try that successor first; the Server never needs to accept an old token.
            String presented=candidate.isEmpty()?token:candidate;
            String successor=ResumeRules.token();
            JsonNode state;
            try{state=resume(next,presented,successor);}
            catch(ExecutionException rejected){
                if(candidate.isEmpty()||!(rejected.getCause() instanceof GatewayException ge)||!ge.code().equals("RESUME_REJECTED"))throw rejected;
                state=resume(next,token,successor);
            }
            token=successor;candidate="";recovering=false;
            if(!closed.get())emit("RECONNECTED",state);
        } catch(Exception failure){
            transportGeneration++;transport=null; // Ignore callbacks from the failed attempt.
            if(next!=null)next.close();
            Throwable cause=failure instanceof ExecutionException?failure.getCause():failure;
            if(cause instanceof GatewayException ge&&ge.code().equals("RESUME_REJECTED")){giveUp();return;}
            if(!closed.get()&&recovering)worker.schedule(this::retry,Math.min(5000,500L<<Math.min(4,attempt++)),TimeUnit.MILLISECONDS);
        }
    }
    private JsonNode resume(QuizGateway next,String presented,String successor)throws Exception {
        String previousCandidate=candidate;
        candidate=successor;
        try {
            return next.request("RESUME",object().put("player_id",credential.path("player_id").asText())
                .put("room_generation",credential.path("room_generation").asText()).put("room_code",credential.path("room_code").asText())
                .put("token",presented).put("next_token",successor)).get(6,TimeUnit.SECONDS);
        }catch(ExecutionException error){
            if(error.getCause() instanceof GatewayException ge&&ge.code().equals("RESUME_REJECTED"))candidate=previousCandidate;
            else token=presented;
            throw error;
        }catch(Exception uncertain){token=presented;throw uncertain;}
    }
    private void giveUp(){recovering=false;credential=null;token=candidate="";emit("DISCONNECTED",object().put("resume_expired",true));}
    private void emit(String type,JsonNode payload){listener.accept(object().put("type",type).set("payload",payload));}
    private static GatewayException disconnected(){return new GatewayException("DISCONNECTED","Đang mất kết nối với máy chủ; yêu cầu chưa được gửi.",object());}
    @Override public void onEvent(Consumer<JsonNode> listener){this.listener=listener;}
    @Override public boolean isPreview(){return false;}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        enqueue(()->{
            var active=transport;transport=null;recovering=false;token=candidate="";
            if(active!=null&&leaving!=null)leaving.orTimeout(750,TimeUnit.MILLISECONDS).whenComplete((v,e)->active.close());
            else if(active!=null&&credential!=null)active.request("LEAVE",object()).orTimeout(750,TimeUnit.MILLISECONDS).whenComplete((v,e)->active.close());
            else if(active!=null)active.close();
            credential=null;worker.shutdown();
        });
    }
}
