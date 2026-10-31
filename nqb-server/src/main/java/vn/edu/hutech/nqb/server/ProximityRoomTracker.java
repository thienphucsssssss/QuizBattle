package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.ProximityRules;
import vn.edu.hutech.nqb.shared.AutomaticApDistance;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static vn.edu.hutech.nqb.server.Wire.*;

/**
 * Owns room-level Wi-Fi proximity configuration, per-player calibration & 5-sample median filtering,
 * threshold alert state machines, Ready eligibility checks, and periodic snapshot building.
 * Accessed exclusively on the single game-worker thread in QuizServer.
 */
final class ProximityRoomTracker {
    final AccessPointTracker accessPoints = new AccessPointTracker();

    void configureAccessPoints(JsonNode payload) {
        accessPoints.configure(payload);
        players.values().forEach(p -> p.apTrack.reset());
    }

    record CandidateAnchor(String ssid, String bssid, String band, String reportedByNickname, String updatedAtUtc) { }

    static final class PlayerProximity {
        final AutomaticApDistance automaticDistance=new AutomaticApDistance();
        final AccessPointTracker.Track apTrack = new AccessPointTracker.Track();
        final String playerId;
        String nickname;
        String avatarId;
        boolean connected = true;

        boolean calibrated;
        int calibratedEpoch;
        String calibratedAdapterId = "";
        String calibratedAdapterName = "";
        String calibratedBssid = "";
        String calibratedBand = "";
        double rssi1m;
        double rssi5m;
        double mad1m;
        double mad5m;
        double pathLossN;

        long lastReportSeq;
        long lastReportReceivedNano;
        long lastValidReportNano;
        String lastDeviceStatus = "NONE";
        String currentAdapterId = "";
        String currentAdapterName = "";
        String currentSsid = "";
        String currentBssid = "";
        String currentBand = "";
        int currentChannel;
        int currentSignalQuality;
        int lastRawRssiDbm;
        final Deque<Integer> rssiWindow = new ArrayDeque<>(ProximityRules.MEDIAN_WINDOW_SIZE);
        Double currentMedianRssi;
        Double currentMadDb;
        Double currentDistanceMeters;
        String updatedAtUtc = "";

        int consecutiveOverThreshold;
        int consecutiveRecovery;
        boolean overThresholdAlertActive;
        boolean dataLostAlertActive;
        boolean anchorMismatchAlertActive;

        long missingDataSinceNano;
        long accumulatedMissingDataMs;
        Double minDistanceMeters;
        Double maxDistanceMeters;
        int overThresholdCount;
        int alertCount;

        PlayerProximity(String playerId, String nickname, String avatarId) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.avatarId = avatarId;
        }

        void clearCalibrationAndMeasurements() {
            calibrated = false;
            calibratedEpoch = 0;
            calibratedAdapterId = "";
            calibratedAdapterName = "";
            calibratedBssid = "";
            calibratedBand = "";
            rssi1m = 0;
            rssi5m = 0;
            mad1m = 0;
            mad5m = 0;
            pathLossN = 0;
            resetMeasurementsOnly();
        }

        void resetMeasurementsOnly() {
            lastValidReportNano = 0L;
            rssiWindow.clear();
            currentMedianRssi = null;
            currentMadDb = null;
            currentDistanceMeters = null;
            consecutiveOverThreshold = 0;
            consecutiveRecovery = 0;
            overThresholdAlertActive = false;
            dataLostAlertActive = false;
            anchorMismatchAlertActive = false;
        }

        void resetMatchCounters(long nowNano, boolean hasValidDataNow) {
            minDistanceMeters = currentDistanceMeters;
            maxDistanceMeters = currentDistanceMeters;
            overThresholdCount = overThresholdAlertActive ? 1 : 0;
            alertCount = overThresholdAlertActive ? 1 : 0;
            accumulatedMissingDataMs = 0L;
            missingDataSinceNano = hasValidDataNow ? 0L : nowNano;
            dataLostAlertActive = !hasValidDataNow;
        }
    }

    private final Map<String, PlayerProximity> players = new LinkedHashMap<>();
    private final Map<String, CandidateAnchor> candidateAnchors = new LinkedHashMap<>();
    private final List<ProximityStore.AlertEventRow> pendingAlerts = new ArrayList<>();

    private boolean enabled;
    private boolean calibrationRequired;
    private int configEpoch = 1;
    private String anchorSsid = "";
    private String anchorBssid = "";
    private String anchorBand = "";
    private int thresholdMeters = ProximityRules.DEFAULT_THRESHOLD_METERS;
    private String recalibratedAtUtc = "";
    private boolean requireFreshCalibration;
    private String persistenceWarning = "";

    private long staleTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(ProximityRules.STALE_TIMEOUT_MS);
    private long minReportIntervalNanos = TimeUnit.MILLISECONDS.toNanos(ProximityRules.REPORT_INTERVAL_MS);
    private long persistIntervalNanos = TimeUnit.MILLISECONDS.toNanos(ProximityRules.SUMMARY_PERSIST_INTERVAL_MS);
    private long lastPersistNano;

    void setTimingsForTest(long staleTimeoutMs, long minReportIntervalMs, long persistIntervalMs) {
        this.staleTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(50L, staleTimeoutMs));
        this.minReportIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, minReportIntervalMs));
        this.persistIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(50L, persistIntervalMs));
    }

    boolean isEnabled() {
        return enabled;
    }

    int configEpoch() {
        return configEpoch;
    }

    String anchorSsid() {
        return anchorSsid;
    }

    String anchorBssid() {
        return anchorBssid;
    }

    String anchorBand() {
        return anchorBand;
    }

    int thresholdMeters() {
        return thresholdMeters;
    }

    void setPersistenceWarning(String warning) {
        this.persistenceWarning = warning == null ? "" : warning;
    }

    void resetAll() {
        players.clear();
        candidateAnchors.clear();
        pendingAlerts.clear();
        enabled = false;
        calibrationRequired = false;
        configEpoch = 1;
        anchorSsid = "";
        anchorBssid = "";
        anchorBand = "";
        thresholdMeters = ProximityRules.DEFAULT_THRESHOLD_METERS;
        recalibratedAtUtc = "";
        requireFreshCalibration = false;
        persistenceWarning = "";
        lastPersistNano = 0L;
    }

    void resetForNewMatchInSameRoom() {
        pendingAlerts.clear();
        lastPersistNano = 0L;
        for (PlayerProximity p : players.values()) {
            p.consecutiveOverThreshold = 0;
            p.consecutiveRecovery = 0;
            p.overThresholdAlertActive = false;
            p.dataLostAlertActive = false;
            p.anchorMismatchAlertActive = false;
            p.missingDataSinceNano = 0L;
            p.accumulatedMissingDataMs = 0L;
            p.minDistanceMeters = null;
            p.maxDistanceMeters = null;
            p.overThresholdCount = 0;
            p.alertCount = 0;
        }
    }

    void onPlayerJoin(String playerId, String nickname, String avatarId) {
        players.put(playerId, new PlayerProximity(playerId, nickname, avatarId));
    }
    void onPlayerResume(String playerId,String nickname,String avatarId) {
        var p=players.get(playerId);
        if(p==null){onPlayerJoin(playerId,nickname,avatarId);return;}
        p.connected=true;p.apTrack.breakContinuity();p.apTrack.deviceStatus="NONE";
        p.lastValidReportNano=0;p.lastDeviceStatus="NONE";p.rssiWindow.clear();
        p.currentDistanceMeters=null;p.currentMedianRssi=null;p.currentMadDb=null;
        // Old measurements remain stale until a new authenticated report arrives.
    }

    void onPlayerUpdateAvatar(String playerId, String avatarId) {
        PlayerProximity p = players.get(playerId);
        if (p != null) {
            p.avatarId = avatarId;
        }
    }

    void onPlayerDisconnect(String playerId, boolean running, String sessionId, long nowNano) {
        onPlayerDisconnect(playerId,running,sessionId,nowNano,false);
    }
    void onPlayerDisconnect(String playerId, boolean running, String sessionId, long nowNano, boolean keepSeat) {
        PlayerProximity p = players.get(playerId);
        if (p == null) return;
        p.connected = false;
        p.automaticDistance.unavailable("DISCONNECTED",nowNano);
        p.apTrack.breakContinuity();
        p.apTrack.deviceStatus="DISCONNECTED";
        p.lastValidReportNano=0;p.lastDeviceStatus="NONE";
        p.currentDistanceMeters=null;p.currentMedianRssi=null;p.currentMadDb=null;
        if (!running && !keepSeat) {
            players.remove(playerId);
        } else {
            markMissingData(p, running, sessionId, "Người chơi mất kết nối với phòng thi.", nowNano);
        }
    }

    /**
     * Applies proximity configuration from ROOM_CONFIGURE or PROXIMITY_CONFIGURE.
     * Returns true if ready states in the waiting room must be cleared.
     */
    boolean configure(JsonNode payload, boolean isExplicitProximityConfigure) {
        if (!isExplicitProximityConfigure && !payload.has("proximity_enabled") && !payload.has("anchor_bssid") && !payload.has("threshold_meters") && !payload.has("calibration_required")) {
            return false;
        }
        boolean nextEnabled = payload.has("enabled")
            ? bool(payload, "enabled", enabled)
            : bool(payload, "proximity_enabled", enabled);
        int nextThreshold = integer(
            payload,
            "threshold_meters",
            thresholdMeters,
            ProximityRules.MIN_THRESHOLD_METERS,
            ProximityRules.MAX_THRESHOLD_METERS
        );
        String rawSsid = optional(payload, "anchor_ssid", anchorSsid, 64);
        String rawBssid = optional(payload, "anchor_bssid", anchorBssid, 32);
        String rawBand = optional(payload, "anchor_band", anchorBand, 20);
        if (payload.hasNonNull("from_player_id")) {
            PlayerProximity source = players.get(optional(payload, "from_player_id", "", 80));
            if (source == null || source.currentBssid.isEmpty() || source.currentBand.isEmpty()) {
                throw fail("INVALID_INPUT", "Người chơi được chọn chưa gửi thông tin điểm phát Wi-Fi hợp lệ.");
            }
            rawSsid = source.currentSsid;
            rawBssid = source.currentBssid;
            rawBand = source.currentBand;
        }
        boolean recalibrateAll = bool(payload, "recalibrate_all", false);
        boolean nextRequired=bool(payload,"calibration_required",calibrationRequired);
        boolean requirementChanged=nextRequired!=calibrationRequired;

        String normalizedBssid = rawBssid.isBlank() ? "" : ProximityRules.normalizeBssid(rawBssid);
        if (!rawBssid.isBlank() && normalizedBssid.isEmpty()) {
            throw fail("INVALID_INPUT", "Địa chỉ BSSID điểm phát không hợp lệ (ví dụ: e4:60:4d:b5:6e:48).");
        }
        String normalizedBand = rawBand.isBlank() ? "" : ProximityRules.normalizeBand(rawBand);
        if (!rawBand.isBlank() && normalizedBand.isEmpty()) {
            throw fail("INVALID_INPUT", "Băng tần điểm phát phải là 2.4 GHz, 5 GHz hoặc 6 GHz.");
        }
        if (nextEnabled && isExplicitProximityConfigure && !recalibrateAll && (normalizedBssid.isEmpty() || normalizedBand.isEmpty())) {
            throw fail("INVALID_INPUT", "Hãy chọn điểm phát làm mốc gồm đầy đủ BSSID và băng tần.");
        }

        boolean anchorChanged = !Objects.equals(this.anchorBssid, normalizedBssid)
            || !Objects.equals(this.anchorBand, normalizedBand);
        boolean enabledTurnedOn = !this.enabled && nextEnabled;

        this.enabled = nextEnabled;
        this.calibrationRequired=nextRequired;
        this.thresholdMeters = nextThreshold;
        this.anchorSsid = rawSsid;
        this.anchorBssid = normalizedBssid;
        this.anchorBand = normalizedBand;

        if (anchorChanged || enabledTurnedOn || recalibrateAll || requirementChanged) {
            this.configEpoch++;
            if (recalibrateAll) {
                this.recalibratedAtUtc = Instant.now().toString();
                this.requireFreshCalibration = true;
            } else if (anchorChanged) {
                this.requireFreshCalibration = false;
            }
            for (PlayerProximity p : players.values()) {
                p.clearCalibrationAndMeasurements();
            }
            return true;
        }
        return false;
    }

    ObjectNode handleCalibration(String playerId, JsonNode payload, long nowNano) {
        if (!enabled) {
            throw fail("PROXIMITY_DISABLED", "Phòng thi hiện không bật giám sát khoảng cách.");
        }
        if (anchorBssid.isEmpty() || anchorBand.isEmpty()) {
            throw fail("PROXIMITY_NOT_CONFIGURED", "Chủ phòng chưa chọn điểm phát Wi-Fi làm mốc.");
        }
        PlayerProximity p = players.get(playerId);
        if (p == null) {
            throw fail("FORBIDDEN", "Bạn chưa tham gia phòng.");
        }
        int epoch = integer(payload, "config_epoch", 0, 1, Integer.MAX_VALUE);
        if (epoch != configEpoch) {
            throw fail("STALE_CONFIG_EPOCH", "Cấu hình điểm phát của phòng đã thay đổi. Hãy hiệu chuẩn lại theo mốc mới.");
        }
        String adapterId = text(payload, "adapter_id", 120);
        String adapterName = optional(payload, "adapter_name", "", 160);
        String bssid = ProximityRules.normalizeBssid(text(payload, "bssid", 32));
        String band = ProximityRules.normalizeBand(text(payload, "band", 20));
        if (!anchorBssid.equals(bssid) || !anchorBand.equals(band)) {
            throw fail("WRONG_ANCHOR", "Điểm phát hoặc băng tần hiệu chuẩn không khớp với mốc của phòng thi.");
        }

        boolean reused = bool(payload, "reused_local_profile", false);
        if (reused) {
            if (requireFreshCalibration) {
                throw fail("RECALIBRATION_REQUIRED", "Chủ phòng đã yêu cầu hiệu chuẩn lại cả phòng do đổi vị trí điểm phát. Hãy đo mới hai bước 1 m và 5 m.");
            }
            if (!bool(payload, "position_confirmed", false)) {
                throw fail("CALIBRATION_CONFIRM_REQUIRED", "Hãy xác nhận điểm phát vẫn ở cùng vị trí trước khi dùng lại hồ sơ hiệu chuẩn.");
            }
        }

        List<Integer> samples1m = readIntList(payload.path("samples_1m"), ProximityRules.MAX_CALIBRATION_SAMPLES);
        List<Integer> samples5m = readIntList(payload.path("samples_5m"), ProximityRules.MAX_CALIBRATION_SAMPLES);
        var eval = ProximityRules.evaluateCalibration(samples1m, samples5m);
        if (!eval.valid()) {
            throw fail("CALIBRATION_INVALID", eval.message());
        }

        // A new calibration must receive a new measurement before Ready can be accepted.
        p.resetMeasurementsOnly();
        p.calibrated = true;
        p.calibratedEpoch = configEpoch;
        p.calibratedAdapterId = adapterId;
        p.calibratedAdapterName = adapterName;
        p.calibratedBssid = bssid;
        p.calibratedBand = band;
        p.rssi1m = eval.step1m().medianRssiDbm();
        p.rssi5m = eval.step5m().medianRssiDbm();
        p.mad1m = eval.step1m().madDb();
        p.mad5m = eval.step5m().madDb();
        p.pathLossN = eval.pathLossN();
        p.updatedAtUtc = Instant.now().toString();

        return obj()
            .put("calibrated", true)
            .put("config_epoch", configEpoch)
            .put("rssi_1m", p.rssi1m)
            .put("rssi_5m", p.rssi5m)
            .put("mad_1m", p.mad1m)
            .put("mad_5m", p.mad5m)
            .put("path_loss_n", p.pathLossN)
            .put("message", eval.message())
            .set("self", buildSelfView(p, nowNano));
    }

    private static List<Integer> readIntList(JsonNode node, int maxItems) {
        if (!node.isArray()) {
            throw fail("INVALID_INPUT", "Danh sách mẫu RSSI phải là mảng số nguyên.");
        }
        if (node.size() > maxItems) {
            throw fail("INVALID_INPUT", "Số lượng mẫu vượt quá giới hạn cho phép.");
        }
        List<Integer> list = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToInt()) {
                throw fail("INVALID_INPUT", "Mẫu RSSI phải là số nguyên dBm.");
            }
            list.add(item.asInt());
        }
        return list;
    }

    /**
     * Processes a student's PROXIMITY_REPORT.
     * Returns true if the student lost Ready eligibility in the waiting room.
     */
    ObjectNode handleReport(
        String authenticatedPlayerId,
        JsonNode request,
        boolean running,
        String currentSessionId,
        long nowNano
    ) {
        JsonNode payload = request.path("payload");
        if (payload.hasNonNull("player_id") && !authenticatedPlayerId.equals(payload.path("player_id").asText())) {
            throw fail("FORBIDDEN", "Không được gửi báo cáo thay cho người chơi khác.");
        }
        PlayerProximity p = players.get(authenticatedPlayerId);
        if (p == null) {
            throw fail("FORBIDDEN", "Bạn chưa tham gia phòng.");
        }

        String reqSession = request.hasNonNull("session_id")
            ? request.path("session_id").asText()
            : payload.path("session_id").asText("");
        if (running && !currentSessionId.equals(reqSession)) {
            throw fail("WRONG_SESSION", "Báo cáo thuộc lượt chơi cũ.");
        }
        if (!running && !reqSession.isBlank()) {
            throw fail("WRONG_SESSION", "Lượt chơi trong báo cáo không còn hoạt động.");
        }

        int epoch = integer(payload, "config_epoch", 0, 1, Integer.MAX_VALUE);
        if (epoch != configEpoch) {
            throw fail("STALE_CONFIG_EPOCH", "Báo cáo thuộc cấu hình điểm phát cũ.");
        }

        if (!payload.path("seq").isIntegralNumber() || !payload.path("seq").canConvertToLong()) {
            throw fail("INVALID_INPUT", "Báo cáo cần số thứ tự nguyên hợp lệ.");
        }
        long seq = payload.path("seq").longValue();
        if (seq <= p.lastReportSeq) {
            throw fail("STALE_SEQUENCE", "Thứ tự báo cáo không hợp lệ hoặc bị trùng.");
        }
        if (p.lastReportReceivedNano > 0L && nowNano - p.lastReportReceivedNano < minReportIntervalNanos) {
            throw fail("RATE_LIMITED", "Gửi báo cáo tín hiệu quá nhanh. Tối đa một bản tin mỗi 5 giây.");
        }
        String deviceStatus = optional(payload, "device_status", "CONNECTED", 30);
        if (!Set.of("CONNECTED", "PERMISSION_DENIED", "WIFI_DISABLED", "NO_ADAPTER", "DISCONNECTED", "API_ERROR").contains(deviceStatus)) {
            throw fail("INVALID_INPUT", "Trạng thái thiết bị không hợp lệ.");
        }
        String adapterId = optional(payload, "adapter_id", "", 120);
        String adapterName = optional(payload, "adapter_name", "", 160);
        String ssid = optional(payload, "ssid", "", 64);
        String rawBssid = optional(payload, "bssid", "", 32);
        String rawBand = optional(payload, "band", "", 20);
        int channel = integer(payload, "channel", 0, 0, 300);
        int quality = integer(payload, "signal_quality", 0, 0, 100);

        // Validate the entire payload before changing any accepted state or consuming seq.
        if (deviceStatus.equals("CONNECTED")) {
            if (adapterId.isBlank() || ProximityRules.normalizeBssid(rawBssid).isEmpty()
                    || ProximityRules.normalizeBand(rawBand).isEmpty()) {
                throw fail("INVALID_INPUT", "Báo cáo cần card Wi-Fi, BSSID và băng tần hợp lệ.");
            }
            if (payload.has("samples")) {
                List<Integer> samples = readIntList(payload.get("samples"), ProximityRules.MEDIAN_WINDOW_SIZE);
                if (samples.stream().anyMatch(v -> !ProximityRules.isValidRssi(v))) {
                    throw fail("INVALID_INPUT", "Giá trị RSSI nằm ngoài dải hợp lệ (-100 đến -15 dBm).");
                }
            }
            if (payload.has("rssi_dbm")) {
                JsonNode rssi = payload.get("rssi_dbm");
                if (!rssi.isIntegralNumber() || !rssi.canConvertToInt() || !ProximityRules.isValidRssi(rssi.intValue())) {
                    throw fail("INVALID_INPUT", "Giá trị RSSI không hợp lệ.");
                }
            }
            if (payload.path("samples").isEmpty() && !payload.has("rssi_dbm")) {
                throw fail("INVALID_INPUT", "Báo cáo kết nối cần có ít nhất một mẫu RSSI hợp lệ.");
            }
        }
        p.lastReportSeq = seq;
        accessPoints.observe(p.apTrack, deviceStatus, adapterId, ssid,
            ProximityRules.normalizeBssid(rawBssid), nowNano);
        p.lastReportReceivedNano = nowNano;
        p.updatedAtUtc = Instant.now().toString();

        p.lastDeviceStatus = deviceStatus;
        p.currentAdapterId = adapterId;
        p.currentAdapterName = adapterName;
        p.currentSsid = ssid;
        p.currentChannel = channel;
        p.currentSignalQuality = quality;

        if (!deviceStatus.equals("CONNECTED")) {
            p.automaticDistance.unavailable(deviceStatus,nowNano);
            p.currentBssid = "";
            p.currentBand = "";
            p.rssiWindow.clear();
            p.currentMedianRssi = null;
            p.currentMadDb = null;
            p.currentDistanceMeters = null;
            p.lastValidReportNano = 0L;
            markMissingData(p, running, currentSessionId, statusErrorReason(p), nowNano);
            return buildSelfView(p, nowNano);
        }

        String bssid = ProximityRules.normalizeBssid(rawBssid);
        String band = ProximityRules.normalizeBand(rawBand);
        if (bssid.isEmpty() || band.isEmpty()) {
            throw fail("INVALID_INPUT", "Báo cáo thiếu BSSID hoặc băng tần hợp lệ.");
        }
        p.currentBssid = bssid;
        p.currentBand = band;

        candidateAnchors.put(
            bssid + "|" + band,
            new CandidateAnchor(ssid, bssid, band, p.nickname, p.updatedAtUtc)
        );
        while (candidateAnchors.size() > 16) {
            candidateAnchors.remove(candidateAnchors.keySet().iterator().next());
        }

        List<Integer> incomingSamples = new ArrayList<>();
        if (payload.has("samples") && payload.path("samples").isArray()) {
            for (JsonNode s : payload.path("samples")) {
                if (!s.isIntegralNumber() || !s.canConvertToInt()) {
                    throw fail("INVALID_INPUT", "Mẫu RSSI phải là số nguyên.");
                }
                int val = s.asInt();
                if (!ProximityRules.isValidRssi(val)) {
                    throw fail("INVALID_INPUT", "Giá trị RSSI nằm ngoài dải hợp lệ (-100 đến -15 dBm).");
                }
                incomingSamples.add(val);
            }
        }
        if (payload.has("rssi_dbm") && payload.path("rssi_dbm").isIntegralNumber()) {
            int single = payload.path("rssi_dbm").asInt();
            if (incomingSamples.isEmpty()) {
                if (!ProximityRules.isValidRssi(single)) {
                    throw fail("INVALID_INPUT", "Giá trị RSSI nằm ngoài dải hợp lệ (-100 đến -15 dBm).");
                }
                incomingSamples.add(single);
            }
            p.lastRawRssiDbm = single;
        } else if (!incomingSamples.isEmpty()) {
            p.lastRawRssiDbm = incomingSamples.getLast();
        } else {
            throw fail("INVALID_INPUT", "Báo cáo kết nối cần có ít nhất một mẫu RSSI hợp lệ.");
        }

        p.automaticDistance.accept(adapterId,ssid,bssid,band,incomingSamples,nowNano);

        // Check if adapter changed since calibration
        if (p.calibrated && !adapterId.isEmpty() && !p.calibratedAdapterId.equalsIgnoreCase(adapterId)) {
            p.clearCalibrationAndMeasurements();
        }

        // Check if connected to the room's anchor
        if (!anchorBssid.isEmpty() && (!anchorBssid.equals(bssid) || !anchorBand.equals(band))) {
            p.rssiWindow.clear();
            p.currentMedianRssi = null;
            p.currentMadDb = null;
            p.currentDistanceMeters = null;
            p.lastValidReportNano = 0L;
            if (p.calibrated && (!p.calibratedBssid.equals(bssid) || !p.calibratedBand.equals(band))) {
                p.calibrated = false;
            }
            if (running && !p.anchorMismatchAlertActive) {
                p.anchorMismatchAlertActive = true;
                recordAlert(
                    p,
                    currentSessionId,
                    "ANCHOR_MISMATCH",
                    null,
                    p.lastRawRssiDbm,
                    "Đổi sang điểm phát " + (ssid.isEmpty() ? bssid : ssid + " (" + bssid + ")") + " · " + band
                );
            }
            markMissingData(p, running, currentSessionId, statusErrorReason(p), nowNano);
            return buildSelfView(p, nowNano);
        }
        p.anchorMismatchAlertActive = false;

        if (!p.calibrated || p.calibratedEpoch != configEpoch) {
            p.lastValidReportNano = 0L;
            markMissingData(p, running, currentSessionId, "Thiết bị cần hiệu chuẩn lại.", nowNano);
            return buildSelfView(p, nowNano);
        }

        // Valid calibrated report for the configured anchor
        for (int sample : incomingSamples) {
            if (p.rssiWindow.size() >= ProximityRules.MEDIAN_WINDOW_SIZE) {
                p.rssiWindow.removeFirst();
            }
            p.rssiWindow.addLast(sample);
        }
        List<Integer> windowList = new ArrayList<>(p.rssiWindow);
        double medianRssi = ProximityRules.median(windowList);
        double madDb = ProximityRules.mad(windowList);
        double estimatedMeters = ProximityRules.estimateDistanceMeters(p.rssi1m, p.pathLossN, medianRssi);

        p.currentMedianRssi = medianRssi;
        p.currentMadDb = madDb;
        p.currentDistanceMeters = estimatedMeters;
        p.lastValidReportNano = nowNano;

        if (running) {
            restoreDataIfMissing(p, currentSessionId, estimatedMeters, (int) Math.round(medianRssi), nowNano);
            if (p.minDistanceMeters == null || estimatedMeters < p.minDistanceMeters) {
                p.minDistanceMeters = estimatedMeters;
            }
            if (p.maxDistanceMeters == null || estimatedMeters > p.maxDistanceMeters) {
                p.maxDistanceMeters = estimatedMeters;
            }
        }

        // Threshold state machine: 3 consecutive > threshold -> alert; 3 consecutive < 80% threshold -> recover
        updateThresholdAlertState(p, estimatedMeters, (int) Math.round(medianRssi), running, currentSessionId);

        return buildSelfView(p, nowNano);
    }

    private void updateThresholdAlertState(
        PlayerProximity p,
        double estimatedMeters,
        int medianRssiInt,
        boolean running,
        String currentSessionId
    ) {
        if (estimatedMeters > thresholdMeters) {
            p.consecutiveOverThreshold++;
            p.consecutiveRecovery = 0;
            if (!p.overThresholdAlertActive && p.consecutiveOverThreshold >= ProximityRules.ALERT_CONSECUTIVE_REPORTS) {
                p.overThresholdAlertActive = true;
                p.overThresholdCount++;
                if (running) {
                    recordAlert(
                        p,
                        currentSessionId,
                        "OVER_THRESHOLD",
                        estimatedMeters,
                        medianRssiInt,
                        String.format(
                            Locale.ROOT,
                            "Khoảng cách ước lượng %s vượt ngưỡng %d m trong %d báo cáo liên tiếp.",
                            ProximityRules.formatEstimatedDistance(estimatedMeters),
                            thresholdMeters,
                            p.consecutiveOverThreshold
                        )
                    );
                }
            }
        } else {
            p.consecutiveOverThreshold = 0;
            if (p.overThresholdAlertActive) {
                double recoveryLimit = thresholdMeters * ProximityRules.RECOVERY_THRESHOLD_RATIO;
                if (estimatedMeters < recoveryLimit) {
                    p.consecutiveRecovery++;
                    if (p.consecutiveRecovery >= ProximityRules.RECOVERY_CONSECUTIVE_REPORTS) {
                        p.overThresholdAlertActive = false;
                        p.consecutiveRecovery = 0;
                        if (running) {
                            recordAlert(
                                p,
                                currentSessionId,
                                "THRESHOLD_RECOVERED",
                                estimatedMeters,
                                medianRssiInt,
                                String.format(
                                    Locale.ROOT,
                                    "Khoảng cách ước lượng %s đã trở về dưới 80%% ngưỡng (%d m) sau 3 báo cáo liên tiếp.",
                                    ProximityRules.formatEstimatedDistance(estimatedMeters),
                                    thresholdMeters
                                )
                            );
                        }
                    }
                } else {
                    p.consecutiveRecovery = 0;
                }
            }
        }
    }

    private void markMissingData(PlayerProximity p, boolean running, String sessionId, String reason, long nowNano) {
        // Missing/invalid reports break consecutiveness and must not retain old RSSI samples.
        p.consecutiveOverThreshold = 0;
        p.consecutiveRecovery = 0;
        p.rssiWindow.clear();
        if (!running) {
            return;
        }
        if (p.missingDataSinceNano == 0L) {
            p.missingDataSinceNano = nowNano;
        }
        if (!p.dataLostAlertActive) {
            p.dataLostAlertActive = true;
            recordAlert(p, sessionId, "DATA_LOST", null, null, reason);
        }
    }

    private void restoreDataIfMissing(PlayerProximity p, String sessionId, double distanceM, int rssiDbm, long nowNano) {
        if (p.missingDataSinceNano > 0L) {
            long deltaMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(nowNano - p.missingDataSinceNano));
            p.accumulatedMissingDataMs += deltaMs;
            p.missingDataSinceNano = 0L;
        }
        if (p.dataLostAlertActive) {
            p.dataLostAlertActive = false;
            recordAlert(
                p,
                sessionId,
                "DATA_RESTORED",
                distanceM,
                rssiDbm,
                "Đã nhận lại dữ liệu tín hiệu Wi-Fi hợp lệ."
            );
        }
    }

    private void recordAlert(
        PlayerProximity p,
        String sessionId,
        String eventType,
        Double distanceM,
        Integer rssiDbm,
        String detail
    ) {
        p.alertCount++;
        if (sessionId != null && !sessionId.isBlank()) {
            pendingAlerts.add(new ProximityStore.AlertEventRow(
                sessionId,
                p.playerId,
                p.nickname,
                eventType,
                distanceM,
                rssiDbm,
                detail,
                Instant.now().toString()
            ));
        }
    }

    /**
     * Checks whether a student is eligible to set Ready or start the match.
     * Returns null if eligible, or a Failure with exact code and Vietnamese message if not.
     */
    Failure checkReadyEligibility(String playerId, long nowNano) {
        if (!enabled || !calibrationRequired) {
            return null;
        }
        if (anchorBssid.isEmpty() || anchorBand.isEmpty()) {
            return fail("PROXIMITY_NOT_CONFIGURED", "Chủ phòng chưa chọn điểm phát Wi-Fi làm mốc cho phòng thi.");
        }
        PlayerProximity p = players.get(playerId);
        if (p == null) {
            return fail("FORBIDDEN", "Bạn chưa tham gia phòng.");
        }
        if (p.lastDeviceStatus.equals("PERMISSION_DENIED")) {
            return fail("WIFI_PERMISSION_REQUIRED", "Bạn chưa cấp quyền đọc Wi-Fi. Vui lòng bật quyền truy cập vị trí trong Windows và thử lại.");
        }
        if (p.lastDeviceStatus.equals("WIFI_DISABLED")) {
            return fail("WIFI_DISABLED", "Wi-Fi đang tắt. Hãy bật Wi-Fi và kết nối vào điểm phát của phòng thi.");
        }
        if (p.lastDeviceStatus.equals("NO_ADAPTER")) {
            return fail("WIFI_NO_ADAPTER", "Không tìm thấy card Wi-Fi hỗ trợ trên thiết bị này.");
        }
        if (p.lastDeviceStatus.equals("DISCONNECTED")) {
            return fail("WIFI_DISCONNECTED", "Máy chưa kết nối Wi-Fi. Hãy kết nối vào điểm phát của phòng thi rồi thử lại.");
        }
        if (p.lastDeviceStatus.equals("API_ERROR")) {
            return fail("WIFI_API_ERROR", "Không đọc được tín hiệu Wi-Fi từ thiết bị. Hãy kiểm tra card mạng và thử lại.");
        }
        if (!p.currentBssid.isEmpty() && (!anchorBssid.equals(p.currentBssid) || !anchorBand.equals(p.currentBand))) {
            return fail("WRONG_ANCHOR", "Máy đang kết nối điểm phát hoặc băng tần khác với mốc của phòng thi.");
        }
        if (!p.calibrated || p.calibratedEpoch != configEpoch || !anchorBssid.equals(p.calibratedBssid) || !anchorBand.equals(p.calibratedBand)) {
            if (p.lastDeviceStatus.equals("NONE")) {
                return fail("PROXIMITY_CALIBRATION_REQUIRED", "Phòng thi bật giám sát khoảng cách. Hãy cấp quyền đọc Wi-Fi và hoàn tất hiệu chuẩn trước khi sẵn sàng (hoặc cập nhật Client mới).");
            }
            return fail("PROXIMITY_CALIBRATION_REQUIRED", "Bạn cần hoàn tất hiệu chuẩn khoảng cách (1 m và 5 m) trước khi sẵn sàng.");
        }
        if (!isFresh(p, nowNano)) {
            return fail("PROXIMITY_DATA_STALE", "Số đo Wi-Fi đã quá 15 giây hoặc chưa cập nhật. Hãy chờ số đo mới rồi bấm Sẵn sàng.");
        }
        return null;
    }

    boolean isReadyEligible(String playerId, long nowNano) {
        return checkReadyEligibility(playerId, nowNano) == null;
    }

    private boolean isFresh(PlayerProximity p, long nowNano) {
        return p.lastValidReportNano > 0L && (nowNano - p.lastValidReportNano) <= staleTimeoutNanos;
    }

    String effectiveStatus(PlayerProximity p, long nowNano) {
        if (!p.connected || !p.lastDeviceStatus.equals("CONNECTED")) {
            return "NO_DATA";
        }
        if (!anchorBssid.isEmpty() && (!anchorBssid.equals(p.currentBssid) || !anchorBand.equals(p.currentBand))) {
            return "NO_DATA";
        }
        if (!p.calibrated || p.calibratedEpoch != configEpoch) {
            return "UNCALIBRATED";
        }
        if (!isFresh(p, nowNano) || p.currentDistanceMeters == null) {
            return "NO_DATA";
        }
        if (p.overThresholdAlertActive) {
            return "OVER_THRESHOLD";
        }
        if (p.currentMadDb != null && p.currentMadDb > ProximityRules.MAX_MAD_DB) {
            return "FLUCTUATING";
        }
        return "MEASURING";
    }

    private String statusErrorReason(PlayerProximity p) {
        if (!p.connected) {
            return "Người chơi mất kết nối với máy chủ.";
        }
        return switch (p.lastDeviceStatus) {
            case "PERMISSION_DENIED" -> "Chưa cấp quyền truy cập vị trí trong Windows.";
            case "WIFI_DISABLED" -> "Công tắc Wi-Fi trên thiết bị đang tắt.";
            case "NO_ADAPTER" -> "Thiết bị không có card Wi-Fi hỗ trợ.";
            case "DISCONNECTED" -> "Thiết bị chưa kết nối mạng Wi-Fi.";
            case "API_ERROR" -> "Lỗi đọc dữ liệu từ card Wi-Fi.";
            case "CONNECTED" -> {
                if (!anchorBssid.isEmpty() && (!anchorBssid.equals(p.currentBssid) || !anchorBand.equals(p.currentBand))) {
                    String current = (p.currentSsid.isEmpty() ? p.currentBssid : p.currentSsid + " (" + p.currentBssid + ")") + " · " + p.currentBand;
                    yield "Đang kết nối sai điểm phát hoặc băng tần: " + current + ".";
                }
                yield "";
            }
            default -> "Chưa nhận được báo cáo tín hiệu từ thiết bị.";
        };
    }

    private String buildDetailText(PlayerProximity p, String effStatus, long nowNano) {
        if (effStatus.equals("NO_DATA")) {
            String err = statusErrorReason(p);
            if (!err.isEmpty()) {
                return err;
            }
            if (p.lastValidReportNano > 0L && !isFresh(p, nowNano)) {
                return "Dữ liệu đã quá 15 giây chưa được cập nhật.";
            }
            return "Chưa có dữ liệu đo hợp lệ.";
        }
        if (effStatus.equals("UNCALIBRATED")) {
            return "Đã kết nối đúng điểm phát làm mốc nhưng chưa hoàn tất hiệu chuẩn 1 m và 5 m.";
        }
        double dist = p.currentDistanceMeters == null ? 0.0 : p.currentDistanceMeters;
        double med = p.currentMedianRssi == null ? 0.0 : p.currentMedianRssi;
        double mad = p.currentMadDb == null ? 0.0 : p.currentMadDb;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            Locale.ROOT,
            "RSSI trung vị %.0f dBm (độ lệch %.1f dB) · Hệ số n = %.2f · Card: %s",
            med,
            mad,
            p.pathLossN,
            p.currentAdapterName.isEmpty() ? p.currentAdapterId : p.currentAdapterName
        ));
        if (ProximityRules.isExtrapolated(dist)) {
            sb.append(" · Ngoài khoảng hiệu chuẩn 1–5 m (kết quả ngoại suy).");
        }
        if (p.overThresholdAlertActive) {
            sb.append(" · Cảnh báo vượt ngưỡng ").append(thresholdMeters).append(" m.");
        }
        return sb.toString();
    }

    /**
     * Checks expiration in tick():
     * - In WAITING room, returns true if any ready player lost eligibility so caller can unready them.
     * - In running match, marks expired players as DATA_LOST and triggers periodic 30s persistence.
     */
    boolean tick(
        boolean running,
        String sessionId,
        int hostUserId,
        String roomName,
        String roomCode,
        String startedAtUtc,
        ProximityStore store,
        long nowNano
    ) {
        if (!enabled) {
            return false;
        }
        boolean stateChanged = false;
        for (PlayerProximity p : players.values()) {
            if (p.lastValidReportNano > 0L && !isFresh(p, nowNano)) {
                markMissingData(p, running, sessionId, "Dữ liệu tín hiệu quá 15 giây không cập nhật.", nowNano);
                p.lastValidReportNano = 0L;
                p.currentDistanceMeters = null;
                p.currentMedianRssi = null;
                p.currentMadDb = null;
                stateChanged = true;
            }
        }
        if (running && store != null && !sessionId.isBlank()) {
            if (lastPersistNano == 0L || (nowNano - lastPersistNano) >= persistIntervalNanos) {
                lastPersistNano = nowNano;
                store.enqueueSnapshot(buildSessionSnapshot(
                    sessionId,
                    hostUserId,
                    roomName,
                    roomCode,
                    "RUNNING",
                    startedAtUtc,
                    null,
                    nowNano
                ));
            }
        }
        return stateChanged;
    }

    void onMatchStart(
        String sessionId,
        int hostUserId,
        String roomName,
        String roomCode,
        String startedAtUtc,
        ProximityStore store,
        long nowNano
    ) {
        pendingAlerts.clear();
        for (PlayerProximity p : players.values()) {
            boolean hasValid = effectiveStatus(p, nowNano).equals("MEASURING")
                || effectiveStatus(p, nowNano).equals("OVER_THRESHOLD")
                || effectiveStatus(p, nowNano).equals("FLUCTUATING");
            p.resetMatchCounters(nowNano, hasValid);
        }
        lastPersistNano = nowNano;
        if (store != null && !sessionId.isBlank()) {
            store.enqueueSnapshot(buildSessionSnapshot(
                sessionId,
                hostUserId,
                roomName,
                roomCode,
                "RUNNING",
                startedAtUtc,
                null,
                nowNano
            ));
        }
    }

    void onMatchEnd(
        String sessionId,
        int hostUserId,
        String roomName,
        String roomCode,
        String startedAtUtc,
        boolean aborted,
        ProximityStore store,
        long nowNano
    ) {
        if (store == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String endedAt = Instant.now().toString();
        store.enqueueSnapshot(buildSessionSnapshot(
            sessionId,
            hostUserId,
            roomName,
            roomCode,
            aborted ? "ABORTED" : "COMPLETED",
            startedAtUtc,
            endedAt,
            nowNano
        ));
    }

    private ProximityStore.SessionSnapshot buildSessionSnapshot(
        String sessionId,
        int hostUserId,
        String roomName,
        String roomCode,
        String status,
        String startedAtUtc,
        String endedAtUtc,
        long nowNano
    ) {
        String nowUtc = Instant.now().toString();
        List<ProximityStore.PlayerSummaryRow> rows = new ArrayList<>(players.size());
        for (PlayerProximity p : players.values()) {
            long extraMissing = p.missingDataSinceNano > 0L
                ? Math.max(0L, TimeUnit.NANOSECONDS.toMillis(nowNano - p.missingDataSinceNano))
                : 0L;
            String eff = effectiveStatus(p, nowNano);
            Double lastDist = eff.equals("NO_DATA") || eff.equals("UNCALIBRATED") ? null : p.currentDistanceMeters;
            rows.add(new ProximityStore.PlayerSummaryRow(
                p.playerId,
                p.nickname,
                p.avatarId,
                p.calibrated && p.calibratedEpoch == configEpoch,
                p.currentAdapterName.isEmpty() ? p.calibratedAdapterName : p.currentAdapterName,
                p.calibrated ? p.pathLossN : null,
                p.minDistanceMeters,
                p.maxDistanceMeters,
                lastDist,
                eff,
                p.accumulatedMissingDataMs + extraMissing,
                p.overThresholdCount,
                p.alertCount,
                p.updatedAtUtc.isEmpty() ? nowUtc : p.updatedAtUtc
            ));
        }
        List<ProximityStore.AlertEventRow> alertsToPersist = new ArrayList<>(pendingAlerts);
        pendingAlerts.clear();
        return new ProximityStore.SessionSnapshot(
            sessionId,
            hostUserId,
            roomName,
            roomCode,
            enabled,
            anchorSsid,
            anchorBssid,
            anchorBand,
            thresholdMeters,
            status,
            startedAtUtc.isEmpty() ? nowUtc : startedAtUtc,
            nowUtc,
            endedAtUtc,
            rows,
            alertsToPersist
        );
    }

    ObjectNode buildRoomStateSection(String viewerPlayerId, boolean locked, long nowNano) {
        ObjectNode node = obj()
            .put("automatic_distance_supported",true).put("calibration_required",calibrationRequired)
            .put("ap_tracking_supported", true)
            .put("supported", true)
            .put("capability", ProximityRules.CAPABILITY)
            .put("enabled", enabled)
            .put("config_epoch", configEpoch)
            .put("anchor_ssid", anchorSsid)
            .put("anchor_bssid", anchorBssid)
            .put("anchor_band", anchorBand)
            .put("threshold_meters", thresholdMeters)
            .put("recalibrated_at_utc", recalibratedAtUtc)
            .put("require_fresh_calibration", requireFreshCalibration)
            .put("locked", locked);
        if (viewerPlayerId != null && !viewerPlayerId.isBlank()) {
            PlayerProximity self = players.get(viewerPlayerId);
            if (self != null) {
                node.set("self", buildSelfView(self, nowNano));
            }
        }
        return node;
    }

    ObjectNode buildSelfView(PlayerProximity p, long nowNano) {
        String eff = effectiveStatus(p, nowNano);
        boolean showDist = !eff.equals("NO_DATA") && !eff.equals("UNCALIBRATED") && p.currentDistanceMeters != null;
        Failure readyFail = checkReadyEligibility(p.playerId, nowNano);
        ObjectNode self = obj()
            .put("player_id", p.playerId)
            .put("config_epoch", configEpoch)
            .put("calibrated", p.calibrated && p.calibratedEpoch == configEpoch)
            .put("status", eff)
            .put("status_label", ProximityStore.playerStatusLabel(eff))
            .put("distance_label", showDist ? ProximityRules.formatEstimatedDistance(p.currentDistanceMeters) : "Không có dữ liệu")
            .put("extrapolated", showDist && ProximityRules.isExtrapolated(p.currentDistanceMeters))
            .put("over_threshold", p.overThresholdAlertActive)
            .put("fresh", isFresh(p, nowNano))
            .put("ready_eligible", readyFail == null)
            .put("message", readyFail == null ? "Đủ điều kiện sẵn sàng." : readyFail.getMessage())
            .put("detail", buildDetailText(p, eff, nowNano))
            .put("updated_at_utc", p.updatedAtUtc);
        if (showDist) {
            self.put("estimated_meters", Math.round(p.currentDistanceMeters * 10.0) / 10.0);
        }
        self.set("access_point", accessPoints.view(p.apTrack, p.connected, nowNano));
        self.set("ap_distance",apDistance(p,nowNano));
        return self;
    }

    private ObjectNode apDistance(PlayerProximity p,long now){
        var result=p.automaticDistance.snapshot(p.connected,now);
        if(result.path("fresh").asBoolean()&&p.calibrated&&p.calibratedEpoch==configEpoch&&p.calibratedAdapterId.equalsIgnoreCase(p.currentAdapterId)
            &&p.calibratedBssid.equals(p.currentBssid)&&p.calibratedBand.equals(p.currentBand)&&isFresh(p,now)&&p.currentDistanceMeters!=null){
            result.put("mode","CALIBRATED").put("calibrated",true).put("estimated_meters",Math.round(p.currentDistanceMeters*10)/10.0)
                .put("distance_label",ProximityRules.formatEstimatedDistance(p.currentDistanceMeters)).put("method_label","Theo hồ sơ hiệu chuẩn")
                .put("reference_rssi_1m",p.rssi1m).put("path_loss_n",p.pathLossN).put("clamped",p.currentDistanceMeters<=.2||p.currentDistanceMeters>=200);
        }
        return result;
    }
    ObjectNode apDistance(String playerId,long now){var p=players.get(playerId);return p==null?obj():apDistance(p,now);}

    ObjectNode buildMonitoringSnapshot(String roomCode, String roomName, String phase, String sessionId, boolean locked, long nowNano) {
        ObjectNode root = obj()
            .put("automatic_distance_supported",true).put("calibration_required",calibrationRequired)
            .put("room_code", roomCode)
            .put("room_name", roomName)
            .put("phase", phase)
            .put("session_id", sessionId)
            .put("enabled", enabled)
            .put("config_epoch", configEpoch)
            .put("anchor_ssid", anchorSsid)
            .put("anchor_bssid", anchorBssid)
            .put("anchor_band", anchorBand)
            .put("threshold_meters", thresholdMeters)
            .put("recalibrated_at_utc", recalibratedAtUtc)
            .put("locked", locked)
            .put("persistence_warning", persistenceWarning);

        ArrayNode candidates = root.putArray("candidate_anchors");
        root.set("ap_config", accessPoints.summary());
        for (CandidateAnchor c : candidateAnchors.values()) {
            candidates.add(obj()
                .put("ssid", c.ssid())
                .put("bssid", c.bssid())
                .put("band", c.band())
                .put("reported_by", c.reportedByNickname())
                .put("updated_at_utc", c.updatedAtUtc()));
        }

        ArrayNode list = root.putArray("players");
        for (PlayerProximity p : players.values()) {
            String eff = effectiveStatus(p, nowNano);
            boolean showDist = !eff.equals("NO_DATA") && !eff.equals("UNCALIBRATED") && p.currentDistanceMeters != null;
            ObjectNode item = obj()
                .put("player_id", p.playerId)
                .put("nickname", p.nickname)
                .put("avatar_id", p.avatarId)
                .put("connected", p.connected)
                .put("calibrated", p.calibrated && p.calibratedEpoch == configEpoch)
                .put("status", eff)
                .put("status_label", ProximityStore.playerStatusLabel(eff))
                .put("distance_label", showDist ? ProximityRules.formatEstimatedDistance(p.currentDistanceMeters) : "Không có dữ liệu")
                .put("extrapolated", showDist && ProximityRules.isExtrapolated(p.currentDistanceMeters))
                .put("over_threshold", p.overThresholdAlertActive)
                .put("alert_count", p.alertCount)
                .put("over_threshold_count", p.overThresholdCount)
                .put("updated_at_utc", p.updatedAtUtc)
                .put("measurement_age_ms",p.lastReportReceivedNano==0?-1:TimeUnit.NANOSECONDS.toMillis(Math.max(0,nowNano-p.lastReportReceivedNano)))
                .put("adapter_name", p.currentAdapterName.isEmpty() ? p.calibratedAdapterName : p.currentAdapterName)
                .put("ssid", p.currentSsid)
                .put("bssid", p.currentBssid)
                .put("band", p.currentBand)
                .put("channel", p.currentChannel)
                .put("detail", buildDetailText(p, eff, nowNano))
                .put("error_reason", statusErrorReason(p));
            if (showDist) {
                item.put("estimated_meters", Math.round(p.currentDistanceMeters * 10.0) / 10.0);
            }
            if (p.currentMedianRssi != null) {
                item.put("median_rssi_dbm", Math.round(p.currentMedianRssi));
            }
            if (p.currentMadDb != null) {
                item.put("mad_db", Math.round(p.currentMadDb * 10.0) / 10.0);
            }
            if (p.calibrated && p.calibratedEpoch == configEpoch) {
                item.put("path_loss_n", Math.round(p.pathLossN * 100.0) / 100.0);
            }
            list.add(item);
            item.set("access_point", accessPoints.view(p.apTrack, p.connected, nowNano));
            item.set("ap_distance",apDistance(p,nowNano));
        }
        return root;
    }
}
