package vn.edu.hutech.nqb.shared.fingerprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

class CorridorPlanTest {
    @Test void roomNamesUseAnUnassignedFloorUntilChosenAndSurviveJsonAndSurveyExport()throws Exception{
        var pending=CorridorPlan.DEFAULT;
        assertEquals("E1.X.02",pending.roomName(0));assertEquals("E1.X.01",pending.roomName(6));
        assertEquals(12,java.util.stream.IntStream.range(0,12).mapToObj(pending::roomName).distinct().count());
        var named=new CorridorPlan(6,8,8,3,"3");assertEquals("03",named.floor());
        assertEquals("E1.03.04",named.roomName(1));assertEquals("E1.03.03",named.roomName(7));
        var area=named.survey("Hành lang E1","HUTECH E1",7);
        assertTrue(area.points().stream().anyMatch(p->p.name().equals("Trong phòng E1.03.03")));
        assertEquals(named,CorridorPlan.fromSurvey(decode(JSON.readTree(encode(Bundle.of(java.util.List.of(area),java.util.List.of()))).path("areas").path(0),Area.class)).orElseThrow());
        assertEquals(named,decode(JSON.readTree(JSON.writeValueAsString(named)),CorridorPlan.class));
        var oldPlan=JSON.valueToTree(named);((com.fasterxml.jackson.databind.node.ObjectNode)oldPlan).remove("floor");
        assertEquals(pending,decode(oldPlan,CorridorPlan.class));
        var oldArea=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.valueToTree(area);
        oldArea.put("axisNote",area.axisNote().replace(" [corridor-floor:03]",""));
        assertEquals(pending,CorridorPlan.fromSurvey(decode(oldArea,Area.class)).orElseThrow());
        assertEquals("X",new CorridorPlan(6,8,8,3," ").floor());
        assertThrows(IllegalArgumentException.class,()->new CorridorPlan(6,8,8,3,"tầng 3"));
        assertThrows(IllegalArgumentException.class,()->named.roomName(12));
    }
    @Test void templateUsesSharedCoordinatesWithoutInventingWifiSamples(){
        var plan=CorridorPlan.DEFAULT;var area=plan.survey("Tầng thử","HUTECH E1",7);validate(area);
        assertEquals(18,area.points().size());assertEquals(6,area.points().stream().filter(p->p.zone()==Zone.CORRIDOR&&p.y()==0).count());
        assertEquals(plan.roomCenter(7).x(),area.serverX());assertEquals(plan.roomCenter(7).y(),area.serverY());
        assertTrue(area.axisNote().contains("QUY ƯỚC"));assertEquals(0,Bundle.of(java.util.List.of(area),java.util.List.of()).runs().size());
        assertEquals(plan,CorridorPlan.fromSurvey(area).orElseThrow());
    }
    @Test void simulatedRouteExitsBothSidesThroughDoorAndDistanceUsesStraightLine(){
        var p=CorridorPlan.DEFAULT;
        for(int room:new int[]{1,7}){
            var start=p.roomCenter(room);assertEquals(start,p.demoPosition(room,0));
            var end=p.demoPosition(room,1);assertEquals(0,end.y());assertEquals(44,end.x());
            assertEquals(Math.hypot(32,5.5),end.distance(start),1e-8);
            double last=-1;for(int i=0;i<=100;i++){var current=p.demoPosition(room,i/100.0);assertTrue(current.distance(start)>=last);last=current.distance(start);}
        }
        assertThrows(IllegalArgumentException.class,()->p.demoPosition(0,Double.NaN));
        assertEquals(4,p.demoPosition(5,1).x());assertEquals(0,p.demoPosition(5,1).y());
        assertThrows(IllegalArgumentException.class,()->new CorridorPlan(6,Double.POSITIVE_INFINITY,8,3));
    }
    @Test void liveSingleScanSharesWknnButDoesNotBypassSurveyQualityRequirements(){
        var sample=FingerprintDemo.create();var area=sample.bundle().areas().getFirst();var training=sample.bundle().runs().stream().filter(r->!sample.heldOut().contains(r.id())).toList();
        var model=new FingerprintModel(area,training,FingerprintModel.Parameters.baseline(2));
        var query=sample.bundle().runs().get(5);assertTrue(model.estimateLive(query.scans().get(1),FingerprintModel.Method.WKNN).estimated());
        assertFalse(model.estimateLive(sample.bundle().runs().get(7).scans().getFirst(),FingerprintModel.Method.WKNN).estimated());
        assertThrows(IllegalArgumentException.class,()->validate(query.withScans(java.util.List.of(query.scans().getFirst())),area));
    }
}
