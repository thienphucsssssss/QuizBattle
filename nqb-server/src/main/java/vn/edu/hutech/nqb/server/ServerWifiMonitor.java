package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import vn.edu.hutech.nqb.client.WifiSignalProvider;
import vn.edu.hutech.nqb.shared.AutomaticApDistance;

/** Reads the physical Server's Wi-Fi on its own worker, never the teacher's workstation. */
final class ServerWifiMonitor implements AutoCloseable {
    private final AutomaticApDistance estimate=new AutomaticApDistance();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("server-wifi").factory());
    private final Supplier<WifiSignalProvider> factory;
    private WifiSignalProvider provider;
    private volatile boolean closed;
    private boolean started;
    ServerWifiMonitor(){this(WifiSignalProvider::createDefault);}
    ServerWifiMonitor(Supplier<WifiSignalProvider> factory){this.factory=factory;}
    synchronized void start(){if(!started&&!closed){started=true;worker.scheduleWithFixedDelay(this::sample,0,1,TimeUnit.SECONDS);}}
    void sample(){
        if(closed)return;
        try{
            if(provider==null)provider=factory.get();var reading=provider.readSignal();if(closed)return;
            if(reading.isConnected())estimate.accept(reading.adapterId(),reading.ssid(),reading.bssid(),reading.band(),List.of(reading.rssiDbm()),System.nanoTime());
            else estimate.unavailable(reading.status().name(),System.nanoTime());
        }catch(RuntimeException|LinkageError error){if(!closed)estimate.unavailable("API_ERROR",System.nanoTime());}
    }
    ObjectNode snapshot(long now){return estimate.snapshot(!closed,now).put("device","SERVER");}
    @Override public synchronized void close(){
        if(closed)return;closed=true;estimate.unavailable("DISCONNECTED",System.nanoTime());
        // Queued cleanup cannot race an in-flight native read and does not block the game worker.
        worker.execute(()->{if(provider!=null)provider.close();});worker.shutdown();
    }
}
