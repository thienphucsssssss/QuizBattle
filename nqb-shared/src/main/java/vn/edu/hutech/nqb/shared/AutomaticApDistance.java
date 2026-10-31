package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Log-distance inversion with explicit, uncalibrated demonstration assumptions. */
public final class AutomaticApDistance {
    public static final String CAPABILITY="AP_DISTANCE_AUTO_V1";
    public static final double EXPONENT=3.0;
    private final Deque<Integer> samples=new ArrayDeque<>();
    private String adapter="",ssid="",bssid="",band="",status="WAITING";
    private long received;
    private boolean measured;
    public static double referenceRssi(String band){return switch(ProximityRules.normalizeBand(band)){
        case "2.4 GHz"->-40;case "5 GHz"->-46;case "6 GHz"->-48;
        default->throw new IllegalArgumentException("Không có băng tần Wi-Fi hợp lệ.");
    };}
    public synchronized void accept(String adapter,String ssid,String bssid,String band,List<Integer> values,long now){
        String mac=ProximityRules.normalizeBssid(bssid),frequency=ProximityRules.normalizeBand(band);
        if(adapter==null||adapter.isBlank()||ssid==null||mac.isBlank()||frequency.isBlank()||values==null||values.isEmpty()
            ||values.size()>ProximityRules.MEDIAN_WINDOW_SIZE||values.stream().anyMatch(v->v==null||!ProximityRules.isValidRssi(v)))
            throw new IllegalArgumentException("Mẫu khoảng cách AP không hợp lệ.");
        if(!this.adapter.equals(adapter)||!this.ssid.equals(ssid)||!this.bssid.equals(mac)||!this.band.equals(frequency)
            ||!measured||now-received>TimeUnit.MILLISECONDS.toNanos(ProximityRules.STALE_TIMEOUT_MS))samples.clear();
        this.adapter=adapter;this.ssid=ssid;this.bssid=mac;this.band=frequency;
        for(int value:values){if(samples.size()==ProximityRules.MEDIAN_WINDOW_SIZE)samples.removeFirst();samples.addLast(value);}
        status="CONNECTED";received=now;measured=true;
    }
    public synchronized void unavailable(String reason,long now){status=reason;received=now;measured=false;samples.clear();adapter=ssid=bssid=band="";}
    public synchronized ObjectNode snapshot(boolean connected,long now){
        long age=TimeUnit.NANOSECONDS.toMillis(Math.max(0,now-received));
        boolean fresh=connected&&measured&&age<=ProximityRules.STALE_TIMEOUT_MS;
        var node=WifiSurvey.JSON.createObjectNode().put("mode","MODEL_DEFAULT").put("calibrated",false).put("source","LOCAL_WIFI_RSSI")
            .put("status",!connected?"DISCONNECTED":measured&&!fresh?"STALE":status).put("fresh",fresh)
            .put("ssid",ssid).put("bssid",bssid).put("band",band).put("adapter_id",adapter).put("age_ms",measured?age:-1)
            .put("distance_label","Chưa có số đo AP").put("method_label","Ước lượng thô · chưa hiệu chuẩn");
        if(fresh){
            var values=List.copyOf(samples);double median=ProximityRules.median(values),reference=referenceRssi(band);
            double raw=Math.pow(10,(reference-median)/(10*EXPONENT));double meters=ProximityRules.estimateDistanceMeters(reference,EXPONENT,median);
            node.put("estimated_meters",Math.round(meters*10)/10.0).put("median_rssi_dbm",median).put("mad_db",ProximityRules.mad(values))
                .put("reference_rssi_1m",reference).put("path_loss_n",EXPONENT).put("sample_count",samples.size()).put("clamped",raw<.2||raw>200)
                .put("distance_label",raw<.2?"≤ 0.2 m":raw>200?"≥ 200 m":String.format(Locale.ROOT,"≈ %.1f m",meters));
        }
        return node;
    }
}
