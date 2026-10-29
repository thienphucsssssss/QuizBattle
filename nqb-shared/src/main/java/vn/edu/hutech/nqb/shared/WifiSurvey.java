package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Portable survey data. Coordinates are measured by the operator, never inferred from AP order. */
public final class WifiSurvey {
    public static final String CAPABILITY="WIFI_SURVEY_V1";
    public static final int MAX_POINTS=100, MAX_SCANS=60, MAX_RUNS=500, MAX_FILE_BYTES=32*1024*1024;
    public static final long WINDOWS_EPOCH_MS=11644473600000L;
    public static final ObjectMapper JSON=new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    static { JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(24).maxStringLength(65536).build()); }
    private WifiSurvey() { }
    public enum Zone { ROOM, CORRIDOR }
    public enum State { CAPTURING, REVIEW, ACCEPTED, DISCARDED, INTERRUPTED }
    public record Point(String id,String name,Zone zone,double x,double y) {
        public String toString(){return name+" · "+(zone==Zone.ROOM?"Phòng":"Hành lang")+" ("+x+", "+y+") m";}
    }
    public record Area(String id,int revision,String name,String collector,String ssid,String units,
                       String axisNote,double serverX,double serverY,long createdAtMillis,List<Point> points) {
        public Area { points=List.copyOf(points); }
        public String toString(){return name+" · v"+revision;}
    }
    public record Observation(String ssid,String bssid,int rssiDbm,int frequencyKhz,long hostTimestamp100ns,
                              long ageMillis,boolean fresh,boolean associated) { }
    public record Scan(String scanId,String adapterId,String adapterName,String ssidFilter,String associatedBssid,
                       long startedAtMillis,long completedAtMillis,String status,String diagnostic,int omittedCount,
                       List<Observation> observations) {
        public Scan { observations=List.copyOf(observations); }
    }
    public record Run(String id,String areaId,int revision,String pointId,String adapterId,String adapterName,
                      String deviceLabel,String platform,String orientation,long startedAtMillis,long endedAtMillis,
                      State state,String note,List<Scan> scans) {
        public Run { scans=List.copyOf(scans); }
        public Run withScans(List<Scan> values){return new Run(id,areaId,revision,pointId,adapterId,adapterName,deviceLabel,platform,orientation,startedAtMillis,endedAtMillis,state,note,values);}
        public Run finish(State value,String detail,long now){return new Run(id,areaId,revision,pointId,adapterId,adapterName,deviceLabel,platform,orientation,startedAtMillis,Math.max(startedAtMillis,now),value,detail,scans);}
    }
    public record Bundle(int schemaVersion,String purpose,String units,List<Area> areas,List<Run> runs) {
        public Bundle { areas=List.copyOf(areas);runs=List.copyOf(runs); }
        public static Bundle of(List<Area> areas,List<Run> runs){return new Bundle(1,"wifi_fingerprint_survey","m",areas,runs);}
    }
    public record Quality(int scans,int freshScans,int radios,int staleObservations) {
        public boolean canAccept(){return freshScans>=3&&radios>=2;}
        public String summary(){return freshScans+" / "+scans+" scan mới hợp lệ · "+radios+" BSSID/tần số · "+staleObservations+" mẫu cũ";}
    }
    public static String id(){return UUID.randomUUID().toString();}
    public static <T>T decode(JsonNode value,Class<T> type){
        try{return JSON.treeToValue(value,type);}catch(Exception error){throw invalid("Dữ liệu khảo sát không đúng định dạng.");}
    }
    public static String encode(Object value){try{return JSON.writeValueAsString(value);}catch(Exception error){throw invalid("Không mã hóa được dữ liệu khảo sát.");}}
    public static String digest(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static void check(boolean test,String message){if(!test)throw invalid(message);}
    private static void text(String value,int max,boolean blank,String name){check(value!=null&&value.length()<=max&&(blank||!value.isBlank())&&value.chars().noneMatch(c->Character.isISOControl(c)),name+" không hợp lệ.");}
    private static void uuid(String value){check(value!=null&&value.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"),"Mã khảo sát không hợp lệ.");}
    private static void coord(double value){check(Double.isFinite(value)&&Math.abs(value)<=10000,"Tọa độ phải là số hữu hạn trong ±10000 mét.");}
    public static void validate(Area area){
        check(area!=null,"Thiếu khu khảo sát.");uuid(area.id);check(area.revision>=1&&area.revision<=10000,"Phiên bản khu không hợp lệ.");
        text(area.name,80,false,"Tên khu");text(area.collector,80,false,"Người khảo sát");text(area.ssid,32,false,"SSID");
        check(area.ssid.getBytes(StandardCharsets.UTF_8).length<=32,"SSID tối đa 32 byte.");check("m".equals(area.units),"Đơn vị phải là mét (m).");
        text(area.axisNote,160,false,"Quy ước trục");coord(area.serverX);coord(area.serverY);check(area.createdAtMillis>0,"Thiếu thời điểm tạo khu.");
        check(!area.points.isEmpty()&&area.points.size()<=MAX_POINTS,"Khu cần 1–100 điểm khảo sát.");
        var ids=new HashSet<String>();var names=new HashSet<String>();
        for(var p:area.points){uuid(p.id);text(p.name,80,false,"Tên điểm");check(p.zone!=null,"Thiếu vùng phòng/hành lang.");coord(p.x);coord(p.y);
            check(p.zone!=Zone.CORRIDOR||p.y==0,"Điểm hành lang nằm trên trục X: Y = 0.");
            check(ids.add(p.id)&&names.add(p.name.toLowerCase(Locale.ROOT)),"Trùng mã hoặc tên điểm trong khu.");}
    }
    public static void validate(Run run,Area area){
        validate(area);check(run!=null,"Thiếu lượt thu.");uuid(run.id);check(area.id.equals(run.areaId)&&run.revision==area.revision,"Lượt thu không thuộc phiên bản khu.");
        check(area.points.stream().anyMatch(p->p.id.equals(run.pointId)),"Điểm thu không có trong khu.");
        text(run.adapterId,100,false,"Card Wi-Fi");text(run.adapterName,200,false,"Tên card Wi-Fi");text(run.deviceLabel,80,false,"Nhãn laptop");
        text(run.platform,160,false,"Nền tảng");text(run.orientation,80,false,"Hướng cầm máy");text(run.note,500,true,"Ghi chú");
        check(run.state!=null&&run.startedAtMillis>0&&run.endedAtMillis>=run.startedAtMillis&&run.endedAtMillis<=4102444800000L,"Trạng thái/thời gian lượt thu không hợp lệ.");
        check(run.endedAtMillis-run.startedAtMillis<=600000,"Lượt thu vượt quá 10 phút.");check(run.scans.size()<=MAX_SCANS,"Lượt thu vượt quá 60 scan.");
        var scanIds=new HashSet<String>();var lastHost=new HashMap<String,Long>();long lastEnd=run.startedAtMillis;
        for(var s:run.scans){
            uuid(s.scanId);check(scanIds.add(s.scanId),"Trùng scan trong lượt thu.");
            check(run.adapterId.equals(s.adapterId)&&area.ssid.equals(s.ssidFilter),"Scan khác card Wi-Fi hoặc SSID khảo sát.");
            text(s.adapterName,200,false,"Tên card");text(s.diagnostic,500,true,"Chẩn đoán");
            check(s.status!=null&&Set.of("COMPLETE","STALE","NO_RESULTS","TIMEOUT","PERMISSION_DENIED","NO_ADAPTER","WIFI_DISABLED","ERROR","CANCELLED","THROTTLED","UNSUPPORTED").contains(s.status),"Trạng thái scan không hợp lệ.");
            check(s.startedAtMillis>=lastEnd&&s.completedAtMillis>=s.startedAtMillis&&s.completedAtMillis<=run.endedAtMillis,"Thời gian scan nằm ngoài lượt thu hoặc bị chồng lấn.");lastEnd=s.completedAtMillis;
            check(s.observations.size()<=64&&s.omittedCount>=0,"Scan vượt giới hạn quan sát.");
            check(s.associatedBssid!=null&&(s.associatedBssid.isEmpty()||mac(s.associatedBssid)),"BSSID kết nối không hợp lệ.");var radioKeys=new HashSet<String>();
            for(var o:s.observations){
                check(area.ssid.equals(o.ssid)&&mac(o.bssid)&&o.rssiDbm>=-110&&o.rssiDbm<=-10&&o.frequencyKhz>=2300000&&o.frequencyKhz<=7125000,"Quan sát Wi-Fi không hợp lệ.");
                check(o.ageMillis>=-1&&o.hostTimestamp100ns>=0,"Tuổi mẫu không hợp lệ.");String key=o.bssid+"/"+o.frequencyKhz;check(radioKeys.add(key),"Trùng BSSID/tần số trong scan.");
                if(o.fresh){long ms=o.hostTimestamp100ns/10000-WINDOWS_EPOCH_MS;
                    check(s.status.equals("COMPLETE")&&ms>=s.startedAtMillis&&ms<=s.completedAtMillis&&o.hostTimestamp100ns>lastHost.getOrDefault(key,0L),"Mẫu đánh dấu mới nhưng timestamp cũ, lặp hoặc không hợp lệ.");
                    check(o.ageMillis==s.completedAtMillis-ms,"Tuổi mẫu không khớp timestamp.");}
                if(o.fresh)lastHost.merge(key,o.hostTimestamp100ns,Math::max);
            }
            check(encode(s).getBytes(StandardCharsets.UTF_8).length<=48000,"Scan quá lớn để đồng bộ.");
        }
        check(run.state!=State.ACCEPTED||quality(run).canAccept(),"Cần ít nhất 3 scan mới và 2 BSSID/tần số để xác nhận lượt thu; đây chưa phải bảo đảm độ chính xác.");
    }
    public static Quality quality(Run run){
        int fresh=0,stale=0;var radios=new HashSet<String>();
        for(var s:run.scans){boolean any=false;for(var o:s.observations){if(o.fresh){any=true;radios.add(o.bssid+"/"+o.frequencyKhz);}else stale++;}if(any)fresh++;}
        return new Quality(run.scans.size(),fresh,radios.size(),stale);
    }
    public static void validate(Bundle bundle){
        check(bundle.schemaVersion==1&&"wifi_fingerprint_survey".equals(bundle.purpose)&&"m".equals(bundle.units),"Không phải JSON khảo sát phiên bản 1, đơn vị mét.");
        check(bundle.areas.size()<=200&&bundle.runs.size()<=MAX_RUNS,"Tệp vượt giới hạn 200 phiên bản khu / 500 lượt thu.");
        var areas=new HashMap<String,Area>();for(var a:bundle.areas){validate(a);check(areas.put(a.id+"/"+a.revision,a)==null,"Trùng phiên bản khu trong tệp.");}
        var runs=new HashSet<String>();var scanIds=new HashSet<String>();for(var r:bundle.runs){check(r.state!=State.CAPTURING,"Không nhập lượt đang thu mẫu.");var a=areas.get(r.areaId+"/"+r.revision);check(a!=null,"Tệp thiếu phiên bản khu của lượt thu.");validate(r,a);check(runs.add(r.id),"Trùng lượt thu trong tệp.");for(var s:r.scans)check(scanIds.add(s.scanId),"Một scan không được gán cho nhiều lượt/điểm.");}
    }
    private static boolean mac(String value){return value!=null&&value.matches("([0-9a-f]{2}:){5}[0-9a-f]{2}")&&!value.equals("00:00:00:00:00:00")&&!value.equals("ff:ff:ff:ff:ff:ff");}
}
