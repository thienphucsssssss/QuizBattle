package vn.edu.hutech.nqb.client;

import java.util.List;

/** Active scan, separate from reading the currently associated AP's signal. */
public interface WifiScanProvider {
    record Adapter(String id,String name,boolean connected) {
        @Override public String toString(){return name+(connected?" · đang kết nối":"");}
    }
    List<Adapter> scanAdapters();
    WifiScanSnapshot scan(String adapterId,String ssid);
    void cancelScan();
}
