package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.time.*;
import java.util.*;

/** Display-only topology: node positions never represent a measured physical position. */
final class RoomTopologyModel {
    record Participant(String id,String nickname,String avatar,String state,String stateStyle,String ip,String distance){}
    record Point(double x,double y){}
    static List<Participant> participants(JsonNode room,boolean live,Instant now){
        var result=new ArrayList<Participant>();var seen=new HashSet<String>();
        for(var p:room.path("players")){
            String id=p.path("player_id").asText();if(id.isBlank()||!seen.add(id))continue;
            boolean connected=p.path("connected").asBoolean();
            String state=!live?"Chưa xác nhận":!connected?"Mất kết nối":p.path("ready").asBoolean()?"Sẵn sàng":"Đang chuẩn bị";
            String style=!live?"unknown":!connected?"offline":p.path("ready").asBoolean()?"ready":"preparing";
            result.add(new Participant(id,p.path("nickname").asText("Thí sinh"),p.path("avatar_id").asText(),
                state,style,p.path("ip_address").asText(),distance(p,live&&connected,now)));
        }
        return List.copyOf(result);
    }
    /** Only explicitly attributed, fresh hardware evidence is eligible. Manual LAN-range records are ignored. */
    static String distance(JsonNode player,boolean live,Instant now){
        if(!live)return "Chưa xác nhận RTT";
        var data=player.path("wifi_rtt_measurement");
        if(!data.path("method").asText().equals("WIFI_RTT")||!data.path("status").asText().equals("MEASURED")
            ||!data.path("distance_m").isNumber())return "Chưa có dữ liệu RTT";
        String reference=data.path("reference").asText();
        if(!Set.of("SERVER","AP").contains(reference))return "Chưa có dữ liệu RTT";
        try{
            double metres=data.path("distance_m").asDouble();
            if(!Double.isFinite(metres)||metres<0||metres>500)return "Chưa có dữ liệu RTT";
            Instant at=Instant.parse(data.path("measured_at").asText());
            if(at.isAfter(now.plusSeconds(2)))return "Chưa có dữ liệu RTT";
            if(at.isBefore(now.minusSeconds(30)))return "RTT đã cũ";
            String number=data.path("distance_m").decimalValue().setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',');
            return (reference.equals("AP")?"AP: ":"")+number+" m";
        }catch(DateTimeException|ArithmeticException invalid){return "Chưa có dữ liệu RTT";}
    }
    static double preferredHeight(int count){return count>8?640:count>6?540:count>4?480:400;}
    static List<Point> positions(double width,double height,int count){
        if(count<0||count>10)throw new IllegalArgumentException("Topology supports at most ten room participants.");
        var result=new ArrayList<Point>();
        double cx=width/2,cy=height/2,rx=Math.max(0,cx-66-14),ry=Math.max(0,cy-54-14);
        for(int i=0;i<count;i++){
            double angle=-Math.PI/2+i*Math.PI*2/count;
            result.add(new Point(cx+Math.cos(angle)*rx-66,cy+Math.sin(angle)*ry-54));
        }
        return List.copyOf(result);
    }
    private RoomTopologyModel(){}
}
