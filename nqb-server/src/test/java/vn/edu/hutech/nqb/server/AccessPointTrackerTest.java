package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class AccessPointTrackerTest {
    @TempDir Path data;
    private final AccessPointTracker tracker = new AccessPointTracker();
    private final AccessPointTracker.Track track = new AccessPointTracker.Track();
    private long now = TimeUnit.SECONDS.toNanos(1);
    static String bssid(int number) { return "aa:bb:cc:dd:ee:" + String.format("%02x", number); }
    static ObjectNode row(int number, String name, String corridor, int position) {
        return obj().put("ssid", "HUTECH E1").put("bssid", bssid(number)).put("name", name)
            .put("corridor", corridor).put("position", position);
    }
    static ObjectNode config() {
        var config = obj().put("revision", 1);
        config.putArray("access_points").add(row(1, "AP1", "Tầng 3 / A", 1))
            .add(row(2, "AP2", "Tầng 3 / A", 2)).add(row(3, "AP3", "Tầng 3 / A", 3))
            .add(row(4, "AP1", "Tầng 3 / A", 1)).add(row(5, "AP5", "Tầng 3 / B", 1));
        return config;
    }
    void report(int ap) { report(ap, "HUTECH E1", "adapter-1", "CONNECTED"); }
    void report(int ap, String ssid, String adapter, String status) {
        now += TimeUnit.SECONDS.toNanos(5);
        tracker.observe(track, status, adapter, ssid, bssid(ap), now);
    }
    ObjectNode view() { return tracker.view(track, true, now); }
    void settle(int ap) { report(ap); report(ap); }

    @Test void tracksSameSsidForwardBackwardAndRadioAliasesWithoutCalibration() {
        tracker.configure(config()); settle(1);
        assertEquals("AP1", view().path("ap_name").asText());
        assertEquals("UNKNOWN", view().path("direction").asText());
        settle(2); assertEquals("FORWARD", view().path("direction").asText());
        settle(3); assertEquals("FORWARD", view().path("direction").asText());
        settle(1); assertEquals("BACKWARD", view().path("direction").asText());
        int history = view().path("history").size();
        settle(4);
        assertEquals(history, view().path("history").size(), "Different radio on AP1 is not movement");
        assertEquals("AP1", view().path("ap_name").asText());
        assertEquals(bssid(4), view().path("bssid").asText());
    }

    @Test void oscillationDoesNotInventTransitionsAndDirectionExpiresWhileReportsContinue() {
        tracker.configure(config()); settle(1);
        report(2);
        assertEquals("AP2", view().path("ap_name").asText(), "Show actual association immediately");
        assertEquals("CONFIRMING", view().path("status").asText());
        report(1); report(2); report(1); report(1);
        assertEquals(1, view().path("history").size());
        settle(2); assertEquals("FORWARD", view().path("direction").asText());
        for (int i = 0; i < 7; i++) report(2);
        assertEquals("UNKNOWN", view().path("direction").asText());
    }

    @Test void staleDisconnectedUnmappedOtherNetworkAndAdapterChangesBreakContinuity() {
        tracker.configure(config()); settle(1);
        now += TimeUnit.SECONDS.toNanos(16);
        assertEquals("STALE", view().path("status").asText());
        assertEquals("", view().path("ap_name").asText());
        settle(2); assertEquals("UNKNOWN", view().path("direction").asText());
        report(2, "HUTECH E1", "adapter-1", "PERMISSION_DENIED");
        assertEquals("PERMISSION_DENIED", view().path("status").asText());
        settle(3); assertEquals("UNKNOWN", view().path("direction").asText());
        report(99); assertEquals("UNMAPPED", view().path("status").asText());
        settle(1); assertEquals("UNKNOWN", view().path("direction").asText());
        report(2, "Other network", "adapter-1", "CONNECTED");
        assertEquals("UNMAPPED", view().path("status").asText());
        settle(2); assertEquals("UNKNOWN", view().path("direction").asText());
        report(1, "HUTECH E1", "adapter-2", "CONNECTED");
        report(1, "HUTECH E1", "adapter-2", "CONNECTED");
        assertEquals("UNKNOWN", view().path("direction").asText());
        assertEquals("STALE", tracker.view(track, false, now).path("status").asText());
    }

    @Test void differentCorridorsNeverImplyDirectionAndHistoryIsBounded() {
        tracker.configure(config()); settle(1); settle(5);
        assertEquals("UNKNOWN", view().path("direction").asText());
        for (int i = 0; i < 20; i++) settle(i % 2 + 1);
        assertEquals(8, view().path("history").size());
    }

    @Test void validatesWholeCatalogBeforeWritingAndPersistsAcrossRestart() throws Exception {
        tracker.load(data.resolve("quiz.db")); tracker.configure(config());
        var file = data.resolve("quiz.db.access-points.json");
        String saved = Files.readString(file);
        var duplicate = config().put("revision", 2); duplicate.withArray("access_points").add(row(1, "AP2", "Tầng 3 / A", 2));
        assertThrows(Failure.class, () -> tracker.configure(duplicate));
        assertEquals(saved, Files.readString(file));
        var conflictingRadio = config().put("revision", 2); conflictingRadio.withArray("access_points").add(row(6, "AP1", "Tầng 3 / A", 6));
        assertThrows(Failure.class, () -> tracker.configure(conflictingRadio));
        var conflictingPosition = config().put("revision", 2); conflictingPosition.withArray("access_points").add(row(6, "AP6", "Tầng 3 / A", 1));
        assertThrows(Failure.class, () -> tracker.configure(conflictingPosition));
        assertEquals("STALE_AP_CONFIG", assertThrows(Failure.class, () -> tracker.configure(config())).code);
        var reloaded = new AccessPointTracker(); reloaded.load(data.resolve("quiz.db"));
        assertEquals(tracker.configuration().path("access_points"), reloaded.configuration().path("access_points"));
    }

    @Test void failedSaveKeepsOldCatalogAndCorruptFilesAreNotSilentlyOverwritten() throws Exception {
        tracker.load(data.resolve("quiz.db")); tracker.configure(config());
        var before = tracker.configuration();
        var file = data.resolve("quiz.db.access-points.json");
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("block"), "fixture");
        assertEquals("AP_SAVE_FAILED", assertThrows(Failure.class, () -> tracker.configure(config().put("revision", 2))).code);
        assertEquals(before, tracker.configuration());
        Path invalid = data.resolve("invalid.db.access-points.json"); Files.writeString(invalid, "{}");
        assertThrows(java.io.IOException.class, () -> new AccessPointTracker().load(data.resolve("invalid.db")));
    }

    @Test void rejectedReportsCannotChangeApAndDisabledDistanceStillTracks() {
        var proximity = new ProximityRoomTracker();
        proximity.configureAccessPoints(config()); proximity.onPlayerJoin("p", "Client", "avatar");
        var report = ProximityServerIntegrationTest.reportPayload(1, 1, bssid(1), "5 GHz", -50, "").put("ssid", "HUTECH E1");
        now += TimeUnit.SECONDS.toNanos(5);
        var self = proximity.handleReport("p", obj().set("payload", report), false, "", now);
        assertEquals("AP1", self.path("access_point").path("ap_name").asText());
        assertTrue(self.path("ready_eligible").asBoolean());
        var invalid = report.deepCopy().put("seq", 2).put("bssid", bssid(2)).put("rssi_dbm", 1234);
        now += TimeUnit.SECONDS.toNanos(5);
        assertThrows(Failure.class, () -> proximity.handleReport("p", obj().set("payload", invalid), false, "", now));
        assertEquals("AP1", proximity.buildRoomStateSection("p", false, now).path("self").path("access_point").path("ap_name").asText());
        assertThrows(Failure.class, () -> proximity.handleReport("p", obj().set("payload", report), false, "", now));
    }
}
