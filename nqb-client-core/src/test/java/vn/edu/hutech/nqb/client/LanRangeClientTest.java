package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.LanRangeProtocol;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

class LanRangeClientTest {
    @TempDir Path temp;
    Options options(){return new Options(BigDecimal.ZERO,30,UUID.randomUUID().toString(),"Một máy","Tại chỗ",Topology.DEDICATED_AP,DistanceBasis.STRAIGHT_LINE,"");}
    static class Peer implements QuizGateway {
        final String run=UUID.randomUUID().toString();boolean unsupported;
        public CompletableFuture<JsonNode> request(String type,ObjectNode p){
            if(unsupported)return CompletableFuture.failedFuture(new GatewayException("UNKNOWN_TYPE","Server cũ",object()));
            if(type.equals("LAN_RANGE_START"))return CompletableFuture.completedFuture(p.deepCopy().put("run_id",run)
                .put("client_ip_observed","127.0.0.1").put("server_ip","127.0.0.1").put("loopback",true));
            return CompletableFuture.completedFuture(p.deepCopy());
        }
        public void onEvent(Consumer<JsonNode> l){}public boolean isPreview(){return false;}public void close(){}
    }
    @Test void unsupportedServerNeverCreatesSimulatedMeasurements()throws Exception {
        var peer=new Peer();peer.unsupported=true;
        try(var client=new LanRangeClient(peer,temp,s->{})){
            var failure=assertThrows(ExecutionException.class,()->client.start(options()).get(3,TimeUnit.SECONDS));
            assertEquals("UNKNOWN_TYPE",assertInstanceOf(GatewayException.class,failure.getCause()).code());
            assertFalse(client.snapshot().path("active").asBoolean());
        }
        assertTrue(LanRangeReport.load(temp).isEmpty());
    }
    @Test void disconnectPreservesMeasuredSamplesWithoutInventingReverseResults()throws Exception {
        var first=new CountDownLatch(1);
        try(var client=new LanRangeClient(new Peer(),temp,s->{if(s.path("client_summary").path("confirmed").asInt()>0)first.countDown();})){
            client.start(options()).get(3,TimeUnit.SECONDS);assertTrue(first.await(3,TimeUnit.SECONDS));
            client.onEvent(object().put("type","DISCONNECTED").set("payload",object()));
            client.stop(EndReason.DISCONNECTED).get(3,TimeUnit.SECONDS);
            var result=client.snapshot();
            assertFalse(result.path("active").asBoolean());assertFalse(result.path("passed").asBoolean());
            assertEquals(0,result.path("server_summary").path("confirmed").asInt());
            assertTrue(result.path("saved").asBoolean());
        }
        assertEquals("DISCONNECTED",LanRangeReport.load(temp).getFirst().path("end_reason").asText());
    }
    @Test void closingAConnectionRejectsNewRunsImmediately()throws Exception {
        var client=new LanRangeClient(new Peer(),temp,s->{});
        client.close();
        assertThrows(ExecutionException.class,()->client.start(options()).get(1,TimeUnit.SECONDS));
        assertFalse(client.snapshot().path("active").asBoolean());
    }
}
