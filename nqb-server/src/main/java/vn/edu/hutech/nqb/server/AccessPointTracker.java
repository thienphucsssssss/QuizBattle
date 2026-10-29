package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.edu.hutech.nqb.shared.ProximityRules;
import static vn.edu.hutech.nqb.server.Wire.*;

/** Association identity is exact; direction is only an inference along a surveyed corridor. */
final class AccessPointTracker {
    static final long STALE_NANOS = TimeUnit.SECONDS.toNanos(15);
    static final long DIRECTION_NANOS = TimeUnit.SECONDS.toNanos(30);
    record Ap(String ssid, String bssid, String name, String corridor, int position) {
        String physicalKey() { return ssid + "\u0000" + corridor + "\u0000" + name; }
        ObjectNode json() {
            return obj().put("ssid", ssid).put("bssid", bssid).put("name", name)
                .put("corridor", corridor).put("position", position);
        }
    }
    static final class Track {
        String ssid = "", bssid = "", adapter = "", deviceStatus = "NONE";
        long lastReport, candidateSince, directionAt;
        Ap candidate, confirmed;
        String direction = "UNKNOWN", directionLabel = "Chưa đủ dữ liệu chuyển AP";
        final Deque<ObjectNode> history = new ArrayDeque<>();
        void breakContinuity() {
            candidate = confirmed = null;
            candidateSince = directionAt = 0;
            direction = "UNKNOWN";
            directionLabel = "Chưa đủ dữ liệu chuyển AP";
        }
        void reset() { breakContinuity(); history.clear(); }
    }
    private Map<String, Ap> catalog = new LinkedHashMap<>();
    private Path file;
    private int revision = 1;

    void load(Path database) throws IOException {
        file = database.toAbsolutePath().resolveSibling(database.getFileName() + ".access-points.json");
        if (Files.exists(file)) {
            try { catalog = validate(JSON.readTree(Files.readString(file))); }
            catch (RuntimeException e) { throw new IOException("Cấu hình AP không hợp lệ: " + file, e); }
        }
    }

    ObjectNode configuration() {
        var result = obj().put("supported", true).put("revision", revision);
        var rows = result.putArray("access_points");
        catalog.values().forEach(ap -> rows.add(ap.json()));
        return result;
    }

    ObjectNode summary() { return obj().put("supported", true).put("revision", revision).put("bssid_count", catalog.size()); }

    void configure(JsonNode payload) {
        int expected = integer(payload, "revision", 0, 1, Integer.MAX_VALUE);
        if (expected != revision) throw fail("STALE_AP_CONFIG", "Bản đồ AP đã thay đổi. Hãy mở lại cấu hình.");
        var next = validate(payload);
        if (file != null) {
            Path temporary = null;
            try {
                Files.createDirectories(file.getParent());
                temporary = Files.createTempFile(file.getParent(), "access-points-", ".tmp");
                var document = obj();
                var rows = document.putArray("access_points");
                next.values().forEach(ap -> rows.add(ap.json()));
                Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document));
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw fail("AP_SAVE_FAILED", "Không lưu được bản đồ AP trên server. Cấu hình cũ vẫn được giữ.");
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
        catalog = next;
        revision++;
    }

    private static Map<String, Ap> validate(JsonNode payload) {
        JsonNode rows = payload == null ? null : payload.get("access_points");
        if (rows == null || !rows.isArray() || rows.size() > 64)
            throw fail("INVALID_INPUT", "Bản đồ AP cần danh sách tối đa 64 BSSID.");
        Map<String, Ap> next = new LinkedHashMap<>();
        Map<String, Ap> physical = new HashMap<>();
        Map<String, String> positions = new HashMap<>();
        for (var row : rows) {
            String bssid = ProximityRules.normalizeBssid(text(row, "bssid", 32));
            if (bssid.isEmpty() || bssid.equals("00:00:00:00:00:00") || bssid.equals("ff:ff:ff:ff:ff:ff"))
                throw fail("INVALID_INPUT", "BSSID không hợp lệ.");
            var ap = new Ap(singleLine(row, "ssid", 64), bssid, singleLine(row, "name", 32),
                singleLine(row, "corridor", 64), integer(row, "position", 0, 1, 10000));
            if (ap.position() == 0) throw fail("INVALID_INPUT", "Hãy nhập thứ tự AP trên hành lang (từ 1).");
            if (next.putIfAbsent(bssid, ap) != null) throw fail("INVALID_INPUT", "BSSID bị trùng: " + bssid);
            Ap prior = physical.putIfAbsent(ap.physicalKey(), ap);
            if (prior != null && prior.position() != ap.position())
                throw fail("INVALID_INPUT", "Các BSSID của cùng AP phải có cùng thứ tự.");
            String slot = ap.ssid() + "\u0000" + ap.corridor() + "\u0000" + ap.position();
            String name = positions.putIfAbsent(slot, ap.name());
            if (name != null && !name.equals(ap.name()))
                throw fail("INVALID_INPUT", "Hai AP khác nhau không được có cùng thứ tự trong một hành lang.");
        }
        return next;
    }
    private static String singleLine(JsonNode row, String key, int max) {
        String value = text(row, key, max);
        if (value.codePoints().anyMatch(Character::isISOControl)) throw fail("INVALID_INPUT", "Trường " + key + " phải nằm trên một dòng.");
        return value;
    }
    private Ap resolve(String ssid, String bssid) {
        Ap ap = catalog.get(bssid);
        return ap != null && ap.ssid().equals(ssid) ? ap : null;
    }

    void observe(Track track, String status, String adapter, String ssid, String bssid, long now) {
        if (track.lastReport == 0 || now - track.lastReport > STALE_NANOS || !track.adapter.equals(adapter))
            track.breakContinuity();
        track.lastReport = now;
        track.deviceStatus = status;
        track.adapter = adapter;
        track.ssid = ssid;
        track.bssid = status.equals("CONNECTED") ? bssid : "";
        Ap current = status.equals("CONNECTED") ? resolve(ssid, bssid) : null;
        if (current == null) { track.breakContinuity(); return; }
        if (track.candidate == null || !track.candidate.physicalKey().equals(current.physicalKey())) {
            track.candidate = current;
            track.candidateSince = now;
            return;
        }
        // Require two reports spanning at least four seconds, suppressing brief roaming oscillation.
        if (now - track.candidateSince < TimeUnit.SECONDS.toNanos(4)) return;
        if (track.confirmed != null && track.confirmed.physicalKey().equals(current.physicalKey())) return;
        Ap previous = track.confirmed;
        boolean continuous = previous != null && previous.ssid().equals(current.ssid())
            && previous.corridor().equals(current.corridor());
        track.direction = continuous ? (current.position() > previous.position() ? "FORWARD" : "BACKWARD") : "UNKNOWN";
        track.directionLabel = continuous ? "Hướng suy đoán: " + previous.name() + " → " + current.name()
            + (track.direction.equals("FORWARD") ? " (tăng thứ tự)" : " (giảm thứ tự)") : "Chưa đủ dữ liệu chuyển AP";
        track.directionAt = continuous ? now : 0;
        track.confirmed = current;
        var event = obj().put("ap_name", current.name()).put("at_utc", Instant.now().toString()).put("continuous", continuous)
            .put("from_ap", previous == null ? "" : previous.name()).put("direction", track.direction);
        track.history.addLast(event);
        while (track.history.size() > 8) track.history.removeFirst();
    }

    ObjectNode view(Track track, boolean connected, long now) {
        boolean fresh = connected && track.lastReport != 0 && now - track.lastReport <= STALE_NANOS;
        boolean associated = fresh && track.deviceStatus.equals("CONNECTED");
        Ap current = associated ? resolve(track.ssid, track.bssid) : null;
        boolean stable = current != null && track.confirmed != null && track.candidate != null
            && current.physicalKey().equals(track.confirmed.physicalKey())
            && now - track.candidateSince >= TimeUnit.SECONDS.toNanos(4);
        boolean directionFresh = stable && track.directionAt != 0 && now - track.directionAt <= DIRECTION_NANOS;
        String status = !fresh ? "STALE" : !associated ? track.deviceStatus : current == null ? "UNMAPPED" : stable ? "MAPPED" : "CONFIRMING";
        var result = obj().put("status", status).put("fresh", fresh).put("stable", stable)
            .put("ssid", associated ? track.ssid : "").put("bssid", associated ? track.bssid : "")
            .put("ap_name", current == null ? "" : current.name())
            .put("ap_label", !fresh ? "Chưa có dữ liệu AP mới" : !associated ? "Không đọc được AP"
                : current == null ? "Chưa gán tên · " + track.bssid : current.name())
            .put("corridor", current == null ? "" : current.corridor())
            .put("direction", directionFresh ? track.direction : "UNKNOWN")
            .put("direction_age_ms", directionFresh ? TimeUnit.NANOSECONDS.toMillis(now - track.directionAt) : -1)
            .put("direction_label", directionFresh ? track.directionLabel : status.equals("CONFIRMING")
                ? "Đang xác nhận AP" : "Chưa xác định hướng di chuyển");
        if (current != null) result.put("position", current.position());
        var history = result.putArray("history");
        track.history.forEach(event -> history.add(event.deepCopy()));
        return result;
    }
}
