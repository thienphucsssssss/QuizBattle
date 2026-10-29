package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;
import static vn.edu.hutech.nqb.shared.SurveyRepository.LOCAL_OWNER;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCaptureServiceTest {
    @TempDir Path temp;
    static class Fixture implements WifiSignalProvider,WifiScanProvider {
        int scans;boolean waitForCancel;final CountDownLatch entered=new CountDownLatch(1),cancel=new CountDownLatch(1);
        public Reading readSignal(){return Reading.failure(WifiSignalProvider.Status.DISCONNECTED,"QA",System.currentTimeMillis());}
        public List<Adapter> scanAdapters(){return List.of(new Adapter("qa","Card QA",true));}
        public WifiScanSnapshot scan(String adapter,String ssid){scans++;entered.countDown();if(waitForCancel)try{cancel.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            long now=System.currentTimeMillis();return new WifiScanSnapshot(id(),adapter,"Card QA",ssid,"",now,now,WifiScanSnapshot.Status.COMPLETE,"QA",0,List.of(
                new WifiObservation(ssid,"02:00:00:00:00:01",-52,5180000,(now+WINDOWS_EPOCH_MS)*10000,0,true,false),new WifiObservation(ssid,"02:00:00:00:00:02",-67,5220000,(now+WINDOWS_EPOCH_MS)*10000,0,true,false)));}
        public void cancelScan(){cancel.countDown();}public void close(){}
    }
    @BeforeEach void paths(){System.setProperty("nqb.wifi.catalog",temp.resolve("catalog.json").toString());}
    @AfterEach void pathsReset(){System.clearProperty("nqb.wifi.catalog");}
    @Test void timedCaptureRequiresReviewAndPersistsEveryScan()throws Exception{
        var fixture=new Fixture();var a=SurveyRepositoryTest.area();
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true);var wifi=new ProximityClientService(fixture,new ProximityCalibrationStore(temp.resolve("cal.json")),1000,5000,30);var capture=new SurveyCaptureService(wifi,repo,200)){
            repo.saveArea(LOCAL_OWNER,a).get();var result=capture.start(a,a.points().getFirst(),fixture.scanAdapters().getFirst(),"Laptop QA","Theo X",1,null).get(4,TimeUnit.SECONDS);
            assertEquals(State.REVIEW,result.state());assertTrue(quality(result).canAccept());assertEquals(result,repo.getRun(LOCAL_OWNER,result.id()).get());
            assertEquals(State.ACCEPTED,repo.finish(result.id(),State.ACCEPTED,"Đứng đúng điểm").get().state());
        }
    }
    @Test void cancellationDoesNotAcceptOrAttachInFlightScanAndReleasesAdapter()throws Exception{
        var fixture=new Fixture();fixture.waitForCancel=true;var a=SurveyRepositoryTest.area();
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true);var wifi=new ProximityClientService(fixture,new ProximityCalibrationStore(temp.resolve("cal.json")),1000,5000,30);var capture=new SurveyCaptureService(wifi,repo,10)){
            repo.saveArea(LOCAL_OWNER,a).get();var result=capture.start(a,a.points().getFirst(),fixture.scanAdapters().getFirst(),"Laptop QA","Theo X",30,null);
            assertTrue(fixture.entered.await(2,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->wifi.scanAsync("qa",a.ssid()).get());capture.cancel();
            var run=result.get(3,TimeUnit.SECONDS);assertEquals(State.DISCARDED,run.state());assertTrue(run.scans().isEmpty());assertFalse(capture.busy());
        }
    }
    @Test void nativeSurveyCaptureOptIn()throws Exception{
        if(!Boolean.getBoolean("nqb.native.survey"))return;
        try(var repo=new SurveyRepository(temp.resolve("native-survey.db"),true);var wifi=new ProximityClientService(new WindowsWifiSignalProvider(),new ProximityCalibrationStore(temp.resolve("native-cal.json")),1000,5000,30);var capture=new SurveyCaptureService(wifi,repo)){
            var reading=wifi.readNowAsync().get(8,TimeUnit.SECONDS);assertTrue(reading.isConnected(),reading.diagnostic());
            var adapter=wifi.scanAdaptersAsync().get(8,TimeUnit.SECONDS).stream().filter(a->a.id().equals(reading.adapterId())).findFirst().orElseThrow();
            long now=System.currentTimeMillis();var a=new Area(id(),1,"CHỈ KIỂM THỬ NATIVE","QA tự động",reading.ssid(),"m","Tọa độ giả định; không dùng định vị",0,0,now,List.of(new Point(id(),"Điểm giả định QA",Zone.ROOM,0,0)));repo.saveArea(LOCAL_OWNER,a).get();
            var run=capture.start(a,a.points().getFirst(),adapter,"Native QA","Không xác nhận vị trí thực",20,null).get(35,TimeUnit.SECONDS);
            System.out.println("NATIVE_SURVEY_CAPTURE: state="+run.state()+" scans="+run.scans().size()+" freshScans="+quality(run).freshScans()+" radios="+quality(run).radios()+" stale="+quality(run).staleObservations());
            assertEquals(State.REVIEW,run.state(),run.note());assertTrue(quality(run).freshScans()>0,"No fresh native scan to validate");assertEquals(run,repo.getRun(LOCAL_OWNER,run.id()).get());
            repo.finish(run.id(),State.DISCARDED,"QA giả định; không dùng làm dữ liệu khảo sát thực.").get();
        }
    }
    @Test void serverSessionLossDoesNotCancelOfflineSurvey()throws Exception{
        var fixture=new Fixture();fixture.waitForCancel=true;var a=SurveyRepositoryTest.area();
        try(var repo=new SurveyRepository(temp.resolve("survey.db"),true);var wifi=new ProximityClientService(fixture,new ProximityCalibrationStore(temp.resolve("cal.json")),1000,5000,30);var capture=new SurveyCaptureService(wifi,repo,200)){
            repo.saveArea(LOCAL_OWNER,a).get();var result=capture.start(a,a.points().getFirst(),fixture.scanAdapters().getFirst(),"Laptop QA","Theo X",1,null);
            assertTrue(fixture.entered.await(2,TimeUnit.SECONDS));wifi.stopSession();assertEquals(1,fixture.cancel.getCount(),"Room disconnect cancelled the independent survey scan");fixture.cancel.countDown();
            var run=result.get(4,TimeUnit.SECONDS);assertEquals(State.REVIEW,run.state());assertTrue(quality(run).freshScans()>0);assertEquals(run,repo.getRun(LOCAL_OWNER,run.id()).get());
        }
    }
}
