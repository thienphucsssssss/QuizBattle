package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static vn.edu.hutech.nqb.server.Wire.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomTelemetryTest {
    private static long at(long ms){return TimeUnit.MILLISECONDS.toNanos(ms);}
    @Test void probesAreBoundToPlayerExpireAndDoNotAcceptClientSuppliedLatency(){
        var net=new RoomTelemetry();net.join("a",true);net.join("b",true);
        String probe=net.probe("a",at(1000)).path("probe_id").asText();
        assertNull(net.probe("a",at(1100)));assertThrows(Failure.class,()->net.reply("b",probe,at(1123)));
        net.reply("a",probe,at(1123));var row=obj();net.decorate("a",row,true,at(2000));assertEquals(123,row.path("ping_ms").asInt());
        assertThrows(Failure.class,()->net.reply("a",probe,at(1200)));
        row=obj();net.decorate("a",row,true,at(16123));assertFalse(row.has("ping_ms"));
        String late=net.probe("a",at(17000)).path("probe_id").asText();assertThrows(Failure.class,()->net.reply("a",late,at(20000)));
    }
    @Test void refreshCoalescesRateLimitsAndShowsPartialCompletion(){
        var net=new RoomTelemetry();net.context("room1|1|");net.join("a",true);net.join("b",true);net.join("old",false);
        String id=net.begin(List.of("a","b","old"),at(1000));assertEquals(id,net.begin(List.of("a"),at(1100)));
        assertEquals(2,net.pending().size());net.complete("a","other","UPDATED","");assertEquals(2,net.pending().size());
        net.complete("a",id,"UPDATED","");assertEquals(2,net.refresh(at(2000)).path("completed").asInt());
        assertTrue(net.tick(at(11000)));assertEquals("COMPLETED",net.refresh(at(11000)).path("state").asText());
        var row=obj();net.decorate("b",row,true,at(11000));assertEquals("TIMEOUT",row.path("refresh_status").asText());
        net.begin(List.of("a"),at(11001));net.complete("a",net.refresh(at(11001)).path("refresh_id").asText(),"UPDATED","");
        assertThrows(Failure.class,()->net.begin(List.of("a"),at(11002)));
    }
    @Test void contextAndDisconnectCancelOnlyTheirOwnPendingResults(){
        var net=new RoomTelemetry();net.join("a",true);net.join("b",true);net.context("r|1|");String id=net.begin(List.of("a","b"),at(1000));
        net.disconnect("a");assertFalse(net.accepts("a",id));assertTrue(net.accepts("b",id));
        net.context("r|1|new-game");assertFalse(net.accepts("b",id));assertEquals("CANCELLED",net.refresh(at(2000)).path("state").asText());
        net.clear();assertThrows(Failure.class,()->net.begin(List.of(),at(3000)));
    }
    @Test void legacyClientsAreSkippedWithoutMarkingThemDisconnected(){
        var net=new RoomTelemetry();net.join("old",false);assertNull(net.probe("old",at(1000)));net.begin(List.of("old"),at(1000));
        var row=obj();net.decorate("old",row,true,at(1000));assertEquals("UNSUPPORTED",row.path("refresh_status").asText());assertFalse(row.has("ping_ms"));
    }
}
