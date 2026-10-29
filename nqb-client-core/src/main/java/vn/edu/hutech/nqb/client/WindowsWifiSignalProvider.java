package vn.edu.hutech.nqb.client;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.util.concurrent.*;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Windows Native Wi-Fi (wlanapi.dll) signal provider using JNA.
 * Opens WLAN handle lazily on first readSignal() call and frees all native buffers deterministically.
 */
public final class WindowsWifiSignalProvider implements WifiSignalProvider, WifiScanProvider {

    static final int ERROR_SUCCESS = 0;
    static final int ERROR_ACCESS_DENIED = 5;
    static final int ERROR_INVALID_HANDLE = 6;
    static final int ERROR_NOT_SUPPORTED = 50;
    static final int ERROR_INVALID_PARAMETER = 87;
    static final int ERROR_SERVICE_NOT_ACTIVE = 1062;
    static final int ERROR_NOT_FOUND = 1168;

    static final int WLAN_INTERFACE_STATE_CONNECTED = 1;
    static final int DOT11_RADIO_STATE_OFF = 2;

    static final int OPCODE_RADIO_STATE = 4;
    static final int OPCODE_CURRENT_CONNECTION = 7;
    static final int OPCODE_CHANNEL_NUMBER = 8;
    static final int OPCODE_RSSI = 0x10000102;

    private static final int INTERFACE_INFO_SIZE = 532;
    private static final int BSS_ENTRY_SIZE = 360;

    interface ScanCallback extends StdCallLibrary.StdCallCallback { void invoke(Pointer notification,Pointer context); }
    interface WlanApi extends StdCallLibrary {
        int WlanScan(Pointer handle,Pointer guid,Pointer ssid,Pointer ieData,Pointer reserved);
        int WlanRegisterNotification(Pointer handle,int source,boolean ignoreDuplicate,ScanCallback callback,Pointer context,Pointer reserved,IntByReference previous);
        int WlanOpenHandle(
            int dwClientVersion,
            Pointer pReserved,
            IntByReference pdwNegotiatedVersion,
            PointerByReference phClientHandle
        );

        int WlanCloseHandle(Pointer hClientHandle, Pointer pReserved);

        int WlanEnumInterfaces(
            Pointer hClientHandle,
            Pointer pReserved,
            PointerByReference ppInterfaceList
        );

        int WlanQueryInterface(
            Pointer hClientHandle,
            Pointer pInterfaceGuid,
            int opCode,
            Pointer pReserved,
            IntByReference pdwDataSize,
            PointerByReference ppData,
            IntByReference pWlanOpcodeValueType
        );

        int WlanGetNetworkBssList(
            Pointer hClientHandle,
            Pointer pInterfaceGuid,
            Pointer pDot11Ssid,
            int dot11BssType,
            boolean bSecurityEnabled,
            Pointer pReserved,
            PointerByReference ppWlanBssList
        );

        void WlanFreeMemory(Pointer pMemory);
    }

    private record CachedBssInfo(String band, int frequencyKhz, int channel) { }

    private final WlanApi api;
    private final Map<String, CachedBssInfo> bssCache = new ConcurrentHashMap<>();
    private Pointer clientHandle;
    private boolean closed;
    private volatile CompletableFuture<Integer> scanCompletion;
    private final Map<String,Long> seenTimestamps=new LinkedHashMap<>();
    private long lastScanNano;
    private final long scanTimeoutMillis,scanCooldownMillis;

    public WindowsWifiSignalProvider() {
        this(loadWlanApi());
    }

    WindowsWifiSignalProvider(WlanApi api) {
        this(api,5000,5000);
    }
    WindowsWifiSignalProvider(WlanApi api,long timeoutMillis,long cooldownMillis) {
        this.api=api;this.scanTimeoutMillis=timeoutMillis;this.scanCooldownMillis=cooldownMillis;
    }

    @Override public synchronized List<WifiScanProvider.Adapter> scanAdapters() {
        if(closed||api==null)return List.of();
        int code=ensureHandle();if(code!=0)throw new IllegalStateException("Không mở được Wi-Fi Windows (code="+code+").");
        var ref=new PointerByReference();code=api.WlanEnumInterfaces(clientHandle,null,ref);var ptr=ref.getValue();
        try {
            if(code!=0)throw new IllegalStateException("Không đọc được card Wi-Fi (code="+code+").");
            if(ptr==null)return List.of();int count=ptr.getInt(0);
            if(count<0||count>64)throw new IllegalStateException("Danh sách card Wi-Fi không hợp lệ.");
            var result=new ArrayList<WifiScanProvider.Adapter>();
            for(int i=0;i<count;i++){long offset=8L+i*INTERFACE_INFO_SIZE;
                result.add(new WifiScanProvider.Adapter(formatGuid(ptr.getByteArray(offset,16)),readWideString(ptr,offset+16,256),ptr.getInt(offset+528)==1));}
            return List.copyOf(result);
        }finally{if(ptr!=null)api.WlanFreeMemory(ptr);}
    }
    @Override public void cancelScan(){var pending=scanCompletion;if(pending!=null)pending.cancel(false);}

    @Override public synchronized WifiScanSnapshot scan(String adapterId,String ssid) {
        var status=WifiScanSnapshot.Status.ERROR;
        long started=System.currentTimeMillis();
        if(closed)return WifiScanSnapshot.failure(WifiScanSnapshot.Status.CANCELLED,"Bộ quét đã đóng.");
        if(api==null)return WifiScanSnapshot.failure(WifiScanSnapshot.Status.UNSUPPORTED,"Quét nhiều BSSID yêu cầu Windows Native Wi-Fi.");
        if(lastScanNano!=0&&System.nanoTime()-lastScanNano<TimeUnit.MILLISECONDS.toNanos(scanCooldownMillis))
            return WifiScanSnapshot.failure(WifiScanSnapshot.Status.THROTTLED,"Chờ ít nhất 5 giây giữa hai yêu cầu quét.");
        WifiScanProvider.Adapter adapter;
        try{adapter=scanAdapters().stream().filter(a->a.id().equals(adapterId)).findFirst().orElse(null);}
        catch(RuntimeException error){return WifiScanSnapshot.failure(status,error.getMessage());}
        if(adapter==null)return WifiScanSnapshot.failure(WifiScanSnapshot.Status.NO_ADAPTER,"Chọn card Wi-Fi đang dùng để quét.");
        if(ssid==null)ssid="";
        if(ssid.getBytes(StandardCharsets.UTF_8).length>32)return WifiScanSnapshot.failure(status,"SSID dài tối đa 32 byte.");
        try(var guid=guidMemory(adapterId)) {
            String associated=associatedBssid(guid);
            var completion=new CompletableFuture<Integer>();scanCompletion=completion;
            ScanCallback callback=(data,context)->{
                if(data==null)return;
                if((data.getInt(0)&8)!=0&&formatGuid(data.getByteArray(8,16)).equals(adapterId)){
                    int event=data.getInt(4);if(event==7||event==8)completion.complete(event);
                }
            };
            int registered=api.WlanRegisterNotification(clientHandle,8,false,callback,null,null,new IntByReference());
            if(registered!=0){scanCompletion=null;return scanError(registered,"Đăng ký thông báo quét");}
            try {
                started=System.currentTimeMillis();lastScanNano=System.nanoTime();
                int code=api.WlanScan(clientHandle,guid,null,null,null);
                if(code!=0)return scanError(code,"Yêu cầu quét");
                try{if(completion.get(scanTimeoutMillis,TimeUnit.MILLISECONDS)!=7)return WifiScanSnapshot.failure(status,"Driver báo quét thất bại. Thử lại sau.");}
                catch(TimeoutException timeout){return WifiScanSnapshot.failure(WifiScanSnapshot.Status.TIMEOUT,"Chưa nhận được thông báo quét hoàn tất; không dùng cache làm mẫu mới.");}
                catch(CancellationException cancelled){return WifiScanSnapshot.failure(WifiScanSnapshot.Status.CANCELLED,"Đã hủy quét.");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return WifiScanSnapshot.failure(WifiScanSnapshot.Status.CANCELLED,"Đã dừng quét.");}
                catch(ExecutionException failed){return WifiScanSnapshot.failure(status,"Quét Wi-Fi thất bại.");}
                var ref=new PointerByReference();code=api.WlanGetNetworkBssList(clientHandle,guid,null,3,false,null,ref);var ptr=ref.getValue();
                try {
                    if(code!=0)return scanError(code,"Đọc kết quả quét");
                    long completed=System.currentTimeMillis();String after=associatedBssid(guid);
                    if(!associated.equals(after))associated=""; // Association changed during scan.
                    var all=parseScanEntries(ptr,adapterId,ssid,associated,started,completed,seenTimestamps);
                    int omitted=Math.max(0,all.size()-64);var observations=all.stream().limit(64).toList();
                    status=all.isEmpty()?WifiScanSnapshot.Status.NO_RESULTS:all.stream().anyMatch(WifiObservation::fresh)?WifiScanSnapshot.Status.COMPLETE:WifiScanSnapshot.Status.STALE;
                    return new WifiScanSnapshot(UUID.randomUUID().toString(),adapter.id(),adapter.name(),ssid,associated,started,completed,status,
                        status==WifiScanSnapshot.Status.STALE?"Driver chỉ trả dữ liệu cũ; chưa có mẫu mới.":status==WifiScanSnapshot.Status.NO_RESULTS?"Không thấy BSSID phù hợp SSID đã chọn.":"Đã nhận thông báo quét hoàn tất.",omitted,observations);
                }finally{if(ptr!=null)api.WlanFreeMemory(ptr);}
            }finally{
                scanCompletion=null;
                // Unregister outside the callback; callbacks never acquire this provider's lock.
                int unregister=api.WlanRegisterNotification(clientHandle,0,false,null,null,null,new IntByReference());
                if(unregister!=0)closeHandleInternal();
                java.lang.ref.Reference.reachabilityFence(callback);
            }
        }catch(RuntimeException error){return WifiScanSnapshot.failure(status,"Dữ liệu hoặc driver quét Wi-Fi không hợp lệ: "+error.getClass().getSimpleName());}
    }
    private WifiScanSnapshot scanError(int code,String operation){
        var status=switch(code){case ERROR_ACCESS_DENIED->WifiScanSnapshot.Status.PERMISSION_DENIED;case ERROR_SERVICE_NOT_ACTIVE,0x80342002->WifiScanSnapshot.Status.WIFI_DISABLED;default->WifiScanSnapshot.Status.ERROR;};
        return WifiScanSnapshot.failure(status,status==WifiScanSnapshot.Status.PERMISSION_DENIED?"Windows chặn quyền vị trí. Bật Location và quyền cho ứng dụng desktop, rồi thử lại.":operation+" thất bại (code="+Integer.toUnsignedString(code)+").");
    }
    private String associatedBssid(Pointer guid){
        var size=new IntByReference();var ref=new PointerByReference();int code=api.WlanQueryInterface(clientHandle,guid,OPCODE_CURRENT_CONNECTION,null,size,ref,new IntByReference());var ptr=ref.getValue();
        try{return code==0&&ptr!=null&&size.getValue()>=588&&ptr.getInt(0)==1?formatMac(ptr.getByteArray(560,6)):"";}
        finally{if(ptr!=null)api.WlanFreeMemory(ptr);}
    }
    private static Memory guidMemory(String id){
        var uuid=UUID.fromString(id);var mem=new Memory(16);
        long most=uuid.getMostSignificantBits(),least=uuid.getLeastSignificantBits();
        mem.setInt(0,(int)(most>>>32));mem.setShort(4,(short)(most>>>16));mem.setShort(6,(short)most);
        for(int i=0;i<8;i++)mem.setByte(8+i,(byte)(least>>>(56-i*8)));return mem;
    }
    static List<WifiObservation> parseScanEntries(Pointer ptr,String adapter,String filter,String associated,long started,long completed,Map<String,Long> seen){
        if(ptr==null)throw new IllegalArgumentException("Missing BSS buffer");
        int bytes=ptr.getInt(0),count=ptr.getInt(4);
        if(bytes<8||bytes>16*1024*1024||count<0||count>4096||8L+count*BSS_ENTRY_SIZE>bytes)throw new IllegalArgumentException("Invalid BSS bounds");
        var unique=new LinkedHashMap<String,WifiObservation>();
        for(int i=0;i<count;i++){
            long offset=8L+i*BSS_ENTRY_SIZE;int length=ptr.getInt(offset);if(length<0||length>32)continue;
            String ssid=new String(ptr.getByteArray(offset+4,length),StandardCharsets.UTF_8);if(!filter.isEmpty()&&!filter.equals(ssid))continue;
            String bssid=formatMac(ptr.getByteArray(offset+40,6));int rssi=ptr.getInt(offset+56),frequency=ptr.getInt(offset+92);
            if(bssid.equals("00:00:00:00:00:00")||bssid.equals("ff:ff:ff:ff:ff:ff")||rssi< -110||rssi> -10||WifiSignalProvider.bandFromFrequencyKhz(frequency).isEmpty())continue;
            long stamp=ptr.getLong(offset+80),millis=stamp/10000L-11644473600000L;
            String key=adapter+"|"+bssid+"|"+frequency;
            boolean valid=stamp>0&&millis<=completed;
            boolean fresh=valid&&millis>=started&&stamp>seen.getOrDefault(key,0L);
            long age=valid?completed-millis:-1;
            var observation=new WifiObservation(ssid,bssid,rssi,frequency,stamp,age,fresh,bssid.equals(associated));
            var previous=unique.get(key);if(previous==null||stamp>previous.hostTimestamp100ns())unique.put(key,observation);
        }
        // Update after deduplication, so duplicate BSS records cannot consume a fresh timestamp.
        unique.forEach((key,value)->{if(value.fresh())seen.put(key,value.hostTimestamp100ns());});
        while(seen.size()>4096)seen.remove(seen.keySet().iterator().next());
        return unique.values().stream().sorted(Comparator.comparing(WifiObservation::fresh).reversed().thenComparing(Comparator.comparingInt(WifiObservation::rssiDbm).reversed())).toList();
    }

    private static WlanApi loadWlanApi() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return null;
        }
        try {
            return Native.load("wlanapi", WlanApi.class);
        } catch (Throwable error) {
            return null;
        }
    }

    @Override
    public synchronized Reading readSignal() {
        long now = System.currentTimeMillis();
        if (closed) {
            return Reading.failure(Status.API_ERROR, "Bộ đọc Wi-Fi đã đóng.", now);
        }
        if (api == null) {
            return Reading.failure(Status.NO_ADAPTER, "Thiết bị không hỗ trợ thư viện Wi-Fi Windows.", now);
        }
        int openCode = ensureHandle();
        if (openCode != ERROR_SUCCESS) {
            return mapErrorCode(openCode, "", "", "WlanOpenHandle", now);
        }
        Reading reading = queryInterfaces(now);
        if (reading.status() == Status.API_ERROR && reading.diagnostic().contains("code=" + ERROR_INVALID_HANDLE)) {
            closeHandleInternal();
            openCode = ensureHandle();
            if (openCode != ERROR_SUCCESS) {
                return mapErrorCode(openCode, "", "", "WlanOpenHandle", now);
            }
            reading = queryInterfaces(now);
        }
        return reading;
    }

    private int ensureHandle() {
        if (clientHandle != null) {
            return ERROR_SUCCESS;
        }
        IntByReference negotiated = new IntByReference();
        PointerByReference handleRef = new PointerByReference();
        int code = api.WlanOpenHandle(2, null, negotiated, handleRef);
        if (code == ERROR_SUCCESS && handleRef.getValue() != null) {
            clientHandle = handleRef.getValue();
            return ERROR_SUCCESS;
        }
        return code == ERROR_SUCCESS ? ERROR_INVALID_HANDLE : code;
    }

    private Reading queryInterfaces(long now) {
        PointerByReference listRef = new PointerByReference();
        int enumCode = api.WlanEnumInterfaces(clientHandle, null, listRef);
        Pointer listPtr = listRef.getValue();
        try {
            if (enumCode != ERROR_SUCCESS) {
                return mapErrorCode(enumCode, "", "", "WlanEnumInterfaces", now);
            }
            if (listPtr == null) {
                return Reading.failure(Status.NO_ADAPTER, "Danh sách card Wi-Fi trống.", now);
            }
            int count = listPtr.getInt(0);
            if (count <= 0 || count > 64) {
                return Reading.failure(Status.NO_ADAPTER, "Không có card Wi-Fi trên thiết bị.", now);
            }

            String firstAdapterId = "";
            String firstAdapterName = "";
            boolean anyRadioOff = false;
            boolean anyPermissionDenied = false;

            for (int i = 0; i < count; i++) {
                long offset = 8L + (long) i * INTERFACE_INFO_SIZE;
                byte[] guidBytes = listPtr.getByteArray(offset, 16);
                Memory guidMem = new Memory(16);
                guidMem.write(0, guidBytes, 0, 16);
                String adapterId = formatGuid(guidBytes);
                String adapterName = readWideString(listPtr, offset + 16, 256);
                int state = listPtr.getInt(offset + 528);

                if (i == 0) {
                    firstAdapterId = adapterId;
                    firstAdapterName = adapterName;
                }

                if (state == WLAN_INTERFACE_STATE_CONNECTED) {
                    Reading connected = readConnectedInterface(guidMem, adapterId, adapterName, now);
                    if (connected.status() == Status.CONNECTED || connected.status() == Status.PERMISSION_DENIED) {
                        return connected;
                    }
                    return connected;
                } else {
                    int connProbe = probePermissionOrRadio(guidMem);
                    if (connProbe == ERROR_ACCESS_DENIED) {
                        anyPermissionDenied = true;
                    } else if (connProbe == -DOT11_RADIO_STATE_OFF) {
                        anyRadioOff = true;
                    }
                }
            }

            if (anyPermissionDenied) {
                return Reading.failure(Status.PERMISSION_DENIED, firstAdapterId, firstAdapterName, "Windows chặn quyền truy cập vị trí khi đọc trạng thái Wi-Fi.", now);
            }
            if (anyRadioOff) {
                return Reading.failure(Status.WIFI_DISABLED, firstAdapterId, firstAdapterName, "Công tắc Wi-Fi trên thiết bị đang tắt.", now);
            }
            return Reading.failure(Status.DISCONNECTED, firstAdapterId, firstAdapterName, "Card Wi-Fi đang bật nhưng chưa kết nối điểm phát.", now);
        } finally {
            if (listPtr != null) {
                api.WlanFreeMemory(listPtr);
            }
        }
    }

    private int probePermissionOrRadio(Memory guidMem) {
        IntByReference sizeRef = new IntByReference();
        PointerByReference dataRef = new PointerByReference();
        IntByReference typeRef = new IntByReference();
        int radioCode = api.WlanQueryInterface(clientHandle, guidMem, OPCODE_RADIO_STATE, null, sizeRef, dataRef, typeRef);
        Pointer dataPtr = dataRef.getValue();
        try {
            if (radioCode == ERROR_ACCESS_DENIED) {
                return ERROR_ACCESS_DENIED;
            }
            if (radioCode == ERROR_SUCCESS && dataPtr != null && sizeRef.getValue() >= 16) {
                int phys = dataPtr.getInt(0);
                if (phys > 0) {
                    boolean allOff = true;
                    int maxPhys = Math.min(phys, 64);
                    for (int p = 0; p < maxPhys; p++) {
                        long phyOffset = 4L + (long) p * 12L;
                        if (phyOffset + 12 <= sizeRef.getValue()) {
                            int sw = dataPtr.getInt(phyOffset + 4);
                            int hw = dataPtr.getInt(phyOffset + 8);
                            if (sw != DOT11_RADIO_STATE_OFF && hw != DOT11_RADIO_STATE_OFF) {
                                allOff = false;
                                break;
                            }
                        }
                    }
                    if (allOff) {
                        return -DOT11_RADIO_STATE_OFF;
                    }
                }
            }
        } finally {
            if (dataPtr != null) {
                api.WlanFreeMemory(dataPtr);
            }
        }
        return ERROR_SUCCESS;
    }

    private Reading readConnectedInterface(Memory guidMem, String adapterId, String adapterName, long now) {
        IntByReference sizeRef = new IntByReference();
        PointerByReference dataRef = new PointerByReference();
        IntByReference typeRef = new IntByReference();
        int code = api.WlanQueryInterface(clientHandle, guidMem, OPCODE_CURRENT_CONNECTION, null, sizeRef, dataRef, typeRef);
        Pointer dataPtr = dataRef.getValue();
        try {
            if (code != ERROR_SUCCESS) {
                return mapErrorCode(code, adapterId, adapterName, "WlanQueryInterface(current_connection)", now);
            }
            if (dataPtr == null || sizeRef.getValue() < 588) {
                return Reading.failure(Status.API_ERROR, adapterId, adapterName, "Dữ liệu kết nối Wi-Fi không đầy đủ.", now);
            }
            int isState = dataPtr.getInt(0);
            if (isState != WLAN_INTERFACE_STATE_CONNECTED) {
                return Reading.failure(Status.DISCONNECTED, adapterId, adapterName, "Card Wi-Fi chưa ở trạng thái kết nối.", now);
            }
            long assocOffset = 520L;
            int ssidLen = Math.max(0, Math.min(32, dataPtr.getInt(assocOffset)));
            byte[] ssidBytes = ssidLen > 0 ? dataPtr.getByteArray(assocOffset + 4, ssidLen) : new byte[0];
            String ssid = new String(ssidBytes, StandardCharsets.UTF_8).strip();
            byte[] bssidBytes = dataPtr.getByteArray(assocOffset + 40, 6);
            String bssid = formatMac(bssidBytes);
            int signalQuality = Math.max(0, Math.min(100, dataPtr.getInt(assocOffset + 56)));

            if (bssid.isEmpty() || bssid.equals("00:00:00:00:00:00")) {
                return Reading.failure(Status.PERMISSION_DENIED, adapterId, adapterName, "Windows ẩn địa chỉ điểm phát do chưa cấp quyền vị trí.", now);
            }

            Integer rssiObj = queryIntOpcode(guidMem, OPCODE_RSSI);
            Integer channelObj = queryIntOpcode(guidMem, OPCODE_CHANNEL_NUMBER);
            int channel = channelObj != null && channelObj > 0 ? channelObj : 0;

            String cacheKey = adapterId + "|" + bssid;
            CachedBssInfo bssInfo = bssCache.get(cacheKey);
            Integer bssRssi = null;

            if (bssInfo == null || (channel > 0 && bssInfo.channel() != channel)) {
                BssLookup lookup = queryBssForBssid(guidMem, bssid);
                if (lookup.errorCode == ERROR_ACCESS_DENIED && rssiObj == null) {
                    return Reading.failure(Status.PERMISSION_DENIED, adapterId, adapterName, "Windows yêu cầu quyền vị trí để đọc tín hiệu điểm phát.", now);
                }
                if (lookup.info != null) {
                    bssInfo = lookup.info;
                    bssCache.put(cacheKey, bssInfo);
                }
                bssRssi = lookup.rssiDbm;
            }

            int rssiDbm;
            if (rssiObj != null && rssiObj <= -10 && rssiObj >= -110) {
                rssiDbm = rssiObj;
            } else {
                // BSS lists may contain old scan results: they are metadata, not a live RSSI sample.
                return Reading.failure(Status.API_ERROR, adapterId, adapterName, "Driver Wi-Fi không trả về chỉ số RSSI hợp lệ.", now);
            }

            int frequencyKhz = bssInfo != null ? bssInfo.frequencyKhz() : 0;
            if (channel <= 0 && bssInfo != null) {
                channel = bssInfo.channel();
            }
            String band = bssInfo != null && !bssInfo.band().isEmpty()
                ? bssInfo.band()
                : ""; // Channel numbers overlap between 2.4/5/6 GHz; never guess the band.

            if (band.isEmpty()) {
                return Reading.failure(Status.API_ERROR, adapterId, adapterName, "Không xác định được băng tần của điểm phát.", now);
            }

            return Reading.connected(adapterId, adapterName, ssid, bssid, band, channel, frequencyKhz, rssiDbm, signalQuality, now);
        } finally {
            if (dataPtr != null) {
                api.WlanFreeMemory(dataPtr);
            }
        }
    }

    private Integer queryIntOpcode(Memory guidMem, int opCode) {
        IntByReference sizeRef = new IntByReference();
        PointerByReference dataRef = new PointerByReference();
        IntByReference typeRef = new IntByReference();
        int code = api.WlanQueryInterface(clientHandle, guidMem, opCode, null, sizeRef, dataRef, typeRef);
        Pointer dataPtr = dataRef.getValue();
        try {
            if (code == ERROR_SUCCESS && dataPtr != null && sizeRef.getValue() >= 4) {
                return dataPtr.getInt(0);
            }
            return null;
        } finally {
            if (dataPtr != null) {
                api.WlanFreeMemory(dataPtr);
            }
        }
    }

    private record BssLookup(CachedBssInfo info, Integer rssiDbm, int errorCode) { }

    private BssLookup queryBssForBssid(Memory guidMem, String targetBssid) {
        PointerByReference bssRef = new PointerByReference();
        int code = api.WlanGetNetworkBssList(clientHandle, guidMem, null, 3, false, null, bssRef);
        Pointer bssPtr = bssRef.getValue();
        try {
            if (code != ERROR_SUCCESS || bssPtr == null) {
                return new BssLookup(null, null, code);
            }
            int totalSize = bssPtr.getInt(0);
            int count = bssPtr.getInt(4);
            if(totalSize<8||totalSize>16*1024*1024||count<0||count>4096||8L+count*BSS_ENTRY_SIZE>totalSize)return new BssLookup(null,null,ERROR_INVALID_PARAMETER);
            for (int i = 0; i < count; i++) {
                long entryOffset = 8L + (long) i * BSS_ENTRY_SIZE;
                if (entryOffset + BSS_ENTRY_SIZE > totalSize && totalSize > 0) {
                    break;
                }
                String entryBssid = formatMac(bssPtr.getByteArray(entryOffset + 40, 6));
                if (targetBssid.equals(entryBssid)) {
                    int rssi = bssPtr.getInt(entryOffset + 56);
                    int freqKhz = bssPtr.getInt(entryOffset + 92);
                    String band = WifiSignalProvider.bandFromFrequencyKhz(freqKhz);
                    int ch = WifiSignalProvider.channelFromFrequencyKhz(freqKhz);
                    return new BssLookup(new CachedBssInfo(band, freqKhz, ch), rssi, ERROR_SUCCESS);
                }
            }
            return new BssLookup(null, null, ERROR_NOT_FOUND);
        } finally {
            if (bssPtr != null) {
                api.WlanFreeMemory(bssPtr);
            }
        }
    }

    private Reading mapErrorCode(int code, String adapterId, String adapterName, String op, long now) {
        return switch (code) {
            case ERROR_ACCESS_DENIED -> Reading.failure(
                Status.PERMISSION_DENIED,
                adapterId,
                adapterName,
                "Windows từ chối quyền đọc vị trí Wi-Fi (" + op + ").",
                now
            );
            case ERROR_SERVICE_NOT_ACTIVE -> Reading.failure(
                Status.WIFI_DISABLED,
                adapterId,
                adapterName,
                "Dịch vụ WLAN AutoConfig đang tắt.",
                now
            );
            case ERROR_NOT_FOUND -> Reading.failure(
                Status.DISCONNECTED,
                adapterId,
                adapterName,
                "Không tìm thấy kết nối Wi-Fi đang hoạt động.",
                now
            );
            default -> Reading.failure(
                Status.API_ERROR,
                adapterId,
                adapterName,
                "Lỗi đọc trạng thái Wi-Fi (" + op + ", code=" + code + ").",
                now
            );
        };
    }

    static String formatMac(byte[] bytes) {
        if (bytes == null || bytes.length != 6) return "";
        return String.format(
            Locale.ROOT,
            "%02x:%02x:%02x:%02x:%02x:%02x",
            bytes[0] & 0xff,
            bytes[1] & 0xff,
            bytes[2] & 0xff,
            bytes[3] & 0xff,
            bytes[4] & 0xff,
            bytes[5] & 0xff
        );
    }

    static String formatGuid(byte[] b) {
        if (b == null || b.length != 16) return "";
        return String.format(
            Locale.ROOT,
            "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
            b[3] & 0xff, b[2] & 0xff, b[1] & 0xff, b[0] & 0xff,
            b[5] & 0xff, b[4] & 0xff,
            b[7] & 0xff, b[6] & 0xff,
            b[8] & 0xff, b[9] & 0xff,
            b[10] & 0xff, b[11] & 0xff, b[12] & 0xff, b[13] & 0xff, b[14] & 0xff, b[15] & 0xff
        );
    }

    private static String readWideString(Pointer ptr, long offset, int maxChars) {
        char[] chars = ptr.getCharArray(offset, maxChars);
        int len = 0;
        while (len < chars.length && chars[len] != 0) {
            len++;
        }
        return new String(chars, 0, len).strip();
    }

    @Override
    public synchronized void releaseHandle() {
        closeHandleInternal();
    }

    private void closeHandleInternal() {
        bssCache.clear();
        if (clientHandle != null && api != null) {
            try {
                api.WlanCloseHandle(clientHandle, null);
            } catch (Throwable ignored) {
            }
            clientHandle = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        bssCache.clear();
        closeHandleInternal();
    }
}
