package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class FloorTrackingServiceTest {
    @TempDir Path temp;
    @BeforeEach void paths(){System.setProperty("nqb.wifi.catalog",temp.resolve("catalog.json").toString());}
    @AfterEach void reset(){System.clearProperty("nqb.wifi.catalog");}
    static class Gateway implements QuizGateway {
        final BlockingQueue<ObjectNode> sent=new LinkedBlockingQueue<>();
        public CompletableFuture<JsonNode> request(String type,ObjectNode p){assertEquals("FLOOR_REPORT",type);sent.add(p);return CompletableFuture.completedFuture(QuizGateway.object());}
        public void onEvent(Consumer<JsonNode> listener){}public boolean isPreview(){return false;}public void close(){}
    }
    @Test void sendsFreshRadioScanAndDisabledContextSuppressesInFlightResult()throws Exception{
        var provider=new SurveyCaptureServiceTest.Fixture();provider.waitForCancel=true;var gateway=new Gateway();
        try(var wifi=new ProximityClientService(provider,new ProximityCalibrationStore(temp.resolve("cal.json")),1000,5000,30);var live=new FloorTrackingService(wifi)){
            live.configure(gateway,QuizGateway.object().put("enabled",true).put("epoch","one").put("ssid","TEST"),null);
            assertTrue(provider.entered.await(2,TimeUnit.SECONDS));live.configure(gateway,QuizGateway.object().put("enabled",false),null);provider.cancel.countDown();
            assertNull(gateway.sent.poll(300,TimeUnit.MILLISECONDS));
            live.configure(gateway,QuizGateway.object().put("enabled",true).put("epoch","two").put("ssid","TEST"),null);
            var report=gateway.sent.poll(2,TimeUnit.SECONDS);assertNotNull(report);assertEquals("two",report.path("epoch").asText());
            assertEquals(2,report.path("scan").path("observations").size());assertEquals("TEST",report.path("scan").path("ssidFilter").asText());
            assertTrue(report.path("scan").path("observations").path(0).path("fresh").asBoolean());
            live.stop();assertNull(gateway.sent.poll(250,TimeUnit.MILLISECONDS));
        }
    }
}
