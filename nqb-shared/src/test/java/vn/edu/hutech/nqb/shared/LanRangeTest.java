package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

class LanRangeTest {
    @TempDir Path temp;
    static ObjectNode options() {
        return JsonNodeFactory.instance.objectNode().put("survey_id", UUID.randomUUID().toString())
            .put("survey_name","Đo tầng 2").put("route","Hành lang").put("topology","DEDICATED_AP")
            .put("distance_basis","CORRIDOR_ROUTE").put("notes","Hai tường").put("distance_m",31.5).put("duration_seconds",30);
    }
    @Test void validatesMeasuredDistanceAndDurationWithoutAcceptingFakeAddresses() {
        var input=options().put("client_ip","fake");
        var parsed=parseOptions(input);
        assertEquals("31.5",parsed.distanceMeters().toPlainString());
        assertFalse(optionsPayload(parsed).has("client_ip"));
        for(double bad:new double[]{-1,500.1,31.55})assertThrows(IllegalArgumentException.class,()->parseOptions(options().put("distance_m",bad)));
        assertThrows(IllegalArgumentException.class,()->parseOptions(options().put("duration_seconds",31)));
        assertThrows(IllegalArgumentException.class,()->parseOptions(options().put("survey_id","../escape")));
        assertThrows(IllegalArgumentException.class,()->parseOptions(options().put("route","")));
        assertEquals(60,parseOptions(options().put("distance_m",0).put("duration_seconds",60)).durationSeconds());
    }
    @Test void thresholdsUsePlannedSamplesAndNeverPassAnInterruptedRun() {
        var values=new ArrayList<Sample>();
        for(int i=1;i<=30;i++)values.add(new Sample(Direction.CLIENT_TO_SERVER,i,i==30?Status.TIMEOUT:Status.ON_TIME,i==30?null:10_000_000L));
        var summary=LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,30,values);
        assertEquals(29,summary.path("on_time").asInt());assertEquals(1,summary.path("timeout").asInt());
        assertTrue(LanRangeStatistics.passes(summary,summary,EndReason.COMPLETED,true));
        assertFalse(LanRangeStatistics.passes(summary,summary,EndReason.STOPPED,true));
        assertFalse(LanRangeStatistics.passes(summary,summary,EndReason.COMPLETED,false));
        values.set(28,new Sample(Direction.CLIENT_TO_SERVER,29,Status.TIMEOUT,null));
        assertFalse(LanRangeStatistics.passes(LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,30,values),summary,EndReason.COMPLETED,true));
        values.remove(28);
        assertEquals(1,LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,30,values).path("unconfirmed").asInt());
    }
    @Test void percentilesAndDuplicateRepliesDoNotInventSuccess() {
        var values=new ArrayList<Sample>();
        for(int i=1;i<=4;i++)values.add(new Sample(Direction.SERVER_TO_CLIENT,i,Status.ON_TIME,i*10_000_000L));
        values.add(new Sample(Direction.SERVER_TO_CLIENT,1,Status.TIMEOUT,null));
        var summary=LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,4,values);
        assertEquals(4,summary.path("confirmed").asInt());
        assertEquals(25,summary.path("rtt_median_ms").asDouble());
        assertEquals(40,summary.path("rtt_p95_ms").asDouble());
        assertTrue(LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,30,List.of()).path("rtt_min_ms").isNull());
        assertThrows(IllegalArgumentException.class,()->LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,4,
            List.of(new Sample(Direction.SERVER_TO_CLIENT,5,Status.ON_TIME,1L))));
    }
    @Test void sixtySampleThresholdAcceptsExactlyFiftySevenOnTimeReplies(){
        var values=new ArrayList<Sample>();
        for(int i=1;i<=60;i++)values.add(new Sample(Direction.CLIENT_TO_SERVER,i,i>57?Status.TIMEOUT:Status.ON_TIME,i>57?null:1_000_000L));
        var summary=LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,60,values);
        assertTrue(LanRangeStatistics.passes(summary,summary,EndReason.COMPLETED,true));
        values.set(56,new Sample(Direction.CLIENT_TO_SERVER,57,Status.TIMEOUT,null));
        assertFalse(LanRangeStatistics.passes(LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,60,values),summary,EndReason.COMPLETED,true));
    }
    @Test void journalFlushesInOrderAndKeepsCompleteLinesAfterCrash() throws Exception {
        String id=UUID.randomUUID().toString();
        var journal=new LanRangeJournal(temp,"client",id);
        journal.append(options().put("record_type","RUN_STARTED")).get(2,TimeUnit.SECONDS);
        journal.append(JsonNodeFactory.instance.objectNode().put("record_type","SAMPLE").put("sequence",1)).get(2,TimeUnit.SECONDS);
        journal.closeAsync().get(2,TimeUnit.SECONDS);
        Path file=temp.resolve("client-"+id+".jsonl");
        var rows=LanRangeJournal.replay(file);
        assertEquals(2,rows.size());assertEquals("RUN_STARTED",rows.getFirst().path("record_type").asText());
        assertEquals(1,rows.get(1).path("schema_version").asInt());
        Files.writeString(file,"{\"record_type\":",StandardOpenOption.APPEND);
        assertEquals(2,LanRangeJournal.replay(file).size());
        assertThrows(IllegalArgumentException.class,()->new LanRangeJournal(temp,"client","../bad"));
    }
    @Test void journalReportsAnUnwritableDestination() throws Exception {
        Path file=Files.writeString(temp.resolve("not-a-directory"),"existing");
        var journal=new LanRangeJournal(file,"server",UUID.randomUUID().toString());
        assertThrows(ExecutionException.class,()->journal.append(options()).get(2,TimeUnit.SECONDS));
        journal.closeAsync().handle((v,e)->null).get(2,TimeUnit.SECONDS);
    }
}
