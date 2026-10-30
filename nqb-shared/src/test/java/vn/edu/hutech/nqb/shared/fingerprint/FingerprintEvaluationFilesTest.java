package vn.edu.hutech.nqb.shared.fingerprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

class FingerprintEvaluationFilesTest {
    @TempDir Path temp;
    @Test void demoIsDeterministicAndContainsBothEstimatedAndRejectedRuns() {
        var demo=FingerprintDemo.create();assertEquals(demo,FingerprintDemo.create());validate(demo.bundle());
        assertEquals("DEMO-OFFLINE",demo.bundle().areas().getFirst().ssid());
        var report=FingerprintEvaluation.evaluate(demo.bundle(),demo.heldOut(),FingerprintModel.Parameters.baseline(2));
        assertEquals(5,report.trainingPoints());
        for(var method:report.methods()){
            assertEquals(3,method.metrics().testRuns());assertEquals(2,method.metrics().estimatedRuns());assertEquals(1,method.metrics().rejectedRuns());
            assertTrue(method.rows().stream().anyMatch(r->r.estimate().status()==FingerprintModel.Status.INSUFFICIENT_OVERLAP));
            assertTrue(Collections.disjoint(report.trainingRunIds(),report.heldOutRunIds()));
        }
    }
    @Test void reportKeepsSyntheticProvenanceAndNeverOverwritesSource() throws Exception {
        var demo=FingerprintDemo.create();var input=temp.resolve("khảo sát.json");Files.writeString(input,encode(demo.bundle()));
        assertEquals(demo.bundle(),FingerprintEvaluationFiles.readSurvey(input));
        var report=FingerprintEvaluation.evaluate(demo.bundle(),demo.heldOut(),FingerprintModel.Parameters.baseline(2));
        var output=temp.resolve("sub/report.json");FingerprintEvaluationFiles.writeReport(output,report,demo.bundle().areas().getFirst().name(),true);
        var json=JSON.readTree(Files.readAllBytes(output));assertEquals("SYNTHETIC_DEMO",json.path("datasetKind").asText());
        assertTrue(json.path("measurementNotice").asText().contains("GIẢ LẬP"));assertEquals(report.datasetSha256(),json.path("datasetSha256").asText());
        byte[] before=Files.readAllBytes(output);
        assertThrows(FileAlreadyExistsException.class,()->FingerprintEvaluationFiles.writeReport(output,report,"Other",false));assertArrayEquals(before,Files.readAllBytes(output));
        assertThrows(FileAlreadyExistsException.class,()->FingerprintEvaluationFiles.writeReport(input,report,"Other",false));assertEquals(demo.bundle(),FingerprintEvaluationFiles.readSurvey(input));
        var userOutput=temp.resolve("user.json");FingerprintEvaluationFiles.writeReport(userOutput,report,"User dataset",false);
        assertEquals("USER_SURVEY",JSON.readTree(Files.readAllBytes(userOutput)).path("datasetKind").asText());
    }
    @Test void malformedMultipleRevisionAndOversizedFilesAreRejected() throws Exception {
        var path=temp.resolve("invalid.json");Files.writeString(path,"{}");assertThrows(Exception.class,()->FingerprintEvaluationFiles.readSurvey(path));
        var demo=FingerprintDemo.create();var a=demo.bundle().areas().getFirst();
        var b=new Area(a.id(),2,a.name(),a.collector(),a.ssid(),a.units(),a.axisNote(),a.serverX(),a.serverY(),a.createdAtMillis(),a.points());
        Files.writeString(path,encode(Bundle.of(List.of(a,b),List.of())));assertThrows(IllegalArgumentException.class,()->FingerprintEvaluationFiles.readSurvey(path));
        try(var file=new java.io.RandomAccessFile(path.toFile(),"rw")){file.setLength(MAX_FILE_BYTES+1L);}
        assertThrows(IllegalArgumentException.class,()->FingerprintEvaluationFiles.readSurvey(path));
    }
}
