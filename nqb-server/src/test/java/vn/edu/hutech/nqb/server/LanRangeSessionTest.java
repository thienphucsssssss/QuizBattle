package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class LanRangeSessionTest {
    @TempDir Path temp;
    final AtomicLong nanos=new AtomicLong(1);
    final List<JsonNode> events=new CopyOnWriteArrayList<>();
    LanRangeSession session;
    LanRangeSession create(){
        String run=UUID.randomUUID().toString();
        var o=new Options(BigDecimal.valueOf(31),30,UUID.randomUUID().toString(),"Tầng 2","Hành lang",Topology.DEDICATED_AP,DistanceBasis.CORRIDOR_ROUTE,"");
        session=new LanRangeSession(run,o,events::add,new LanRangeJournal(temp,"server",run),nanos::get,Clock.systemUTC());
        return session;
    }
    @AfterEach void close()throws Exception{if(session!=null){session.finish(EndReason.APP_CLOSED);session.completion().get(3,TimeUnit.SECONDS);}}
    @Test void waitsForClientCompletionAndNeverResetsItsClock()throws Exception {
        var s=create();s.tick();assertTrue(events.isEmpty());s.start();
        for(int i=1;i<=30;i++){
            nanos.set((i-1)*1_000_000_000L+1);s.start();s.tick();
            final int seq=i;
            var probe=events.stream().filter(e->e.path("type").asText().equals("LAN_RANGE_CHALLENGE")&&e.path("payload").path("sequence").asInt()==seq).findFirst().orElseThrow();
            nanos.addAndGet(10_000_000);s.challengeReply(probe.path("request_id").asText(),i);
            s.challengeReply(probe.path("request_id").asText(),i);
        }
        assertFalse(s.finished());s.clientComplete();s.completion().get(3,TimeUnit.SECONDS);
        var end=events.stream().filter(e->e.path("type").asText().equals("LAN_RANGE_FINISHED")).findFirst().orElseThrow().path("payload");
        assertEquals(30,end.path("server_summary").path("on_time").asInt());
        assertEquals(10,end.path("server_summary").path("rtt_p95_ms").asDouble());
        assertEquals("COMPLETED",end.path("end_reason").asText());
        assertTrue(end.path("server_journal_healthy").asBoolean());
    }
    @Test void lateAndForgedRepliesCannotBecomeSuccess()throws Exception {
        var s=create();s.start();
        var challenge=events.stream().filter(e->e.path("type").asText().equals("LAN_RANGE_CHALLENGE")).findFirst().orElseThrow();
        s.challengeReply("wrong",1);
        nanos.addAndGet(1_100_000_000);s.challengeReply(challenge.path("request_id").asText(),1);
        s.finish(EndReason.STOPPED);s.completion().get(3,TimeUnit.SECONDS);
        var sample=events.stream().filter(e->e.path("type").asText().equals("LAN_RANGE_SERVER_SAMPLE")).findFirst().orElseThrow();
        assertEquals("TIMEOUT",sample.path("payload").path("status").asText());
        assertTrue(sample.path("payload").path("rtt_ms").isNull());
    }
    @Test void delayedSchedulingDoesNotBurstOrSilentlyPass()throws Exception {
        var s=create();s.start();nanos.addAndGet(4_500_000_000L);s.tick();s.finish(EndReason.STOPPED);s.completion().get(3,TimeUnit.SECONDS);
        assertEquals(2,events.stream().filter(e->e.path("type").asText().equals("LAN_RANGE_CHALLENGE")).count());
        assertEquals(4,events.stream().filter(e->e.path("payload").path("status").asText().equals("CANCELLED")).count());
    }
}
