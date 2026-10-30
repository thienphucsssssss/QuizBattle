package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.edu.hutech.nqb.shared.WifiSurvey;
import vn.edu.hutech.nqb.shared.fingerprint.FingerprintModel;
import vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan;
import static vn.edu.hutech.nqb.server.Wire.obj;
import static vn.edu.hutech.nqb.server.Wire.fail;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Owned by the room worker. Independent of exam readiness, AP proximity and scoring. */
final class FloorTracker {
    static final String CAPABILITY="FLOOR_TRACKING_V1";
    static final long EXPIRY=TimeUnit.SECONDS.toNanos(20);
    private Area area;
    private FingerprintModel model;
    private CorridorPlan plan=CorridorPlan.DEFAULT;
    private String epoch=UUID.randomUUID().toString();
    private final Map<String,Sample> samples=new HashMap<>();
    private static final class Sample {
        long sequence,received;boolean receivedOnce,visible;
        Double x,y;String status="WAITING",bssid="",adapter="";
        final Map<String,Long> timestamps=new HashMap<>();
    }
    record Prepared(Area area,FingerprintModel model,CorridorPlan plan) { }
    static Prepared prepare(Bundle bundle){
        return prepare(bundle,CorridorPlan.DEFAULT);
    }
    static Prepared prepare(Bundle bundle,CorridorPlan plan){
        validate(bundle);if(bundle.areas().size()!=1)throw invalid("Chọn đúng một phiên bản khu khảo sát.");
        var area=bundle.areas().getFirst();
        return new Prepared(area,new FingerprintModel(area,bundle.runs(),FingerprintModel.Parameters.baseline(2)),CorridorPlan.fromSurvey(area).orElse(plan));
    }
    boolean enabled(){return model!=null;}
    void configure(Prepared value){area=value.area();model=value.model();plan=value.plan();epoch=UUID.randomUUID().toString();samples.clear();}
    void clear(){area=null;model=null;epoch=UUID.randomUUID().toString();samples.clear();}
    ObjectNode config(){var result=obj().put("enabled",model!=null).put("epoch",epoch).put("ssid",area==null?"":area.ssid());result.set("plan",JSON.valueToTree(plan));if(area!=null)result.set("area",JSON.valueToTree(area));return result;}
    void report(String player,JsonNode payload,long now){
        if(model==null||!epoch.equals(payload.path("epoch").asText()))throw fail("FLOOR_CONTEXT_CHANGED","Bản đồ đã đổi. Chờ cấu hình mới.");
        var s=samples.computeIfAbsent(player,k->new Sample());
        if(!payload.path("sequence").isIntegralNumber()||!payload.path("sequence").canConvertToLong()||payload.path("sequence").asLong()<=s.sequence)
            throw fail("FLOOR_OLD_SAMPLE","Mẫu vị trí bị lặp hoặc sai thứ tự.");
        if(s.receivedOnce&&now-s.received<TimeUnit.SECONDS.toNanos(2))throw fail("RATE_LIMITED","Chờ lượt quét Wi-Fi tiếp theo.");
        var scan=decode(payload.path("scan"),Scan.class);
        // Validate before mutating replay state, including radio identities, scan status and timestamps.
        var estimate=model.estimateLive(scan,FingerprintModel.Method.WKNN);
        boolean newAdapter=!s.adapter.equals(scan.adapterId());
        if(newAdapter){s.timestamps.clear();s.x=null;s.y=null;}
        boolean duplicate=scan.observations().stream().filter(Observation::fresh)
            .anyMatch(o->o.hostTimestamp100ns()<=s.timestamps.getOrDefault(o.bssid()+"/"+o.frequencyKhz(),0L));
        if(duplicate)throw fail("FLOOR_OLD_SAMPLE","Scan chứa tín hiệu Wi-Fi đã dùng.");
        // This cache contains only radios from the latest scan, bounded by the scan's 64-radio limit.
        for(var o:scan.observations())if(o.fresh())s.timestamps.put(o.bssid()+"/"+o.frequencyKhz(),o.hostTimestamp100ns());
        if(s.timestamps.size()>512)s.timestamps.keySet().removeIf(k->scan.observations().stream().noneMatch(o->k.equals(o.bssid()+"/"+o.frequencyKhz())));
        boolean smooth=s.visible&&s.receivedOnce&&now-s.received<=EXPIRY;
        s.sequence=payload.path("sequence").asLong();s.received=now;s.receivedOnce=true;s.adapter=scan.adapterId();s.bssid=scan.associatedBssid();s.status=estimate.status().name();s.visible=estimate.estimated();
        if(s.visible){s.x=smooth?s.x+.65*(estimate.x()-s.x):estimate.x();s.y=smooth?s.y+.65*(estimate.y()-s.y):estimate.y();}
    }
    ObjectNode player(String id,String nickname,boolean connected,long now){
        var result=obj().put("player_id",id).put("nickname",nickname);var s=samples.get(id);
        boolean fresh=s!=null&&s.visible&&connected&&now-s.received<=EXPIRY;
        String status=!connected?"DISCONNECTED":s==null?"WAITING":now-s.received>EXPIRY?"STALE":s.status;
        result.put("status",status).put("located",fresh).put("bssid",s==null?"":s.bssid);
        if(s!=null)result.put("age_ms",TimeUnit.NANOSECONDS.toMillis(Math.max(0,now-s.received)));
        if(fresh)result.put("x",s.x).put("y",s.y).put("distance_m",Math.hypot(s.x-area.serverX(),s.y-area.serverY()));
        return result;
    }
    void disconnect(String player){var s=samples.get(player);if(s!=null){s.visible=false;s.sequence=0;s.status="DISCONNECTED";s.receivedOnce=false;}}
    void remove(String player){samples.remove(player);}
}
