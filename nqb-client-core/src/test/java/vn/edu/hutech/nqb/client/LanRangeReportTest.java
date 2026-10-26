package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

class LanRangeReportTest {
    @TempDir Path temp;
    @Test void reloadKeepsMissingServerEvidenceUnknownAndEscapesCsv()throws Exception {
        String run=UUID.randomUUID().toString();
        var journal=new LanRangeJournal(temp,"client",run);
        journal.append(object().put("record_type","RUN_STARTED").put("survey_id","one").put("survey_name","=SUM(1,2)")
            .put("route","Tầng \"2\"\nHành lang").put("distance_m",31).put("duration_seconds",30).put("loopback",true)).get(2,TimeUnit.SECONDS);
        journal.append(samplePayload(new Sample(Direction.CLIENT_TO_SERVER,1,Status.ON_TIME,12_000_000L)).put("record_type","SAMPLE")).get(2,TimeUnit.SECONDS);
        journal.closeAsync().get(2,TimeUnit.SECONDS);
        var runs=LanRangeReport.load(temp);
        assertEquals(1,runs.size());var restored=runs.getFirst();
        assertEquals("APP_CLOSED",restored.path("end_reason").asText());
        assertEquals(1,restored.path("client_summary").path("on_time").asInt());
        assertEquals(30,restored.path("server_summary").path("unconfirmed").asInt());
        assertFalse(restored.path("passed").asBoolean());
        Path summary=temp.resolve("summary.csv");LanRangeReport.exportSummary(summary,runs);
        var csv=Files.readString(summary);
        assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("\"'=SUM(1,2)\""));
        assertTrue(csv.contains("\"Tầng \"\"2\"\"\nHành lang\""));
        Path detail=temp.resolve("samples.csv");LanRangeReport.exportSamples(detail,runs);
        assertTrue(Files.readString(detail).contains("CLIENT_TO_SERVER"));assertFalse(Files.readString(detail).contains("SERVER_TO_CLIENT"));
        assertTrue(LanRangeReport.farthestPassing(runs,"one").isEmpty());
    }
    @Test void farthestIsScopedToTheSameSurveyAndExcludesLocalOrIncompleteRuns(){
        var a=object().put("survey_id","a").put("passed",true).put("loopback",false).put("distance_m",30);
        var other=object().put("survey_id","b").put("passed",true).put("loopback",false).put("distance_m",99);
        var local=a.deepCopy().put("distance_m",50).put("loopback",true);
        var partial=a.deepCopy().put("distance_m",60).put("passed",false);
        assertEquals("30",LanRangeReport.farthestPassing(List.of(a,other,local,partial),"a").orElseThrow().toPlainString());
    }
}
