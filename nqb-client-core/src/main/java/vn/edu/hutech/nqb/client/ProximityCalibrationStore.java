package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.ProximityRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Local storage of laptop Wi-Fi calibration profiles keyed by (adapterId, BSSID, band).
 * Changing adapter, BSSID, or band requires a separate profile; host-triggered room recalibration
 * invalidates profiles saved before the recalibration timestamp.
 */
public final class ProximityCalibrationStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record Profile(
        String adapterId,
        String adapterName,
        String ssid,
        String bssid,
        String band,
        List<Integer> samples1m,
        List<Integer> samples5m,
        double rssi1m,
        double rssi5m,
        double mad1m,
        double mad5m,
        double pathLossN,
        String savedAtUtc
    ) { }

    private final Path file;

    public ProximityCalibrationStore() {
        this(defaultPath());
    }

    public ProximityCalibrationStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public static Path defaultPath() {
        String override = System.getProperty("nqb.proximity.store", "").strip();
        if (!override.isEmpty()) {
            return Path.of(override).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home", "."), ".nqb", "proximity-calibrations.json").toAbsolutePath();
    }

    public static String key(String adapterId, String bssid, String band) {
        String a = adapterId == null ? "" : adapterId.strip().toLowerCase(Locale.ROOT);
        String b = WifiSignalProvider.normalizeBssid(bssid);
        String f = WifiSignalProvider.normalizeBand(band);
        if (a.isEmpty() || b.isEmpty() || f.isEmpty()) {
            return "";
        }
        return a + "|" + b + "|" + f;
    }

    public synchronized Optional<Profile> find(String adapterId, String bssid, String band, String roomRecalibratedAtUtc) {
        String targetKey = key(adapterId, bssid, band);
        if (targetKey.isEmpty() || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(Files.readString(file));
            JsonNode node = root.path("profiles").path(targetKey);
            if (!node.isObject()) {
                return Optional.empty();
            }
            List<Integer> s1 = readSamples(node.path("samples_1m"));
            List<Integer> s5 = readSamples(node.path("samples_5m"));
            var eval = ProximityRules.evaluateCalibration(s1, s5);
            if (!eval.valid()) {
                return Optional.empty();
            }
            String savedAt = node.path("saved_at_utc").asText("");
            if (roomRecalibratedAtUtc != null && !roomRecalibratedAtUtc.isBlank()) {
                try {
                    if (Instant.parse(savedAt).isBefore(Instant.parse(roomRecalibratedAtUtc))) {
                        return Optional.empty();
                    }
                } catch (Exception invalidTimestamp) {
                    return Optional.empty();
                }
            }
            return Optional.of(new Profile(
                node.path("adapter_id").asText(adapterId.strip()),
                node.path("adapter_name").asText(""),
                node.path("ssid").asText(""),
                WifiSignalProvider.normalizeBssid(bssid),
                WifiSignalProvider.normalizeBand(band),
                s1,
                s5,
                eval.step1m().medianRssiDbm(),
                eval.step5m().medianRssiDbm(),
                eval.step1m().madDb(),
                eval.step5m().madDb(),
                eval.pathLossN(),
                savedAt
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public synchronized Profile save(
        String adapterId,
        String adapterName,
        String ssid,
        String bssid,
        String band,
        List<Integer> raw1m,
        List<Integer> raw5m
    ) {
        String targetKey = key(adapterId, bssid, band);
        if (targetKey.isEmpty()) {
            throw new IllegalArgumentException("Thiếu thông tin card Wi-Fi, BSSID hoặc băng tần.");
        }
        List<Integer> s1 = ProximityRules.filterValidSamples(raw1m);
        List<Integer> s5 = ProximityRules.filterValidSamples(raw5m);
        var eval = ProximityRules.evaluateCalibration(s1, s5);
        if (!eval.valid()) {
            throw new IllegalArgumentException(eval.message());
        }
        String now = Instant.now().toString();
        Profile profile = new Profile(
            adapterId.strip(),
            adapterName == null ? "" : adapterName.strip(),
            ssid == null ? "" : ssid.strip(),
            WifiSignalProvider.normalizeBssid(bssid),
            WifiSignalProvider.normalizeBand(band),
            s1,
            s5,
            eval.step1m().medianRssiDbm(),
            eval.step5m().medianRssiDbm(),
            eval.step1m().madDb(),
            eval.step5m().madDb(),
            eval.pathLossN(),
            now
        );
        try {
            ObjectNode root = loadRoot();
            ObjectNode profiles = root.withObject("/profiles");
            ObjectNode entry = JSON.createObjectNode()
                .put("adapter_id", profile.adapterId())
                .put("adapter_name", profile.adapterName())
                .put("ssid", profile.ssid())
                .put("bssid", profile.bssid())
                .put("band", profile.band())
                .put("rssi_1m", profile.rssi1m())
                .put("rssi_5m", profile.rssi5m())
                .put("mad_1m", profile.mad1m())
                .put("mad_5m", profile.mad5m())
                .put("path_loss_n", profile.pathLossN())
                .put("saved_at_utc", profile.savedAtUtc());
            ArrayNode a1 = entry.putArray("samples_1m");
            s1.forEach(a1::add);
            ArrayNode a5 = entry.putArray("samples_5m");
            s5.forEach(a5::add);
            profiles.set(targetKey, entry);
            writeRoot(root);
        } catch (Exception error) {
            throw new IllegalStateException("Đã hiệu chuẩn nhưng không lưu được hồ sơ trên máy. Lần sau cần đo lại.", error);
        }
        return profile;
    }

    public synchronized void remove(String adapterId, String bssid, String band) {
        String targetKey = key(adapterId, bssid, band);
        if (targetKey.isEmpty() || !Files.isRegularFile(file)) return;
        try {
            ObjectNode root = loadRoot();
            root.withObject("/profiles").remove(targetKey);
            writeRoot(root);
        } catch (Exception ignored) {
        }
    }

    private ObjectNode loadRoot() {
        if (Files.isRegularFile(file)) {
            try {
                JsonNode existing = JSON.readTree(Files.readString(file));
                if (existing instanceof ObjectNode obj) {
                    return obj;
                }
            } catch (Exception ignored) {
            }
        }
        ObjectNode root = JSON.createObjectNode();
        root.putObject("profiles");
        return root;
    }

    private void writeRoot(ObjectNode root) throws Exception {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "proximity-", ".tmp");
        try {
            Files.writeString(temp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static List<Integer> readSamples(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<Integer> list = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isIntegralNumber()) {
                list.add(item.asInt());
            }
        }
        return List.copyOf(list);
    }
}
