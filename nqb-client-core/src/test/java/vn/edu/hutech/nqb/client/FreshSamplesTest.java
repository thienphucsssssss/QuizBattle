package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class FreshSamplesTest {
    @TempDir Path temp;
    record Sent(String type,ObjectNode data,long nano) { }
    static final class Gateway implements QuizGateway {
        final List<Sent> sent=new CopyOnWriteArrayList<>();
        public CompletableFuture<JsonNode> request(String type,ObjectNode payload){sent.add(new Sent(type,payload.deepCopy(),System.nanoTime()));return CompletableFuture.completedFuture(QuizGateway.object().put("config_epoch",1));}
        public void onEvent(Consumer<JsonNode> listener){}public boolean isPreview(){return false;}public void close(){}
    }
    private static WifiSignalProvider.Reading reading(int rssi){return WifiSignalProvider.Reading.connected("a","Wi-Fi","AP","aa:bb:cc:dd:ee:01","5 GHz",36,5180000,rssi,80,System.currentTimeMillis());}
    private static void await(java.util.function.BooleanSupplier check)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!check.getAsBoolean()){if(System.nanoTime()>end)fail("Timed out");Thread.sleep(5);}}
    @Test void freshBatchExcludesOldSamplesAndSharesAutomaticReportThrottle()throws Exception {
        var value=new AtomicInteger(-45);WifiSignalProvider provider=new WifiSignalProvider(){public Reading readSignal(){return reading(value.get());}public void close(){}};
        var gateway=new Gateway();try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(temp.resolve("c.json")),10,150,30)){
            service.startReporting(gateway,()->1,()->"",null,null);await(()->!gateway.sent.isEmpty());
            value.set(-65);service.requestFreshSamples(gateway,"fresh-1",1,"",()->1,()->"",null);
            await(()->gateway.sent.stream().anyMatch(s->s.data.path("refresh_id").asText().equals("fresh-1")));
            var fresh=gateway.sent.stream().filter(s->s.data.has("refresh_id")).findFirst().orElseThrow();assertEquals(5,fresh.data.path("samples").size());for(var sample:fresh.data.path("samples"))assertEquals(-65,sample.asInt());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(fresh.nano-gateway.sent.getFirst().nano)>=145);
        }
    }
    @Test void refreshReportsPermissionFailureWithoutInventingDistance()throws Exception {
        WifiSignalProvider provider=new WifiSignalProvider(){public Reading readSignal(){return Reading.failure(Status.PERMISSION_DENIED,"denied",System.currentTimeMillis());}public void close(){}};
        var gateway=new Gateway();try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(temp.resolve("p.json")),10,100,30)){
            service.requestFreshSamples(gateway,"denied",1,"",()->1,()->"",null);await(()->!gateway.sent.isEmpty());assertEquals("PROXIMITY_REFRESH_STATUS",gateway.sent.getFirst().type);assertEquals("PERMISSION_DENIED",gateway.sent.getFirst().data.path("reason").asText());
        }
    }
    @Test void refreshDoesNotInterruptCalibration()throws Exception {
        WifiSignalProvider provider=new WifiSignalProvider(){public Reading readSignal(){return reading(-45);}public void close(){}};
        var gateway=new Gateway();var done=new CountDownLatch(1);try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(temp.resolve("c.json")),10,100,25)){
            service.startCalibrationStep(1,"a","aa:bb:cc:dd:ee:01","5 GHz",null,r->done.countDown());service.requestFreshSamples(gateway,"busy",1,"",()->1,()->"",null);
            await(()->!gateway.sent.isEmpty());assertEquals("CALIBRATING",gateway.sent.getFirst().data.path("reason").asText());assertTrue(done.await(2,TimeUnit.SECONDS));
        }
    }
    @Test void lateNativeReadCannotPublishIntoAnotherSession()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicReference<>("");
        WifiSignalProvider provider=new WifiSignalProvider(){public Reading readSignal(){entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return reading(-45);}public void close(){}};
        var gateway=new Gateway();try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(temp.resolve("late.json")),10,100,25)){
            service.requestFreshSamples(gateway,"old",1,"",()->1,session::get,null);assertTrue(entered.await(1,TimeUnit.SECONDS));session.set("new");release.countDown();Thread.sleep(100);assertTrue(gateway.sent.isEmpty());
        }finally{release.countDown();}
    }
}
