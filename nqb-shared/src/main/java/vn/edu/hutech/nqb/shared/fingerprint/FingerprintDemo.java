package vn.edu.hutech.nqb.shared.fingerprint;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Explicitly synthetic offline teaching example. Never discovers Wi-Fi or writes survey storage. */
public final class FingerprintDemo {
    private FingerprintDemo() { }
    public record Sample(Bundle bundle, Set<String> heldOut) { }
    private static final long START = 1760000000000L;
    private static String key(String name) { return UUID.nameUUIDFromBytes(("nqb-synthetic/"+name).getBytes(StandardCharsets.UTF_8)).toString(); }
    private static Point point(String name, Zone zone, double x, double y) { return new Point(key(name),name,zone,x,y); }

    public static Sample create() {
        var p0=point("Cửa phòng",Zone.CORRIDOR,0,0);
        var p5=point("Hành lang 5 m",Zone.CORRIDOR,5,0);
        var p10=point("Hành lang 10 m",Zone.CORRIDOR,10,0);
        var room0=point("Trong phòng A",Zone.ROOM,0,4);
        var room4=point("Trong phòng B",Zone.ROOM,4,4);
        var hallTest=point("Kiểm tra hành lang",Zone.CORRIDOR,2,0);
        var roomTest=point("Kiểm tra trong phòng",Zone.ROOM,2,4);
        var area=new Area(key("area"),1,"Mô phỏng tại nhà · không phải HUTECH","Dữ liệu tổng hợp","DEMO-OFFLINE","m",
            "X dọc hành lang, Y hướng vào phòng. Tọa độ và tín hiệu đều giả lập.",0,4,START,List.of(p0,p5,p10,room0,room4,hallTest,roomTest));
        var runs=List.of(run(area,p0,0,false,-45,-55,-65),run(area,p5,1,false,-55,-45,-55),run(area,p10,2,false,-65,-55,-45),
            run(area,room0,3,false,-80,-40,-85),run(area,room4,4,false,-86,-46,-79),
            run(area,hallTest,5,false,-49,-51,-61),run(area,roomTest,6,false,-83,-43,-82),run(area,hallTest,7,true,-50,-60,-70));
        var bundle=Bundle.of(List.of(area),runs);validate(bundle);
        return new Sample(bundle,Set.of(runs.get(5).id(),runs.get(6).id(),runs.get(7).id()));
    }
    private static Run run(Area area,Point point,int sequence,boolean unknown,int...levels) {
        long start=START+sequence*60000L;var scans=new ArrayList<Scan>();
        for(int i=0;i<3;i++) {
            long time=start+i*5000L;var observations=new ArrayList<Observation>();
            for(int j=0;j<levels.length;j++)observations.add(new Observation(area.ssid(),String.format(Locale.ROOT,"02:00:00:%02x:00:%02x",unknown?1:0,j+1),
                levels[j]+i-1,5180000,(time+5+WINDOWS_EPOCH_MS)*10000,5,true,false));
            scans.add(new Scan(key("scan-"+sequence+"-"+i),"demo-adapter","Card giả lập",area.ssid(),"",time,time+10,"COMPLETE","MÔ PHỎNG",0,observations));
        }
        return new Run(key("run-"+sequence),area.id(),1,point.id(),"demo-adapter","Card giả lập","Laptop giả lập","Offline demo","Nhìn theo X tăng",start,start+10010,State.ACCEPTED,
            unknown?"Mô phỏng radio chưa được khảo sát; phải từ chối định vị.":"Dữ liệu tổng hợp, không chứng minh sai số thực địa.",scans);
    }
}
