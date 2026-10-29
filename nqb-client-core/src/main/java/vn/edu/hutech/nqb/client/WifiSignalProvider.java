package vn.edu.hutech.nqb.client;

import java.util.Locale;
import java.util.Objects;

/**
 * Abstraction for reading current Wi-Fi association and RSSI.
 * Separated from UI and network transport so tests can supply deterministic simulated readings.
 */
public interface WifiSignalProvider extends AutoCloseable {

    enum Status {
        CONNECTED,
        PERMISSION_DENIED,
        WIFI_DISABLED,
        NO_ADAPTER,
        DISCONNECTED,
        API_ERROR
    }

    record Reading(
        Status status,
        String adapterId,
        String adapterName,
        String ssid,
        String bssid,
        String band,
        int channel,
        int frequencyKhz,
        int rssiDbm,
        int signalQuality,
        long timestampEpochMs,
        String diagnostic
    ) {
        public Reading {
            Objects.requireNonNull(status, "status");
            adapterId = adapterId == null ? "" : adapterId.strip();
            adapterName = adapterName == null ? "" : adapterName.strip();
            ssid = ssid == null ? "" : ssid.strip();
            bssid = normalizeBssid(bssid);
            band = normalizeBand(band);
            diagnostic = diagnostic == null ? "" : diagnostic.strip();
        }

        public static Reading connected(
            String adapterId,
            String adapterName,
            String ssid,
            String bssid,
            String band,
            int channel,
            int frequencyKhz,
            int rssiDbm,
            int signalQuality,
            long timestampEpochMs
        ) {
            return new Reading(
                Status.CONNECTED,
                adapterId,
                adapterName,
                ssid,
                bssid,
                band,
                Math.max(0, channel),
                Math.max(0, frequencyKhz),
                rssiDbm,
                Math.max(0, Math.min(100, signalQuality)),
                timestampEpochMs,
                ""
            );
        }

        public static Reading failure(Status status, String diagnostic, long timestampEpochMs) {
            if (status == Status.CONNECTED) {
                throw new IllegalArgumentException("Use connected() for CONNECTED status");
            }
            return new Reading(status, "", "", "", "", "", 0, 0, 0, 0, timestampEpochMs, diagnostic);
        }

        public static Reading failure(Status status, String adapterId, String adapterName, String diagnostic, long timestampEpochMs) {
            if (status == Status.CONNECTED) {
                throw new IllegalArgumentException("Use connected() for CONNECTED status");
            }
            return new Reading(status, adapterId, adapterName, "", "", "", 0, 0, 0, 0, timestampEpochMs, diagnostic);
        }

        public boolean isConnected() {
            return status == Status.CONNECTED && !bssid.isEmpty() && !band.isEmpty() && rssiDbm <= -10 && rssiDbm >= -110;
        }

        public boolean matchesAnchor(String anchorBssid, String anchorBand) {
            if (!isConnected()) return false;
            String targetBssid = normalizeBssid(anchorBssid);
            String targetBand = normalizeBand(anchorBand);
            if (targetBssid.isEmpty() || targetBand.isEmpty()) return false;
            return bssid.equals(targetBssid) && band.equals(targetBand);
        }

        public String userMessage(String anchorBssid, String anchorBand, String anchorSsid) {
            return switch (status) {
                case PERMISSION_DENIED -> "Bạn chưa cấp quyền đọc Wi-Fi. Vui lòng bật quyền truy cập vị trí trong Windows và thử lại.";
                case WIFI_DISABLED -> "Wi-Fi đang tắt. Hãy bật Wi-Fi và kết nối vào điểm phát của phòng thi.";
                case NO_ADAPTER -> "Không tìm thấy card Wi-Fi hỗ trợ trên thiết bị này.";
                case DISCONNECTED -> "Máy chưa kết nối Wi-Fi. Hãy kết nối vào điểm phát của phòng thi rồi thử lại.";
                case API_ERROR -> "Không đọc được tín hiệu Wi-Fi từ thiết bị. Hãy kiểm tra card mạng và thử lại.";
                case CONNECTED -> {
                    if (!anchorBssid.isBlank() && !matchesAnchor(anchorBssid, anchorBand)) {
                        String expected = (anchorSsid == null || anchorSsid.isBlank() ? anchorBssid : anchorSsid + " (" + anchorBssid + ")")
                            + " · " + normalizeBand(anchorBand);
                        String current = (ssid.isBlank() ? bssid : ssid + " (" + bssid + ")") + " · " + band;
                        yield "Máy đang kết nối điểm phát " + current + ", không khớp mốc phòng thi " + expected + ".";
                    }
                    yield "Đang kết nối đúng điểm phát làm mốc.";
                }
            };
        }
    }

    Reading readSignal();

    default void releaseHandle() { }

    @Override
    void close();

    static WifiSignalProvider createDefault() {
        return new WindowsWifiSignalProvider();
    }

    static String normalizeBssid(String raw) {
        if (raw == null) return "";
        String cleaned = raw.strip().toLowerCase(Locale.ROOT).replace('-', ':');
        if (cleaned.matches("([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
            return cleaned;
        }
        return "";
    }

    static String normalizeBand(String raw) {
        return vn.edu.hutech.nqb.shared.ProximityRules.normalizeBand(raw);
    }

    static String bandFromFrequencyKhz(int frequencyKhz) {
        if (frequencyKhz >= 2_400_000 && frequencyKhz <= 2_500_000) return "2.4 GHz";
        if (frequencyKhz >= 4_900_000 && frequencyKhz <= 5_900_000) return "5 GHz";
        if (frequencyKhz >= 5_925_000 && frequencyKhz <= 7_125_000) return "6 GHz";
        return "";
    }

    static String bandFromChannel(int channel) {
        if (channel >= 1 && channel <= 14) return "2.4 GHz";
        if (channel >= 32 && channel <= 177) return "5 GHz";
        return "";
    }

    static int channelFromFrequencyKhz(int frequencyKhz) {
        if (frequencyKhz == 2_484_000) return 14;
        if (frequencyKhz >= 2_412_000 && frequencyKhz < 2_484_000) return (frequencyKhz - 2_407_000) / 5_000;
        if (frequencyKhz >= 5_000_000 && frequencyKhz <= 5_895_000) return (frequencyKhz - 5_000_000) / 5_000;
        if (frequencyKhz >= 5_955_000 && frequencyKhz <= 7_115_000) return (frequencyKhz - 5_950_000) / 5_000;
        return 0;
    }
}
