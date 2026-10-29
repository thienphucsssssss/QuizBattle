package vn.edu.hutech.nqb.client;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WindowsWifiSignalProviderTest {

    @Test
    void nativeProbeOnCurrentLaptopReadsWifiAndCleansUpHandles() {
        try (WifiSignalProvider provider = WifiSignalProvider.createDefault()) {
            WifiSignalProvider.Reading first = provider.readSignal();
            assertNotNull(first);
            System.out.println("NATIVE_WIFI_PROBE_1: status=" + first.status()
                + " adapterId=" + first.adapterId()
                + " adapterName=" + first.adapterName()
                + " ssid=" + first.ssid()
                + " bssid=" + first.bssid()
                + " band=" + first.band()
                + " channel=" + first.channel()
                + " freqKhz=" + first.frequencyKhz()
                + " rssiDbm=" + first.rssiDbm()
                + " quality=" + first.signalQuality()
                + " diagnostic=" + first.diagnostic());

            if (first.status() == WifiSignalProvider.Status.CONNECTED) {
                assertTrue(first.isConnected());
                assertFalse(first.adapterId().isEmpty());
                assertFalse(first.adapterName().isEmpty());
                assertTrue(first.bssid().matches("([0-9a-f]{2}:){5}[0-9a-f]{2}"));
                assertTrue(first.band().equals("2.4 GHz") || first.band().equals("5 GHz") || first.band().equals("6 GHz"));
                assertTrue(first.rssiDbm() <= -10 && first.rssiDbm() >= -110);
                assertTrue(first.matchesAnchor(first.bssid().toUpperCase(), first.band()));
                assertFalse(first.matchesAnchor("00:11:22:33:44:55", first.band()));
                assertFalse(first.matchesAnchor(first.bssid(), first.band().equals("5 GHz") ? "2.4 GHz" : "5 GHz"));
            }

            // Second reading exercises cached BSS band without rescanning BSS list, then release handle
            WifiSignalProvider.Reading second = provider.readSignal();
            assertEquals(first.status(), second.status());
            provider.releaseHandle();

            // Reading after releaseHandle() transparently reopens handle
            WifiSignalProvider.Reading third = provider.readSignal();
            assertEquals(first.status(), third.status());
        }
    }

    @Test
    void handlesPermissionDeniedWifiOffNoAdapterDisconnectedAndMemoryCleanup() {
        FakeWlanApi fake = new FakeWlanApi();
        try (WindowsWifiSignalProvider provider = new WindowsWifiSignalProvider(fake)) {
            // 1. Permission denied on current_connection
            fake.mode = FakeMode.PERMISSION_DENIED;
            WifiSignalProvider.Reading r1 = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.PERMISSION_DENIED, r1.status());
            assertEquals(
                "Bạn chưa cấp quyền đọc Wi-Fi. Vui lòng bật quyền truy cập vị trí trong Windows và thử lại.",
                r1.userMessage("e4:60:4d:b5:6e:48", "5 GHz", "Test")
            );
            assertEquals(fake.allocations.get(), fake.frees.get(), "All native buffers must be freed");

            // 2. Wi-Fi radio turned off
            fake.mode = FakeMode.RADIO_OFF;
            WifiSignalProvider.Reading r2 = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.WIFI_DISABLED, r2.status());
            assertTrue(r2.userMessage("", "", "").contains("Wi-Fi đang tắt"));
            assertEquals(fake.allocations.get(), fake.frees.get());

            // 3. No Wi-Fi adapter
            fake.mode = FakeMode.NO_ADAPTER;
            WifiSignalProvider.Reading r3 = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.NO_ADAPTER, r3.status());
            assertTrue(r3.userMessage("", "", "").contains("Không tìm thấy card Wi-Fi"));
            assertEquals(fake.allocations.get(), fake.frees.get());

            // 4. Disconnected adapter
            fake.mode = FakeMode.DISCONNECTED;
            WifiSignalProvider.Reading r4 = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.DISCONNECTED, r4.status());
            assertTrue(r4.userMessage("", "", "").contains("chưa kết nối Wi-Fi"));
            assertEquals(fake.allocations.get(), fake.frees.get());

            // 5. Connected 5 GHz reading + BSS cache verification
            fake.mode = FakeMode.CONNECTED_5GHZ;
            int bssCallsBefore = fake.bssCalls.get();
            WifiSignalProvider.Reading r5 = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.CONNECTED, r5.status());
            assertEquals("e4:60:4d:b5:6e:48", r5.bssid());
            assertEquals("5 GHz", r5.band());
            assertEquals(161, r5.channel());
            assertEquals(-54, r5.rssiDbm());
            assertEquals(1, fake.bssCalls.get() - bssCallsBefore, "BSS queried once for new BSSID");

            WifiSignalProvider.Reading r5Cached = provider.readSignal();
            assertEquals(WifiSignalProvider.Status.CONNECTED, r5Cached.status());
            assertEquals(1, fake.bssCalls.get() - bssCallsBefore, "Second read must not re-query BSS for same BSSID");
            assertEquals(fake.allocations.get(), fake.frees.get());
        }
        assertEquals(fake.opens.get(), fake.closes.get(), "All opened WLAN handles must be closed");
    }

    @Test
    void refusesCachedScanRssiAndAmbiguousChannelBand() {
        for (FakeMode mode : new FakeMode[]{FakeMode.NO_LIVE_RSSI, FakeMode.NO_BSS}) {
            FakeWlanApi fake = new FakeWlanApi();
            fake.mode = mode;
            try (WindowsWifiSignalProvider provider = new WindowsWifiSignalProvider(fake)) {
                assertEquals(WifiSignalProvider.Status.API_ERROR, provider.readSignal().status());
                assertEquals(fake.allocations.get(), fake.frees.get());
            }
            assertEquals(fake.opens.get(), fake.closes.get());
        }
    }

    private enum FakeMode {
        PERMISSION_DENIED,
        RADIO_OFF,
        NO_ADAPTER,
        DISCONNECTED,
        CONNECTED_5GHZ,
        NO_LIVE_RSSI,
        NO_BSS
    }

    private static final class FakeWlanApi implements WindowsWifiSignalProvider.WlanApi {
        @Override public int WlanScan(Pointer h,Pointer g,Pointer s,Pointer ie,Pointer r){return 50;}
        @Override public int WlanRegisterNotification(Pointer h,int source,boolean ignore,WindowsWifiSignalProvider.ScanCallback callback,Pointer context,Pointer reserved,IntByReference previous){return 50;}
        FakeMode mode = FakeMode.CONNECTED_5GHZ;
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger allocations = new AtomicInteger();
        final AtomicInteger frees = new AtomicInteger();
        final AtomicInteger bssCalls = new AtomicInteger();

        @Override
        public int WlanOpenHandle(int dwClientVersion, Pointer pReserved, IntByReference pdwNegotiatedVersion, PointerByReference phClientHandle) {
            opens.incrementAndGet();
            pdwNegotiatedVersion.setValue(2);
            Memory handle = new Memory(8);
            handle.setLong(0, 0x1234L);
            phClientHandle.setValue(handle);
            return WindowsWifiSignalProvider.ERROR_SUCCESS;
        }

        @Override
        public int WlanCloseHandle(Pointer hClientHandle, Pointer pReserved) {
            closes.incrementAndGet();
            return WindowsWifiSignalProvider.ERROR_SUCCESS;
        }

        @Override
        public int WlanEnumInterfaces(Pointer hClientHandle, Pointer pReserved, PointerByReference ppInterfaceList) {
            Memory mem = new Memory(8 + 532);
            mem.clear();
            allocations.incrementAndGet();
            if (mode == FakeMode.NO_ADAPTER) {
                mem.setInt(0, 0);
                ppInterfaceList.setValue(mem);
                return WindowsWifiSignalProvider.ERROR_SUCCESS;
            }
            mem.setInt(0, 1);
            byte[] guid = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
            mem.write(8, guid, 0, 16);
            char[] desc = "Intel Wi-Fi 6 AX201\0".toCharArray();
            mem.write(24, desc, 0, desc.length);
            int state = (mode == FakeMode.CONNECTED_5GHZ || mode == FakeMode.PERMISSION_DENIED
                || mode == FakeMode.NO_LIVE_RSSI || mode == FakeMode.NO_BSS)
                ? WindowsWifiSignalProvider.WLAN_INTERFACE_STATE_CONNECTED
                : 4;
            mem.setInt(8 + 528, state);
            ppInterfaceList.setValue(mem);
            return WindowsWifiSignalProvider.ERROR_SUCCESS;
        }

        @Override
        public int WlanQueryInterface(
            Pointer hClientHandle,
            Pointer pInterfaceGuid,
            int opCode,
            Pointer pReserved,
            IntByReference pdwDataSize,
            PointerByReference ppData,
            IntByReference pWlanOpcodeValueType
        ) {
            if (mode == FakeMode.PERMISSION_DENIED && opCode == WindowsWifiSignalProvider.OPCODE_CURRENT_CONNECTION) {
                return WindowsWifiSignalProvider.ERROR_ACCESS_DENIED;
            }
            if (opCode == WindowsWifiSignalProvider.OPCODE_RADIO_STATE) {
                Memory m = new Memory(16);
                m.clear();
                allocations.incrementAndGet();
                m.setInt(0, 1);
                int radio = mode == FakeMode.RADIO_OFF ? WindowsWifiSignalProvider.DOT11_RADIO_STATE_OFF : 1;
                m.setInt(8, radio);
                m.setInt(12, radio);
                pdwDataSize.setValue(16);
                ppData.setValue(m);
                return WindowsWifiSignalProvider.ERROR_SUCCESS;
            }
            if (opCode == WindowsWifiSignalProvider.OPCODE_CURRENT_CONNECTION) {
                Memory m = new Memory(604);
                m.clear();
                allocations.incrementAndGet();
                m.setInt(0, WindowsWifiSignalProvider.WLAN_INTERFACE_STATE_CONNECTED);
                byte[] ssid = "Thuong Thuong".getBytes(StandardCharsets.UTF_8);
                m.setInt(520, ssid.length);
                m.write(524, ssid, 0, ssid.length);
                byte[] bssid = new byte[]{(byte) 0xe4, 0x60, 0x4d, (byte) 0xb5, 0x6e, 0x48};
                m.write(560, bssid, 0, 6);
                m.setInt(576, 86);
                pdwDataSize.setValue(604);
                ppData.setValue(m);
                return WindowsWifiSignalProvider.ERROR_SUCCESS;
            }
            if (opCode == WindowsWifiSignalProvider.OPCODE_RSSI) {
                if (mode == FakeMode.NO_LIVE_RSSI) return WindowsWifiSignalProvider.ERROR_NOT_SUPPORTED;
                Memory m = new Memory(4);
                allocations.incrementAndGet();
                m.setInt(0, -54);
                pdwDataSize.setValue(4);
                ppData.setValue(m);
                return WindowsWifiSignalProvider.ERROR_SUCCESS;
            }
            if (opCode == WindowsWifiSignalProvider.OPCODE_CHANNEL_NUMBER) {
                Memory m = new Memory(4);
                allocations.incrementAndGet();
                m.setInt(0, 161);
                pdwDataSize.setValue(4);
                ppData.setValue(m);
                return WindowsWifiSignalProvider.ERROR_SUCCESS;
            }
            return WindowsWifiSignalProvider.ERROR_NOT_SUPPORTED;
        }

        @Override
        public int WlanGetNetworkBssList(
            Pointer hClientHandle,
            Pointer pInterfaceGuid,
            Pointer pDot11Ssid,
            int dot11BssType,
            boolean bSecurityEnabled,
            Pointer pReserved,
            PointerByReference ppWlanBssList
        ) {
            bssCalls.incrementAndGet();
            if (mode == FakeMode.NO_BSS) return WindowsWifiSignalProvider.ERROR_NOT_SUPPORTED;
            Memory m = new Memory(8 + 360);
            m.clear();
            allocations.incrementAndGet();
            m.setInt(0, 8 + 360);
            m.setInt(4, 1);
            byte[] bssid = new byte[]{(byte) 0xe4, 0x60, 0x4d, (byte) 0xb5, 0x6e, 0x48};
            m.write(8 + 40, bssid, 0, 6);
            m.setInt(8 + 56, -54);
            m.setInt(8 + 92, 5_805_000);
            ppWlanBssList.setValue(m);
            return WindowsWifiSignalProvider.ERROR_SUCCESS;
        }

        @Override
        public void WlanFreeMemory(Pointer pMemory) {
            if (pMemory != null) {
                frees.incrementAndGet();
            }
        }
    }
}
