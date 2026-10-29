package vn.edu.hutech.nqb.client;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WindowsWifiScanTest {
    @TempDir Path directory;
    static final byte[] GUID={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
    static final String ADAPTER=WindowsWifiSignalProvider.formatGuid(GUID);
    static Memory bss(long millis,int count){
        var memory=new Memory(8L+count*360);memory.clear();memory.setInt(0,(int)memory.size());memory.setInt(4,count);
        for(int i=0;i<count;i++){
            long offset=8L+i*360;byte[] ssid="HUTECH E1".getBytes(StandardCharsets.UTF_8);memory.setInt(offset,ssid.length);memory.write(offset+4,ssid,0,ssid.length);
            memory.write(offset+40,new byte[]{2,1,2,3,4,(byte)i},0,6);memory.setInt(offset+56,-45-i%60);memory.setInt(offset+92,5_180_000);
            memory.setLong(offset+80,(millis+11644473600000L)*10000);
        }return memory;
    }
    static final class Fake implements WindowsWifiSignalProvider.WlanApi {
        WindowsWifiSignalProvider.ScanCallback callback;int frees,allocations,unregistered,scanCode,notification=7;long stamp;boolean suppress;volatile boolean started;
        List<Memory> memory=new ArrayList<>();
        Memory allocate(long bytes){var m=new Memory(bytes);m.clear();memory.add(m);allocations++;return m;}
        public int WlanOpenHandle(int v,Pointer r,IntByReference n,PointerByReference h){h.setValue(new Pointer(1));return 0;}
        public int WlanCloseHandle(Pointer h,Pointer r){return 0;}
        public int WlanEnumInterfaces(Pointer h,Pointer r,PointerByReference ref){var m=allocate(540);m.setInt(0,1);m.write(8,GUID,0,16);m.setInt(536,1);ref.setValue(m);return 0;}
        public int WlanQueryInterface(Pointer h,Pointer g,int op,Pointer r,IntByReference size,PointerByReference ref,IntByReference type){return 50;}
        public int WlanGetNetworkBssList(Pointer h,Pointer g,Pointer ssid,int type,boolean security,Pointer reserved,PointerByReference ref){var m=bss(stamp,3);memory.add(m);allocations++;ref.setValue(m);return 0;}
        public void WlanFreeMemory(Pointer ptr){frees++;}
        public int WlanScan(Pointer h,Pointer guid,Pointer ssid,Pointer ie,Pointer reserved){
            started=true;if(scanCode!=0)return scanCode;if(stamp==0)stamp=System.currentTimeMillis();
            if(!suppress){try(var notificationData=new Memory(40)){notificationData.clear();notificationData.setInt(0,8);notificationData.setInt(4,notification);notificationData.write(8,GUID,0,16);callback.invoke(notificationData,null);}}
            return 0;
        }
        public int WlanRegisterNotification(Pointer h,int source,boolean ignore,WindowsWifiSignalProvider.ScanCallback cb,Pointer context,Pointer reserved,IntByReference previous){callback=cb;if(source==0)unregistered++;return 0;}
    }
    @Test void freshDuplicateOldFutureAndMalformedEntriesAreHandledWithoutInventingFreshScans(){
        var seen=new LinkedHashMap<String,Long>();long now=System.currentTimeMillis();
        try(var data=bss(now,3)){
            var first=WindowsWifiSignalProvider.parseScanEntries(data,ADAPTER,"HUTECH E1","02:01:02:03:04:00",now-10,now+10,seen);
            assertEquals(3,first.size());assertTrue(first.stream().allMatch(WifiObservation::fresh));assertTrue(first.getFirst().associated());
            assertTrue(WindowsWifiSignalProvider.parseScanEntries(data,ADAPTER,"HUTECH E1","",now-10,now+10,seen).stream().noneMatch(WifiObservation::fresh));
            assertTrue(WindowsWifiSignalProvider.parseScanEntries(data,ADAPTER,"Other","",now,now,seen).isEmpty());
            data.setInt(4,500);assertThrows(IllegalArgumentException.class,()->WindowsWifiSignalProvider.parseScanEntries(data,ADAPTER,"","",now,now,seen));
        }
        try(var old=bss(now-1000,1);var future=bss(now+1000,1)){
            assertFalse(WindowsWifiSignalProvider.parseScanEntries(old,ADAPTER,"","",now,now,new HashMap<>()).getFirst().fresh());
            var value=WindowsWifiSignalProvider.parseScanEntries(future,ADAPTER,"","",now,now,new HashMap<>()).getFirst();assertFalse(value.fresh());assertEquals(-1,value.ageMillis());
        }
    }
    @Test void waitsForCompletionRejectsCacheAndAlwaysUnregistersAndFreesBuffers()throws Exception {
        var fake=new Fake();try(var provider=new WindowsWifiSignalProvider(fake,100,0)){
            assertEquals(1,provider.scanAdapters().size());
            var first=provider.scan(ADAPTER,"HUTECH E1");assertEquals(WifiScanSnapshot.Status.COMPLETE,first.status());assertEquals(3,first.freshCount());
            var second=provider.scan(ADAPTER,"HUTECH E1");assertEquals(WifiScanSnapshot.Status.STALE,second.status());assertEquals(0,second.freshCount());
            assertEquals(fake.allocations,fake.frees);assertEquals(2,fake.unregistered);
            fake.suppress=true;assertEquals(WifiScanSnapshot.Status.TIMEOUT,provider.scan(ADAPTER,"").status());
            fake.suppress=false;fake.notification=8;assertEquals(WifiScanSnapshot.Status.ERROR,provider.scan(ADAPTER,"").status());
            fake.scanCode=5;assertEquals(WifiScanSnapshot.Status.PERMISSION_DENIED,provider.scan(ADAPTER,"").status());
            assertEquals(5,fake.unregistered);assertEquals(fake.allocations,fake.frees);
        }
    }
    @Test void cancellationWakesNativeWaitAndCatalogPersistsOnlyFreshObservations()throws Exception {
        var fake=new Fake();fake.suppress=true;
        try(var provider=new WindowsWifiSignalProvider(fake,5000,0)){
            var work=CompletableFuture.supplyAsync(()->provider.scan(ADAPTER,""));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(!fake.started&&System.nanoTime()<until)Thread.sleep(5);
            provider.cancelScan();assertEquals(WifiScanSnapshot.Status.CANCELLED,work.get(1,TimeUnit.SECONDS).status());assertEquals(1,fake.unregistered);
            fake.suppress=false;fake.stamp=0;var first=provider.scan(ADAPTER,"HUTECH E1");
            var path=directory.resolve("observed.json");assertEquals(3,new WifiObservationCatalog(path).record(first));String saved=Files.readString(path);
            assertEquals(3,new WifiObservationCatalog(path).record(provider.scan(ADAPTER,"HUTECH E1")));assertEquals(saved,Files.readString(path));
            WifiObservationCatalog.export(directory.resolve("scan.json"),first);assertTrue(Files.readString(directory.resolve("scan.json")).contains("wifi_scan_diagnostic"));
        }
    }
    @Test void optInRealLaptopScan()throws Exception {
        if(!Boolean.getBoolean("nqb.native.scan"))return;
        try(var provider=new WindowsWifiSignalProvider()){
            var adapters=provider.scanAdapters();assertFalse(adapters.isEmpty());
            var adapter=adapters.stream().filter(WifiScanProvider.Adapter::connected).findFirst().orElse(adapters.getFirst());
            for(int i=0;i<2;i++){
                var scan=provider.scan(adapter.id(),System.getProperty("nqb.native.scan.ssid",""));
                System.out.println("NATIVE_MULTI_BSSID: status="+scan.status()+" fresh="+scan.freshCount()+" total="+scan.observations().size()+" duration_ms="+(scan.completedAtMillis()-scan.startedAtMillis())+" diagnostic="+scan.diagnostic());
                assertTrue(Set.of(WifiScanSnapshot.Status.COMPLETE,WifiScanSnapshot.Status.STALE,WifiScanSnapshot.Status.NO_RESULTS,WifiScanSnapshot.Status.PERMISSION_DENIED).contains(scan.status()));
                if(i==0)Thread.sleep(5100);
            }
        }
    }
}
