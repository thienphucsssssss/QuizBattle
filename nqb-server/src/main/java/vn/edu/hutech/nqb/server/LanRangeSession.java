package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;
import static vn.edu.hutech.nqb.server.Wire.envelope;

/** Bounded per-socket ranging state. Async journal callbacks never touch room or JDBC state. */
final class LanRangeSession {
    private static final long SECOND=1_000_000_000L;
    private final String runId;
    private final Options options;
    private final Consumer<ObjectNode> send;
    private final LanRangeJournal journal;
    private final LongSupplier nanoClock;
    private final Clock wallClock;
    private final long created;
    private long readyAt;
    private int next=1;
    private boolean ready,clientDone,finished,healthy=true,missedSchedule;
    private final Map<Integer,Long> waiting=new LinkedHashMap<>();
    private final Map<Integer,Sample> samples=new LinkedHashMap<>();
    private final BitSet clientProbes=new BitSet();
    private final CompletableFuture<Void> completion=new CompletableFuture<>();
    private ObjectNode terminal;
    LanRangeSession(String runId,Options options,Consumer<ObjectNode> send,LanRangeJournal journal,LongSupplier nanoClock,Clock wallClock) {
        this.runId=runId;this.options=options;this.send=send;this.journal=journal;this.nanoClock=nanoClock;this.wallClock=wallClock;
        created=nanoClock.getAsLong();
    }
    String runId(){return runId;}
    synchronized boolean ready(){return ready;}
    synchronized boolean finished(){return finished;}
    CompletableFuture<Void> completion(){return completion;}
    synchronized boolean sawClientProbe(int sequence){return clientProbes.get(sequence);}
    synchronized ObjectNode evidence(){
        var result=object().put("server_summary_received",terminal!=null).put("server_journal_healthy",terminal!=null&&terminal.path("server_journal_healthy").asBoolean());
        if(terminal!=null)result.setAll(terminal);
        result.set("server_summary",LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,options.durationSeconds(),samples.values()));
        var list=result.putArray("samples");samples.values().forEach(s->list.add(samplePayload(s)));return result;
    }
    synchronized void start(){
        if(ready||finished)return;
        ready=true;readyAt=nanoClock.getAsLong();
        record(object().put("record_type","RUN_READY").put("started_at",wallClock.instant().toString()));
        tick();
    }
    synchronized ObjectNode clientProbe(int sequence){
        if(!ready||finished||sequence<1||sequence>options.durationSeconds()||clientProbes.get(sequence))
            throw new IllegalArgumentException("Mẫu không thuộc lượt đo đang chạy hoặc đã nhận.");
        clientProbes.set(sequence);
        return object().put("run_id",runId).put("sequence",sequence);
    }
    synchronized void challengeReply(String requestId,int sequence){
        if(finished||!requestId.equals("range:"+runId+":"+sequence))return;
        Long sent=waiting.remove(sequence);if(sent==null)return;
        long elapsed=nanoClock.getAsLong()-sent;
        add(new Sample(Direction.SERVER_TO_CLIENT,sequence,elapsed<=SECOND?Status.ON_TIME:Status.TIMEOUT,elapsed<=SECOND?elapsed:null));
        checkComplete();
    }
    synchronized void clientComplete(){if(!finished&&ready){clientDone=true;checkComplete();}}
    synchronized void tick(){
        if(finished)return;
        long now=nanoClock.getAsLong();
        if(!ready){if(now-created>10*SECOND)finish(EndReason.CONTROL_TIMEOUT);return;}
        for(var entry:new ArrayList<>(waiting.entrySet()))if(now-entry.getValue()>SECOND){
            waiting.remove(entry.getKey());add(new Sample(Direction.SERVER_TO_CLIENT,entry.getKey(),Status.TIMEOUT,null));
        }
        while(next<=options.durationSeconds()&&now>=readyAt+(next-1)*SECOND){
            int seq=next++;
            if(now-(readyAt+(seq-1)*SECOND)>=SECOND){
                missedSchedule=true;add(new Sample(Direction.SERVER_TO_CLIENT,seq,Status.CANCELLED,null));continue;
            }
            waiting.put(seq,now);
            send.accept(envelope("LAN_RANGE_CHALLENGE","range:"+runId+":"+seq,object().put("run_id",runId).put("sequence",seq)));
            break;
        }
        checkComplete();
        if(!finished&&now-readyAt>(options.durationSeconds()+5L)*SECOND)finish(EndReason.CONTROL_TIMEOUT);
    }
    private void checkComplete(){
        if(clientDone&&samples.size()==options.durationSeconds())finish(missedSchedule?EndReason.CONTROL_TIMEOUT:EndReason.COMPLETED);
    }
    private void add(Sample sample){
        if(samples.putIfAbsent(sample.sequence(),sample)!=null)return;
        var payload=samplePayload(sample).put("run_id",runId);
        record(payload.deepCopy().put("record_type","SAMPLE"));
        send.accept(envelope("LAN_RANGE_SERVER_SAMPLE",null,payload));
    }
    private void record(ObjectNode record){
        journal.append(record.put("survey_id",options.surveyId())).whenComplete((v,e)->{
            if(e!=null)synchronized(this){healthy=false;finish(EndReason.STORAGE_ERROR);}
        });
    }
    synchronized void finish(EndReason reason){
        if(finished)return;
        finished=true;
        for(var seq:new ArrayList<>(waiting.keySet()))
            add(new Sample(Direction.SERVER_TO_CLIENT,seq,reason==EndReason.DISCONNECTED?Status.DISCONNECTED:Status.CANCELLED,null));
        waiting.clear();
        var result=object().put("run_id",runId).put("end_reason",reason.name()).put("ended_at",wallClock.instant().toString());
        result.set("server_summary",LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,options.durationSeconds(),samples.values()));
        journal.append(result.deepCopy().put("record_type","RUN_FINISHED").put("survey_id",options.surveyId()))
            .handle((v,e)->{if(e!=null)synchronized(this){healthy=false;}return null;})
            .thenCompose(v->journal.closeAsync()).whenComplete((v,error)->{
                synchronized(this){
                    boolean saved=healthy&&error==null;
                    result.put("server_journal_healthy",saved);
                    if(!saved)result.put("end_reason",EndReason.STORAGE_ERROR.name());
                    terminal=result.deepCopy();
                    send.accept(envelope("LAN_RANGE_FINISHED",null,result));
                    completion.complete(null);
                }
            });
    }
}
