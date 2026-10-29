package vn.edu.hutech.nqb.shared.fingerprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;
import static vn.edu.hutech.nqb.shared.fingerprint.FingerprintModel.*;

class FingerprintEvaluationTest {
    @TempDir Path temp;
    long clock=1760000000000L;
    Point point(String name,Zone zone,double x,double y){return new Point(id(),name,zone,x,y);}
    Area area(Point...points){return new Area(id(),1,"QA synthetic","QA","HUTECH E1","m","X corridor; Y room",0,4,clock,List.of(points));}
    Run run(Area a,Point p,int...rssi){
        long start=clock;clock+=1000;var scans=new ArrayList<Scan>();
        for(int i=0;i<3;i++){long now=start+i*100;var observations=new ArrayList<Observation>();for(int j=0;j<rssi.length;j++)observations.add(new Observation(a.ssid(),"02:00:00:00:00:0"+(j+1),rssi[j],5180000,(now+5+WINDOWS_EPOCH_MS)*10000,5,true,false));
            // A strong cached record must not change the fingerprint.
            observations.add(new Observation(a.ssid(),"02:00:00:00:00:ff",-10,5220000,(now-1000+WINDOWS_EPOCH_MS)*10000,1010,false,false));
            scans.add(new Scan(id(),"adapter","QA card",a.ssid(),"",now,now+10,"COMPLETE","QA",0,observations));}
        return new Run(id(),a.id(),a.revision(),p.id(),"adapter","QA card","QA laptop","Windows QA","X tăng",start,start+210,State.ACCEPTED,"QA synthetic",scans);
    }
    @Test void heldOutSignalFindsCoordinatesAndStraightLineServerDistance()throws Exception{
        var p0=point("p0",Zone.CORRIDOR,0,0);var p8=point("p8",Zone.CORRIDOR,8,0);var middle=point("test",Zone.CORRIDOR,4,0);var a=area(p0,p8,middle);
        var r0=run(a,p0,-50,-60);var r8=run(a,p8,-58,-68);var test=run(a,middle,-52,-62);var data=Bundle.of(List.of(a),List.of(r0,r8,test));
        var report=FingerprintEvaluation.evaluate(data,Set.of(test.id()),Parameters.baseline(2));assertEquals(2,report.trainingPoints());assertFalse(report.trainingRunIds().contains(test.id()));
        var knn=report.methods().get(0).rows().getFirst();var wknn=report.methods().get(1).rows().getFirst();
        assertEquals(4,knn.estimate().x(),1e-12);assertEquals(Math.sqrt(32),knn.estimate().serverDistanceMeters(),1e-12);assertEquals(0,knn.positionErrorMeters(),1e-12);
        assertEquals(2,wknn.estimate().x(),1e-12);assertEquals(2,wknn.positionErrorMeters(),1e-12);assertEquals(0,knn.estimate().y());
        assertEquals(2,knn.estimate().commonRadios());assertEquals(0,report.methods().getFirst().metrics().rejectionRate());
        Path in=temp.resolve("survey.json"),out=temp.resolve("report.json");Files.writeString(in,encode(data));FingerprintCli.evaluate(in,out,Set.of(test.id()),Parameters.baseline(2));
        assertEquals("wifi_fingerprint_offline_evaluation",JSON.readTree(Files.readString(out)).path("purpose").asText());
        assertThrows(FileAlreadyExistsException.class,()->FingerprintCli.evaluate(in,out,Set.of(test.id()),Parameters.baseline(2)));
        assertThrows(FileAlreadyExistsException.class,()->FingerprintCli.evaluate(in,in,Set.of(test.id()),Parameters.baseline(2)));assertEquals(data,JSON.readValue(Files.readString(in),Bundle.class));
    }
    @Test void mixedZonesAndIndistinguishableFingerprintsDoNotProduceInventedCoordinates(){
        var room=point("room",Zone.ROOM,0,4);var corridor=point("hall",Zone.CORRIDOR,0,0);var a=area(room,corridor);
        var left=run(a,room,-50,-60);var right=run(a,corridor,-54,-64);var query=run(a,room,-52,-62);
        assertEquals(Status.AMBIGUOUS_ZONE,new FingerprintModel(a,List.of(left,right),Parameters.baseline(2)).estimateHeldOut(query,Method.WKNN).status());
        var p0=point("a",Zone.CORRIDOR,0,0);var p8=point("b",Zone.CORRIDOR,8,0);var b=area(p0,p8);
        var one=run(b,p0,-50,-60);var two=run(b,p8,-50,-60);var same=run(b,p0,-50,-60);
        assertEquals(Status.AMBIGUOUS_EXACT_MATCH,new FingerprintModel(b,List.of(one,two),Parameters.baseline(1)).estimateHeldOut(same,Method.KNN).status());
    }
    @Test void allRejectedMetricsRemainNullAndMissingRadiosAreNotZeroDbm(){
        var p0=point("a",Zone.CORRIDOR,0,0);var p8=point("b",Zone.CORRIDOR,8,0);var a=area(p0,p8);var r0=run(a,p0,-50,-60);var r8=run(a,p8,-60,-70);var weak=run(a,p0,-100,-100);
        var report=FingerprintEvaluation.evaluate(Bundle.of(List.of(a),List.of(r0,r8,weak)),Set.of(weak.id()),Parameters.baseline(2));
        assertEquals(1,report.methods().getFirst().metrics().rejectedRuns());assertNull(report.methods().getFirst().metrics().medianPositionErrorMeters());assertEquals(Status.OUTSIDE_SIGNAL_MAP,report.methods().getFirst().rows().getFirst().estimate().status());
        var node=JSON.valueToTree(weak);for(var s:node.path("scans"))for(var o:s.path("observations"))((com.fasterxml.jackson.databind.node.ObjectNode)o).put("bssid",o.path("bssid").asText().replace("02:00:","04:00:"));
        var unseen=decode(node,Run.class);assertEquals(Status.INSUFFICIENT_OVERLAP,new FingerprintModel(a,List.of(r0,r8),Parameters.baseline(2)).estimateHeldOut(unseen,Method.KNN).status());
        assertFalse(encode(report).contains("NaN"));
    }
    @Test void separateFrequencyIsSeparateFeatureAndTrainingLeakageIsRejected(){
        var p0=point("a",Zone.CORRIDOR,0,0);var p8=point("b",Zone.CORRIDOR,8,0);var a=area(p0,p8);var r0=run(a,p0,-50,-60);var r8=run(a,p8,-60,-70);
        var model=new FingerprintModel(a,List.of(r0,r8),Parameters.baseline(1));assertThrows(IllegalArgumentException.class,()->model.estimateHeldOut(r0,Method.KNN));
        var node=JSON.valueToTree(r0);((com.fasterxml.jackson.databind.node.ObjectNode)node).put("id",id());for(var s:node.path("scans"))((com.fasterxml.jackson.databind.node.ObjectNode)s).put("scanId",id());
        var renamed=decode(node,Run.class);assertThrows(IllegalArgumentException.class,()->model.estimateHeldOut(renamed,Method.KNN));
        var query=JSON.valueToTree(run(a,p0,-50,-60));for(var s:query.path("scans"))for(var o:s.path("observations"))((com.fasterxml.jackson.databind.node.ObjectNode)o).put("frequencyKhz",5745000);
        assertEquals(Status.INSUFFICIENT_OVERLAP,model.estimateHeldOut(decode(query,Run.class),Method.KNN).status());
        assertThrows(IllegalArgumentException.class,()->FingerprintEvaluation.evaluate(Bundle.of(List.of(a),List.of(r0,r8)),Set.of(id()),Parameters.baseline(1)));
    }
    @Test void physicallySeparatedNeighborsAreRejectedEvenIfSignalMatches(){
        var p0=point("a",Zone.CORRIDOR,0,0);var far=point("b",Zone.CORRIDOR,30,0);var a=area(p0,far);var r0=run(a,p0,-50,-60);var r30=run(a,far,-54,-64);var query=run(a,p0,-52,-62);
        assertEquals(Status.NEIGHBORS_TOO_FAR_APART,new FingerprintModel(a,List.of(r0,r30),Parameters.baseline(2)).estimateHeldOut(query,Method.WKNN).status());
    }
}
