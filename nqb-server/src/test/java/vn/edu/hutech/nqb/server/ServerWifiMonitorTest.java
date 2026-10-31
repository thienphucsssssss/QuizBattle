package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import vn.edu.hutech.nqb.client.WifiSignalProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ServerWifiMonitorTest {
    static final class Fixture implements WifiSignalProvider {
        volatile Reading reading=Reading.connected("server-card","Server","Server Wi-Fi","aa:bb:cc:dd:ee:fe","5 GHz",36,5180000,-46,90,System.currentTimeMillis());
        final CountDownLatch closed=new CountDownLatch(1);
        @Override public Reading readSignal(){return reading;}
        @Override public void close(){closed.countDown();}
    }
    @Test void serverUsesItsOwnAdapterAndClosesItOnTheWorker()throws Exception{
        var wifi=new Fixture();var monitor=new ServerWifiMonitor(()->wifi);
        try{
            monitor.sample();var result=monitor.snapshot(System.nanoTime());
            assertEquals("SERVER",result.path("device").asText());assertEquals("server-card",result.path("adapter_id").asText());
            assertEquals(1,result.path("estimated_meters").asDouble());
            wifi.reading=WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.PERMISSION_DENIED,"denied",System.currentTimeMillis());
            monitor.sample();result=monitor.snapshot(System.nanoTime());assertFalse(result.has("estimated_meters"));assertEquals("PERMISSION_DENIED",result.path("status").asText());
        }finally{monitor.close();}
        assertTrue(wifi.closed.await(3,TimeUnit.SECONDS));assertFalse(monitor.snapshot(System.nanoTime()).has("estimated_meters"));
    }
    @Test void nativeFailureDoesNotEscapeOrInventADistance(){
        try(var monitor=new ServerWifiMonitor(()->{throw new IllegalStateException("native unavailable");})){
            monitor.sample();assertFalse(monitor.snapshot(System.nanoTime()).has("estimated_meters"));assertEquals("API_ERROR",monitor.snapshot(System.nanoTime()).path("status").asText());
        }
    }
}
