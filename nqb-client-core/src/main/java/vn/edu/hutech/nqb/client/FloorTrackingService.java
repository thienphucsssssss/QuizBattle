package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** One scan/request at a time, sharing the native Wi-Fi worker. No JavaFX calls here. */
public final class FloorTrackingService implements AutoCloseable {
    private final ProximityClientService wifi;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("floor-tracking").factory());
    private final AtomicLong generation=new AtomicLong();
    private final AtomicLong sequence=new AtomicLong();
    private String context="";
    private ScheduledFuture<?> next;
    private volatile boolean closed;
    public FloorTrackingService(ProximityClientService wifi){this.wifi=wifi;}
    public synchronized void configure(QuizGateway gateway,JsonNode config,Consumer<JsonNode> result){
        String key=config.path("enabled").asBoolean()?config.path("epoch").asText():"";
        if(closed||key.equals(context))return;
        stop();if(key.isBlank()||gateway==null)return;context=key;
        long token=generation.get();String ssid=config.path("ssid").asText();
        next=worker.schedule(()->step(gateway,key,ssid,token,result),0,TimeUnit.MILLISECONDS);
    }
    private void step(QuizGateway gateway,String epoch,String ssid,long token,Consumer<JsonNode> result){
        if(!active(token))return;
        wifi.scanAdaptersAsync().thenCompose(adapters->{
            if(!active(token))return CompletableFuture.failedFuture(new CancellationException());
            var adapter=adapters.stream().filter(WifiScanProvider.Adapter::connected).findFirst().or(()->adapters.stream().findFirst());
            if(adapter.isEmpty())return CompletableFuture.<JsonNode>completedFuture(null);
            return wifi.scanAsync(adapter.get().id(),ssid).thenCompose(snapshot->{
                if(!active(token))return CompletableFuture.failedFuture(new CancellationException());
                var scan=new Scan(snapshot.scanId(),adapter.get().id(),adapter.get().name(),ssid,snapshot.associatedBssid(),snapshot.startedAtMillis(),snapshot.completedAtMillis(),snapshot.status().name(),snapshot.diagnostic(),snapshot.omittedCount(),
                    snapshot.observations().stream().map(o->new Observation(o.ssid(),o.bssid(),o.rssiDbm(),o.frequencyKhz(),o.hostTimestamp100ns(),o.ageMillis(),o.fresh(),o.associated())).toList());
                var payload=QuizGateway.object().put("epoch",epoch).put("sequence",sequence.incrementAndGet());payload.set("scan",JSON.valueToTree(scan));
                return gateway.request("FLOOR_REPORT",payload);
            });
        }).whenComplete((value,error)->{
            if(!active(token))return;
            if(error==null&&value!=null&&result!=null)result.accept(value);
            synchronized(this){if(active(token))next=worker.schedule(()->step(gateway,epoch,ssid,token,result),5,TimeUnit.SECONDS);}
        });
    }
    private boolean active(long token){return !closed&&token==generation.get();}
    public synchronized void stop(){generation.incrementAndGet();context="";if(next!=null)next.cancel(false);next=null;}
    @Override public synchronized void close(){closed=true;stop();worker.shutdownNow();}
}
