package vn.edu.hutech.nqb.client;

import java.util.List;
import java.util.UUID;

public record WifiScanSnapshot(String scanId,String adapterId,String adapterName,String ssidFilter,
                               String associatedBssid,long startedAtMillis,long completedAtMillis,
                               Status status,String diagnostic,int omittedCount,List<WifiObservation> observations) {
    public enum Status { COMPLETE, STALE, NO_RESULTS, TIMEOUT, PERMISSION_DENIED, NO_ADAPTER, WIFI_DISABLED, ERROR, CANCELLED, THROTTLED, UNSUPPORTED }
    public WifiScanSnapshot {observations=List.copyOf(observations);}
    public long freshCount(){return observations.stream().filter(WifiObservation::fresh).count();}
    public static WifiScanSnapshot failure(Status status,String message){
        long now=System.currentTimeMillis();return new WifiScanSnapshot(UUID.randomUUID().toString(),"","","","",now,now,status,message,0,List.of());
    }
}
