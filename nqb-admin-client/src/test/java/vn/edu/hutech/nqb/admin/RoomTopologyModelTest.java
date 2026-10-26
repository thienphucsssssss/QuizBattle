package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

class RoomTopologyModelTest {
    private static final Instant NOW=Instant.parse("2026-10-25T15:00:00Z");
    @Test void onlyActualPlayersAppearAndCachedConnectionsAreUnconfirmed(){
        var room=object().put("room_code","ABC123").put("max_players",10);
        room.putArray("players").add(object().put("player_id","p1").put("nickname","An").put("ready",true).put("connected",true))
            .add(object().put("player_id","p2").put("nickname","Bình").put("connected",false));
        var nodes=RoomTopologyModel.participants(room,true,NOW);
        assertEquals(2,nodes.size());assertEquals("p1",nodes.getFirst().id());assertEquals("Sẵn sàng",nodes.getFirst().state());
        assertEquals("Mất kết nối",nodes.get(1).state());
        assertEquals("Chưa xác nhận",RoomTopologyModel.participants(room,false,NOW).getFirst().state());
        assertTrue(RoomTopologyModel.participants(object(),true,NOW).isEmpty());
    }
    @Test void doesNotPromoteManualDistanceOrPingIntoHardwareMetres(){
        var player=object().put("distance_m",31).put("ping_ms",2);
        player.set("lan_range",object().put("distance_m",30).put("passed",true));
        assertEquals("Chưa có dữ liệu RTT",RoomTopologyModel.distance(player,true,NOW));
        player.set("wifi_rtt_measurement",object().put("method","RSSI").put("distance_m",31).put("reference","SERVER")
            .put("status","MEASURED").put("measured_at",NOW.toString()));
        assertEquals("Chưa có dữ liệu RTT",RoomTopologyModel.distance(player,true,NOW));
    }
    @Test void hardwareEvidenceMustBeFreshAndNameItsActualReference(){
        var measure=object().put("method","WIFI_RTT").put("status","MEASURED").put("reference","SERVER")
            .put("distance_m",31.4).put("measured_at",NOW.toString());
        var player=object().set("wifi_rtt_measurement",measure);
        assertEquals("31,4 m",RoomTopologyModel.distance(player,true,NOW));
        measure.put("reference","AP");
        assertEquals("AP: 31,4 m",RoomTopologyModel.distance(player,true,NOW));
        measure.put("measured_at",NOW.minusSeconds(31).toString());
        assertEquals("RTT đã cũ",RoomTopologyModel.distance(player,true,NOW));
        measure.put("measured_at",NOW.toString()).put("distance_m",-1);
        assertEquals("Chưa có dữ liệu RTT",RoomTopologyModel.distance(player,true,NOW));
        measure.put("distance_m",31);
        assertEquals("Chưa xác nhận RTT",RoomTopologyModel.distance(player,false,NOW));
    }
    @Test void orbitLayoutFitsTenClientsWithoutOverlappingCardsOrHub(){
        for(double width:new double[]{620,720,1000})for(int count=1;count<=10;count++){
            double height=RoomTopologyModel.preferredHeight(count);
            var points=RoomTopologyModel.positions(width,height,count);
            assertEquals(count,points.size());
            for(int i=0;i<count;i++){
                var p=points.get(i);
                assertTrue(p.x()>=0&&p.x()+132<=width,"Horizontal clipping");
                assertTrue(p.y()>=0&&p.y()+108<=height,"Vertical clipping");
                assertFalse(overlap(p.x(),p.y(),132,108,width/2-70,height/2-70,140,140),"Client overlaps room");
                for(int j=0;j<i;j++){var q=points.get(j);assertFalse(overlap(p.x(),p.y(),132,108,q.x(),q.y(),132,108),"Client cards overlap");}
            }
        }
    }
    private boolean overlap(double x,double y,double w,double h,double a,double b,double c,double d){
        return x<a+c-1&&x+w>a+1&&y<b+d-1&&y+h>b+1;
    }
}
