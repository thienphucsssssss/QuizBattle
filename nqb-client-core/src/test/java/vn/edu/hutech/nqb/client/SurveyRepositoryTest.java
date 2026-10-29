package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;
import static vn.edu.hutech.nqb.shared.SurveyRepository.LOCAL_OWNER;
import static org.junit.jupiter.api.Assertions.*;

class SurveyRepositoryTest {
    @TempDir Path temp;
    static final long TIME=1760000000000L;
    static Area area(){return new Area(id(),1,"Tầng thử nghiệm","Nhóm QA","HUTECH E1","m","X dọc hành lang; Y vào phòng",1,4,TIME,List.of(new Point(id(),"Cửa",Zone.CORRIDOR,0,0),new Point(id(),"Bàn",Zone.ROOM,1,4)));}
    static Scan scan(long start,boolean fresh){return new Scan(id(),"adapter","Card QA","HUTECH E1","02:00:00:00:00:01",start,start+10,fresh?"COMPLETE":"STALE","QA",0,List.of(
        new Observation("HUTECH E1","02:00:00:00:00:01",-51,5180000,(start+(fresh?5:-1000)+WINDOWS_EPOCH_MS)*10000,fresh?5:1010,fresh,true),
        new Observation("HUTECH E1","02:00:00:00:00:02",-70,5220000,(start+(fresh?5:-1000)+WINDOWS_EPOCH_MS)*10000,fresh?5:1010,fresh,false)));}
    static Run run(Area a,State state){return new Run(id(),a.id(),a.revision(),a.points().getFirst().id(),"adapter","Card QA","Laptop QA","Windows QA","Theo X tăng",TIME,TIME+310,state,"QA",List.of(scan(TIME+100,true),scan(TIME+200,false),scan(TIME+300,true)));}
    static Run accepted(Area a){var r=run(a,State.REVIEW);var scans=new ArrayList<>(r.scans());scans.add(scan(TIME+400,true));return r.withScans(scans).finish(State.ACCEPTED,"Xác nhận QA",TIME+410);}
    static <T>T get(CompletableFuture<T> value)throws Exception{return value.get(5,TimeUnit.SECONDS);}
    @Test void rawScansSurviveRestartAndInterruptedCaptureCannotBeAccepted()throws Exception{
        Path file=temp.resolve("survey.db");Area a=area();Run r=run(a,State.CAPTURING).withScans(List.of());
        try(var repo=new SurveyRepository(file,true)){get(repo.saveArea(LOCAL_OWNER,a));get(repo.start(r));get(repo.append(r.id(),scan(TIME+100,true)));}
        try(var repo=new SurveyRepository(file,true)){var recovered=get(repo.getRun(LOCAL_OWNER,r.id()));assertEquals(State.INTERRUPTED,recovered.state());assertEquals(1,recovered.scans().size());assertThrows(ExecutionException.class,()->get(repo.finish(r.id(),State.ACCEPTED,"bad")));}
    }
    @Test void importIsAtomicIdempotentAndRevisionDoesNotMoveExistingSamples()throws Exception{
        var a=area();var r=accepted(a);var bundle=Bundle.of(List.of(a),List.of(r));
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true)){
            assertEquals(1,get(repo.importBundle(LOCAL_OWNER,bundle)));assertEquals(0,get(repo.importBundle(LOCAL_OWNER,bundle)));
            var a2=new Area(a.id(),2,a.name(),a.collector(),a.ssid(),"m",a.axisNote(),5,6,TIME+1,a.points());get(repo.saveArea(LOCAL_OWNER,a2));assertTrue(get(repo.runs(LOCAL_OWNER,a.id(),2)).isEmpty());
            var other=area();var conflicting=r.finish(State.DISCARDED,"changed",r.endedAtMillis());
            assertThrows(ExecutionException.class,()->get(repo.importBundle(LOCAL_OWNER,Bundle.of(List.of(other,a),List.of(conflicting)))));
            assertEquals(2,get(repo.areas(LOCAL_OWNER)).size());assertEquals(r,get(repo.getRun(LOCAL_OWNER,r.id())));
            var file=temp.resolve("export.json");get(repo.exportFile(file,LOCAL_OWNER,a.id(),1));assertEquals(bundle,get(repo.previewFile(file)));
        }
    }
    @Test void wrongSsidBadCoordinateUnknownVersionAndCacheClaimFailBeforeWrite()throws Exception{
        var a=area();var r=accepted(a);var node=JSON.valueToTree(Bundle.of(List.of(a),List.of(r)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("runs").get(0).path("scans").get(0).path("observations").get(0)).put("ssid","Elsewhere");
        assertThrows(IllegalArgumentException.class,()->validate(decode(node,Bundle.class)));
        var bad=new Area(a.id(),1,a.name(),a.collector(),a.ssid(),"m",a.axisNote(),Double.NaN,0,TIME,a.points());assertThrows(IllegalArgumentException.class,()->validate(bad));
        var stale=scan(TIME+100,false);var observation=stale.observations().getFirst();var lying=new Observation(observation.ssid(),observation.bssid(),observation.rssiDbm(),observation.frequencyKhz(),observation.hostTimestamp100ns(),observation.ageMillis(),true,true);
        var badScan=new Scan(stale.scanId(),stale.adapterId(),stale.adapterName(),stale.ssidFilter(),stale.associatedBssid(),stale.startedAtMillis(),stale.completedAtMillis(),"COMPLETE","",0,List.of(lying));
        assertThrows(IllegalArgumentException.class,()->validate(r.withScans(List.of(badScan)),a));
        assertEquals(2,quality(run(a,State.REVIEW)).freshScans());assertFalse(quality(run(a,State.REVIEW)).canAccept());
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true)){
            Path file=temp.resolve("bad.json");Files.writeString(file,encode(Bundle.of(List.of(a),List.of(r))).replace("\"schemaVersion\":1","\"schemaVersion\":99"));assertThrows(ExecutionException.class,()->get(repo.previewFile(file)));assertTrue(get(repo.areas(LOCAL_OWNER)).isEmpty());
        }
    }
    @Test void sameScanCannotBeAssignedToAnotherPointOrRun()throws Exception{
        var a=area();var r=accepted(a);var copy=new Run(id(),a.id(),1,a.points().get(1).id(),r.adapterId(),r.adapterName(),r.deviceLabel(),r.platform(),r.orientation(),r.startedAtMillis(),r.endedAtMillis(),r.state(),r.note(),r.scans());
        assertThrows(IllegalArgumentException.class,()->validate(Bundle.of(List.of(a),List.of(r,copy))));
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true)){get(repo.importBundle(LOCAL_OWNER,Bundle.of(List.of(a),List.of(r))));assertThrows(ExecutionException.class,()->get(repo.importBundle(LOCAL_OWNER,Bundle.of(List.of(a),List.of(copy)))));assertEquals(1,get(repo.runs(LOCAL_OWNER,a.id(),1)).size());}
    }
    @Test void incompleteAndRepeatedUploadsAreIsolatedByOwnerAndNeverPublishPartialRun()throws Exception{
        var a=area();var r=accepted(a);String owner="10",other="11";var hash=digest(r);
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true)){
            get(repo.handle(owner,"SURVEY_PUT_AREA",JSON.createObjectNode().set("area",JSON.valueToTree(a))));
            var begin=JSON.createObjectNode().put("scan_count",r.scans().size()).put("digest",hash).put("final_state","ACCEPTED");begin.set("run",JSON.valueToTree(r.withScans(List.of()).finish(State.REVIEW,r.note(),r.endedAtMillis())));
            get(repo.handle(owner,"SURVEY_UPLOAD_BEGIN",begin));get(repo.handle(owner,"SURVEY_UPLOAD_BEGIN",begin));
            var commit=JSON.createObjectNode().put("run_id",r.id()).put("digest",hash);assertThrows(ExecutionException.class,()->get(repo.handle(owner,"SURVEY_UPLOAD_COMMIT",commit)));assertTrue(get(repo.runs(owner,a.id(),1)).isEmpty());
            for(int i=0;i<r.scans().size();i++){
                var payload=JSON.createObjectNode().put("run_id",r.id()).put("sequence",i);payload.set("scan",JSON.valueToTree(r.scans().get(i)));
                assertThrows(ExecutionException.class,()->get(repo.handle(other,"SURVEY_UPLOAD_SCAN",payload)));get(repo.handle(owner,"SURVEY_UPLOAD_SCAN",payload));get(repo.handle(owner,"SURVEY_UPLOAD_SCAN",payload));
                assertTrue(encode(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<Protocol.MAX_FRAME_BYTES-1000);
            }
            assertTrue(get(repo.handle(owner,"SURVEY_UPLOAD_COMMIT",commit)).path("committed").asBoolean());assertTrue(get(repo.handle(owner,"SURVEY_UPLOAD_COMMIT",commit)).path("committed").asBoolean());assertTrue(get(repo.handle(owner,"SURVEY_UPLOAD_BEGIN",begin)).path("committed").asBoolean());
            assertEquals(r,get(repo.getRun(owner,r.id())));assertTrue(get(repo.areas(other)).isEmpty());
        }
    }
    @Test void uploadStagingSurvivesReconnectAndStoreRestart()throws Exception{
        Path file=temp.resolve("survey.db");var a=area();var r=accepted(a);var hash=digest(r);String owner="3";
        var begin=JSON.createObjectNode().put("scan_count",r.scans().size()).put("digest",hash).put("final_state","ACCEPTED");begin.set("run",JSON.valueToTree(r.withScans(List.of()).finish(State.REVIEW,r.note(),r.endedAtMillis())));
        try(var repo=new SurveyRepository(file,true)){get(repo.saveArea(owner,a));get(repo.handle(owner,"SURVEY_UPLOAD_BEGIN",begin));}
        try(var repo=new SurveyRepository(file,true)){get(repo.handle(owner,"SURVEY_UPLOAD_BEGIN",begin));for(int i=0;i<r.scans().size();i++){var p=JSON.createObjectNode().put("run_id",r.id()).put("sequence",i);p.set("scan",JSON.valueToTree(r.scans().get(i)));get(repo.handle(owner,"SURVEY_UPLOAD_SCAN",p));}
            get(repo.handle(owner,"SURVEY_UPLOAD_COMMIT",JSON.createObjectNode().put("run_id",r.id()).put("digest",hash)));assertEquals(r,get(repo.getRun(owner,r.id())));}
    }
}
