package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Bounded local observations, not a physical-AP map or a surveyed fingerprint dataset. */
public final class WifiObservationCatalog {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path file;
    public WifiObservationCatalog(){this(Path.of(System.getProperty("nqb.wifi.catalog",Path.of(System.getProperty("user.home"),".nqb","wifi-observed.json").toString())));}
    public WifiObservationCatalog(Path file){this.file=file.toAbsolutePath();}
    public Path path(){return file;}
    public synchronized int record(WifiScanSnapshot scan)throws IOException {
        var rows=new LinkedHashMap<String,ObjectNode>();
        if(Files.exists(file)){
            if(Files.size(file)>4*1024*1024)throw new IOException("Danh mục BSSID vượt giới hạn 4 MiB.");
            var saved=JSON.readTree(Files.readString(file));
            if(saved.path("schema_version").asInt()!=1||!saved.path("observations").isArray()||saved.path("observations").size()>2048)throw new IOException("Danh mục BSSID không đúng định dạng.");
            for(var row:saved.path("observations")){
                if(!(row instanceof ObjectNode object)||!row.path("bssid").asText().matches("([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IOException("BSSID đã lưu không hợp lệ.");
                rows.put(key(row.path("adapter_id").asText(),row.path("bssid").asText(),row.path("frequency_khz").asInt()),object);
            }
        }
        for(var observed:scan.observations()){
            if(!observed.fresh())continue;
            String key=key(scan.adapterId(),observed.bssid(),observed.frequencyKhz());var row=rows.remove(key);
            if(row==null)row=JSON.createObjectNode().put("first_seen_ms",scan.completedAtMillis());
            row.put("last_seen_ms",scan.completedAtMillis()).put("adapter_id",scan.adapterId()).put("adapter_name",scan.adapterName())
                .put("ssid",observed.ssid()).put("bssid",observed.bssid()).put("frequency_khz",observed.frequencyKhz()).put("rssi_dbm",observed.rssiDbm());
            rows.put(key,row);
        }
        while(rows.size()>2048)rows.remove(rows.keySet().iterator().next());
        var document=JSON.createObjectNode().put("schema_version",1);var array=document.putArray("observations");rows.values().forEach(array::add);
        atomicWrite(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document));return rows.size();
    }
    public static void export(Path destination,WifiScanSnapshot snapshot)throws IOException {
        var data=JSON.createObjectNode().put("schema_version",1).put("purpose","wifi_scan_diagnostic");data.set("scan",JSON.valueToTree(snapshot));
        atomicWrite(destination.toAbsolutePath(),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));
    }
    private static String key(String adapter,String bssid,int frequency){return adapter+"|"+bssid+"|"+frequency;}
    private static void atomicWrite(Path target,String text)throws IOException {
        Files.createDirectories(target.getParent());var temp=Files.createTempFile(target.getParent(),"wifi-",".tmp");
        try {
            Files.writeString(temp,text);
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
