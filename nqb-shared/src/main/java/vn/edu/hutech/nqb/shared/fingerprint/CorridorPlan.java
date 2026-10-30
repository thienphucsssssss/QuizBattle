package vn.edu.hutech.nqb.shared.fingerprint;

import java.util.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Explicit schematic dimensions, not claimed averages or measured HUTECH geometry. */
public record CorridorPlan(int roomsPerSide,double roomWidth,double roomDepth,double corridorWidth) {
    public static final CorridorPlan DEFAULT=new CorridorPlan(6,8,8,3);
    public CorridorPlan {
        if(roomsPerSide<2||roomsPerSide>10||!valid(roomWidth,3,20)||!valid(roomDepth,3,20)||!valid(corridorWidth,1,8))
            throw invalid("Kích thước sơ đồ không hợp lệ.");
    }
    private static boolean valid(double v,double min,double max){return Double.isFinite(v)&&v>=min&&v<=max;}
    public double length(){return roomsPerSide*roomWidth;}
    public double halfHeight(){return corridorWidth/2+roomDepth;}
    public record Position(double x,double y) {public double distance(Position other){return Math.hypot(x-other.x,y-other.y);}}
    public Position roomCenter(int room){
        if(room<0||room>=roomsPerSide*2)throw invalid("Phòng không có trên sơ đồ.");
        return new Position((room%roomsPerSide+.5)*roomWidth,(room<roomsPerSide?1:-1)*(corridorWidth/2+roomDepth/2));
    }
    public String roomName(int room){roomCenter(room);return (room<roomsPerSide?"A":"B")+(room%roomsPerSide+1);}
    /** Optional versioned metadata in the existing portable area's axis note; old surveys still work. */
    public static Optional<CorridorPlan> fromSurvey(Area area){
        var match=java.util.regex.Pattern.compile("\\[corridor-v1:(\\d+),([0-9.]+),([0-9.]+),([0-9.]+)\\]").matcher(area.axisNote());
        if(!match.find())return Optional.empty();
        return Optional.of(new CorridorPlan(Integer.parseInt(match.group(1)),Double.parseDouble(match.group(2)),Double.parseDouble(match.group(3)),Double.parseDouble(match.group(4))));
    }
    /** Travels through a door, then along the corridor. Progress is a simulation control only. */
    public Position demoPosition(int room,double progress){
        if(!Double.isFinite(progress)||progress<0||progress>1)throw invalid("Tiến độ mô phỏng không hợp lệ.");
        var start=roomCenter(room);double end=start.x()<=length()/2?length()-roomWidth/2:roomWidth/2;
        double step=progress*(Math.abs(start.y())+Math.abs(end-start.x()));
        return step<=Math.abs(start.y())?new Position(start.x(),Math.copySign(Math.abs(start.y())-step,start.y())):
            new Position(start.x()+Math.copySign(step-Math.abs(start.y()),end-start.x()),0);
    }
    public Area survey(String name,String ssid,int serverRoom){
        var server=roomCenter(serverRoom);var points=new ArrayList<Point>();
        for(int i=0;i<roomsPerSide;i++){
            double x=(i+.5)*roomWidth;
            points.add(new Point(id(),"Hành lang trước "+roomName(i)+" / "+roomName(i+roomsPerSide),Zone.CORRIDOR,x,0));
            for(int room:new int[]{i,i+roomsPerSide}){var p=roomCenter(room);points.add(new Point(id(),"Trong phòng "+roomName(room),Zone.ROOM,p.x(),p.y()));}
        }
        var area=new Area(id(),1,name,"Sơ đồ quy ước",ssid,"m",
            "KÍCH THƯỚC QUY ƯỚC; chưa đo thực tế. X dọc hành lang, Y dương về dãy A. Phòng "+roomWidth+" × "+roomDepth+" m; hành lang "+corridorWidth+" m. [corridor-v1:"+roomsPerSide+","+roomWidth+","+roomDepth+","+corridorWidth+"]",
            server.x(),server.y(),System.currentTimeMillis(),points);
        validate(area);return area;
    }
}
