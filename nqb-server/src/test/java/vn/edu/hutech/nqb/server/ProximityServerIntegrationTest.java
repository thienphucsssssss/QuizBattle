package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.client.Endpoint;
import vn.edu.hutech.nqb.client.GatewayException;
import vn.edu.hutech.nqb.client.TlsQuizGateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class ProximityServerIntegrationTest {

    @TempDir
    static Path certs;

    @TempDir
    Path data;

    static final char[] KEY_PASSWORD = "ephemeral-test-only".toCharArray();
    static Path key, trust;

    QuizServer server;
    int port;
    final List<Client> clients = new ArrayList<>();

    @BeforeAll
    static void certificates() throws Exception {
        key = certs.resolve("server.p12");
        trust = certs.resolve("trust.p12");
        Path certificate = certs.resolve("server.cer");
        ServerIntegrationTest.keytool("-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-storetype", "PKCS12",
            "-keystore", key.toString(), "-storepass", new String(KEY_PASSWORD));
        ServerIntegrationTest.keytool("-exportcert", "-alias", "test", "-keystore", key.toString(),
            "-storepass", new String(KEY_PASSWORD), "-file", certificate.toString());
        ServerIntegrationTest.keytool("-importcert", "-noprompt", "-alias", "test", "-storetype", "PKCS12",
            "-keystore", trust.toString(), "-storepass", new String(KEY_PASSWORD), "-file", certificate.toString());
    }

    @BeforeEach
    void start() throws Exception {
        var store = new QuizStore(data.resolve("quiz.db"));
        int owner = store.bootstrap("admin", "Quản trị", "Admin-test-only-1".toCharArray());
        ServerTestData.seedQuestions(store, owner);
        var config = new ServerConfig("127.0.0.1", 0, data.resolve("quiz.db"), key, 10, 32, 80, 80, 120);
        server = new QuizServer(config, config.tls(KEY_PASSWORD), store,new ServerWifiMonitor(ServerWifiMonitorTest.Fixture::new));
        server.proximityTracker().setTimingsForTest(15_000L, 10L, 100L);
        server.start();
        port = server.port();
    }

    @AfterEach
    void close() {
        clients.forEach(Client::close);
        if (server != null) {
            server.close();
        }
    }

    final class Client implements AutoCloseable {
        final TlsQuizGateway gateway;
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();

        Client() throws Exception {
            gateway = TlsQuizGateway.connect(new Endpoint("127.0.0.1", port), trust, KEY_PASSWORD);
            gateway.onEvent(events::add);
            clients.add(this);
        }

        JsonNode request(String type, ObjectNode payload) throws Exception {
            return gateway.request(type, payload).get(8, TimeUnit.SECONDS);
        }

        JsonNode event(String type) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
            while (System.nanoTime() < until) {
                var e = events.poll(50, TimeUnit.MILLISECONDS);
                if (e != null && e.path("type").asText().equals(type)) {
                    return e.path("payload");
                }
            }
            throw new AssertionError("Missing event " + type);
        }

        @Override
        public void close() {
            gateway.close();
        }
    }

    Client admin() throws Exception {
        var c = new Client();
        assertEquals("ADMIN", c.request("LOGIN", obj().put("username", "admin").put("password", "Admin-test-only-1")).path("role").asText());
        return c;
    }

    Client guest(String code, String name) throws Exception {
        var c = new Client();
        c.request("JOIN", obj().put("room_code", code).put("nickname", name));
        return c;
    }

    void error(Client c, String code, String type, ObjectNode p) {
        var ex = assertThrows(ExecutionException.class, () -> c.request(type, p));
        assertEquals(code, assertInstanceOf(GatewayException.class, ex.getCause()).code());
    }

    static ObjectNode calibrationPayload(int epoch, String bssid, String band) {
        ObjectNode p = obj()
            .put("config_epoch", epoch)
            .put("adapter_id", "adapter-1")
            .put("adapter_name", "Intel Wi-Fi 6")
            .put("bssid", bssid)
            .put("band", band);
        ArrayNode s1 = p.putArray("samples_1m");
        ArrayNode s5 = p.putArray("samples_5m");
        for (int i = 0; i < 22; i++) {
            s1.add(-45);
            s5.add(-61);
        }
        return p;
    }

    static ObjectNode reportPayload(int epoch, long seq, String bssid, String band, int rssi, String sessionId) {
        ObjectNode p = obj()
            .put("config_epoch", epoch)
            .put("seq", seq)
            .put("device_status", "CONNECTED")
            .put("adapter_id", "adapter-1")
            .put("adapter_name", "Intel Wi-Fi 6")
            .put("ssid", "Lab-AP")
            .put("bssid", bssid)
            .put("band", band)
            .put("channel", 36)
            .put("rssi_dbm", rssi)
            .put("signal_quality", 85);
        if (sessionId != null && !sessionId.isBlank()) {
            p.put("session_id", sessionId);
        }
        ArrayNode samples = p.putArray("samples");
        for (int i = 0; i < 5; i++) {
            samples.add(rssi);
        }
        return p;
    }

    @Test void automaticDistanceSurvivesRoamingAndDoesNotGateTheExam()throws Exception{
        var host=admin();var room=host.request("ROOM_CONFIGURE",obj().put("room_name","Automatic AP distance").put("question_count",1).put("duration_seconds",5)
            .put("proximity_enabled",true).put("anchor_bssid","aa:bb:cc:dd:ee:01").put("anchor_band","5 GHz"));
        assertFalse(room.path("proximity").path("calibration_required").asBoolean());
        var a=guest(room.path("room_code").asText(),"Moving");var b=guest(room.path("room_code").asText(),"No Wi-Fi");
        int epoch=room.path("proximity").path("config_epoch").asInt();
        var near=a.request("PROXIMITY_REPORT",reportPayload(epoch,1,"aa:bb:cc:dd:ee:01","5 GHz",-46,""));
        assertEquals(1,near.path("ap_distance").path("estimated_meters").asDouble());assertFalse(near.has("estimated_meters"));
        assertEquals("MODEL_DEFAULT",near.path("ap_distance").path("mode").asText());
        a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));
        host.request("START_GAME",obj());String session=host.request("ROOM_STATE",obj()).path("session_id").asText();
        Thread.sleep(20);
        var far=a.request("PROXIMITY_REPORT",reportPayload(epoch,2,"aa:bb:cc:dd:ee:02","5 GHz",-76,session));
        assertEquals(10,far.path("ap_distance").path("estimated_meters").asDouble());assertTrue(far.path("ready_eligible").asBoolean());
        var monitor=host.request("PROXIMITY_GET",obj());assertEquals("aa:bb:cc:dd:ee:fe",monitor.path("server_ap_distance").path("bssid").asText());
        assertEquals(1,monitor.path("server_ap_distance").path("estimated_meters").asDouble());
        for(var player:b.request("ROOM_STATE",obj()).path("players"))assertFalse(player.has("ap_distance"));
        assertEquals(2,host.request("ROOM_STATE",obj()).path("connected_player_count").asInt());
        Thread.sleep(20);var denied=a.request("PROXIMITY_REPORT",obj().put("config_epoch",epoch).put("seq",3).put("session_id",session).put("device_status","PERMISSION_DENIED"));
        assertFalse(denied.path("ap_distance").has("estimated_meters"));assertTrue(denied.path("ready_eligible").asBoolean());
    }

    @Test void refreshWorksWithoutAnchorOrCalibrationAndCannotOverwriteTheServerReading()throws Exception{
        var host=admin();var room=host.request("ROOM_CONFIGURE",obj().put("room_name","Auto refresh").put("question_count",1).put("duration_seconds",5));
        var a=guest(room.path("room_code").asText(),"No calibration");int epoch=room.path("proximity").path("config_epoch").asInt();
        a.request("PROXIMITY_REPORT",reportPayload(epoch,1,"aa:bb:cc:dd:ee:01","5 GHz",-46,""));
        String refresh=host.request("PROXIMITY_REFRESH",obj()).path("refresh").path("refresh_id").asText();assertFalse(refresh.isBlank());
        Thread.sleep(20);var payload=reportPayload(epoch,2,"aa:bb:cc:dd:ee:02","5 GHz",-76,"").put("refresh_id",refresh);
        payload.set("server_ap_distance",obj().put("estimated_meters",12345));a.request("PROXIMITY_REPORT",payload);
        var result=host.request("PROXIMITY_GET",obj());assertEquals(1,result.path("refresh").path("updated").asInt());
        assertEquals(1,result.path("server_ap_distance").path("estimated_meters").asDouble());
        error(a,"FORBIDDEN","PROXIMITY_GET",obj());
    }

    @Test void apCatalogIsHostOnlyPersistentAndTrackingWorksWithoutDistanceCalibration() throws Exception {
        var host = admin();
        var room = host.request("ROOM_CONFIGURE", obj().put("room_name", "Hành lang E1").put("question_count", 1)
            .put("duration_seconds", 5).put("proximity_enabled", false));
        var a = guest(room.path("room_code").asText(), "Client di chuyển");
        var b = guest(room.path("room_code").asText(), "Client khác");
        error(a, "FORBIDDEN", "AP_CONFIG_GET", obj());
        error(a, "FORBIDDEN", "AP_CONFIGURE", AccessPointTrackerTest.config());
        host.request("AP_CONFIGURE", AccessPointTrackerTest.config());
        assertEquals(5, host.request("AP_CONFIG_GET", obj()).path("access_points").size());
        assertTrue(Files.exists(data.resolve("quiz.db.access-points.json")));
        int epoch = room.path("proximity").path("config_epoch").asInt();
        var self = a.request("PROXIMITY_REPORT", reportPayload(epoch, 1, AccessPointTrackerTest.bssid(1), "5 GHz", -50, "")
            .put("ssid", "HUTECH E1"));
        assertEquals("AP1", self.path("access_point").path("ap_name").asText());
        assertTrue(self.path("ready_eligible").asBoolean());
        var monitor = host.request("PROXIMITY_GET", obj());
        assertEquals("AP1", monitor.path("players").get(0).path("access_point").path("ap_name").asText());
        for (var p : b.request("ROOM_STATE", obj()).path("players")) assertFalse(p.has("access_point"));
        error(host, "STALE_AP_CONFIG", "AP_CONFIGURE", AccessPointTrackerTest.config());
        a.request("READY_SET", obj().put("ready", true)); b.request("READY_SET", obj().put("ready", true));
        host.request("START_GAME", obj());
        error(host, "INVALID_STATE", "AP_CONFIGURE", AccessPointTrackerTest.config().put("revision", 2));
    }

    @Test void refreshUsesFreshReportsIsPrivateAndSurvivesGamePhasesWithoutChangingScore()throws Exception {
        var host=admin();var configured=host.request("ROOM_CONFIGURE",obj().put("room_name","Refresh TLS").put("question_count",1).put("duration_seconds",5).put("proximity_enabled",true).put("anchor_ssid","Lab-AP").put("anchor_bssid","aa:bb:cc:dd:ee:01").put("anchor_band","5 GHz").put("threshold_meters",10));
        int epoch=configured.path("proximity").path("config_epoch").asInt();String code=configured.path("room_code").asText();
        var a=guest(code,"Mẫu mới");var b=guest(code,"Chưa quyền");
        for(var c:List.of(a,b)){c.request("PROXIMITY_CALIBRATION",calibrationPayload(epoch,"aa:bb:cc:dd:ee:01","5 GHz"));c.request("PROXIMITY_REPORT",reportPayload(epoch,1,"aa:bb:cc:dd:ee:01","5 GHz",-50,""));c.request("READY_SET",obj().put("ready",true));}
        error(a,"FORBIDDEN","PROXIMITY_REFRESH",obj());
        error(a,"FORBIDDEN","PROXIMITY_GET",obj());
        var refresh=host.request("PROXIMITY_REFRESH",obj());String id=refresh.path("refresh").path("refresh_id").asText();
        assertEquals(id,host.request("PROXIMITY_REFRESH",obj()).path("refresh").path("refresh_id").asText());
        assertEquals(id,a.event("PROXIMITY_REFRESH_REQUEST").path("refresh_id").asText());b.event("PROXIMITY_REFRESH_REQUEST");
        Thread.sleep(20);
        a.request("PROXIMITY_REPORT",reportPayload(epoch,2,"aa:bb:cc:dd:ee:01","5 GHz",-61,"").put("refresh_id","forged"));
        assertEquals(0,host.request("PROXIMITY_GET",obj()).path("refresh").path("completed").asInt());
        Thread.sleep(20);a.request("PROXIMITY_REPORT",reportPayload(epoch,3,"aa:bb:cc:dd:ee:01","5 GHz",-61,"").put("refresh_id",id));
        b.request("PROXIMITY_REFRESH_STATUS",obj().put("refresh_id",id).put("reason","PERMISSION_DENIED"));
        var done=host.request("PROXIMITY_GET",obj());assertEquals("COMPLETED",done.path("refresh").path("state").asText());assertEquals(1,done.path("refresh").path("updated").asInt());
        assertEquals(2,host.request("ROOM_STATE",obj()).path("ready_count").asInt());
        for(var row:a.request("ROOM_STATE",obj()).path("players"))if(row.path("nickname").asText().equals("Chưa quyền")){assertFalse(row.has("ping_ms"));assertFalse(row.has("refresh_status"));assertFalse(row.has("estimated_meters"));}
        error(host,"RATE_LIMITED","PROXIMITY_REFRESH",obj());
        host.request("START_GAME",obj());var q=a.event("QUESTION");String answer=host.request("QUESTION_GET",obj().put("question_id",q.path("question_id").asInt())).path("correct_answer").asText();
        a.request("ANSWER",obj().put("session_id",q.path("session_id").asText()).put("question_id",q.path("question_id").asInt()).put("answer",answer));
        assertTrue(a.event("GAME_OVER").path("score").asInt()>=100);
    }
    @Test void tenClientsReceiveOneCoalescedRefreshAndLegacyClientsStayPlayable()throws Exception {
        var host=admin();var configured=host.request("ROOM_CONFIGURE",obj().put("room_name","10 Clients").put("question_count",1).put("duration_seconds",5).put("proximity_enabled",true).put("anchor_bssid","aa:bb:cc:dd:ee:01").put("anchor_band","5 GHz").put("threshold_meters",10));
        String code=configured.path("room_code").asText();var roomClients=new ArrayList<Client>();for(int i=0;i<9;i++)roomClients.add(guest(code,"Máy "+i));
        var old=new Client();var legacy=obj().put("room_code",code).put("nickname","Client cũ");legacy.putArray("client_capabilities");old.request("JOIN",legacy);
        var started=host.request("PROXIMITY_REFRESH",obj());String id=started.path("refresh").path("refresh_id").asText();assertEquals(10,started.path("refresh").path("total").asInt());
        for(var c:roomClients){assertEquals(id,c.event("PROXIMITY_REFRESH_REQUEST").path("refresh_id").asText());c.request("PROXIMITY_REFRESH_STATUS",obj().put("refresh_id",id).put("reason","NOT_REPORTING"));}
        var snap=host.request("PROXIMITY_GET",obj());assertEquals(10,snap.path("refresh").path("completed").asInt());assertEquals("COMPLETED",snap.path("refresh").path("state").asText());
        for(var p:snap.path("players"))if(p.path("nickname").asText().equals("Client cũ"))assertEquals("UNSUPPORTED",p.path("refresh_status").asText());
        assertEquals(10,host.request("ROOM_STATE",obj()).path("connected_player_count").asInt());
    }
    @Test
    void blockedHistoryWriterDoesNotBlockGameplayOrPing() throws Exception {
        var host=admin();
        var room=host.request("ROOM_CONFIGURE",obj().put("room_name","Locked DB").put("question_count",1).put("duration_seconds",5));
        var a=guest(room.path("room_code").asText(),"A");
        var b=guest(room.path("room_code").asText(),"B");
        a.request("READY_SET",obj().put("ready",true)); b.request("READY_SET",obj().put("ready",true));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+data.resolve("quiz.db"));var lock=db.createStatement()) {
            lock.execute("BEGIN IMMEDIATE");
            try {
                host.request("START_GAME",obj());
                var history=host.gateway.request("PROXIMITY_HISTORY_LIST",obj());
                a.gateway.request("PING",obj()).get(1,TimeUnit.SECONDS);
                assertFalse(history.isDone(),"History waits behind the DB writer, not on the game worker");
            } finally { lock.execute("ROLLBACK"); }
        }
    }

    @Test
    void readyAndStartGameEnforceCalibrationAnchorAndFreshDataWhileAlertsNeverChangeScore() throws Exception {
        var host = admin();
        var configured = host.request("ROOM_CONFIGURE", obj()
            .put("room_name", "Phòng Thi LAN")
            .put("question_count", 1)
            .put("duration_seconds", 5)
            .put("proximity_enabled", true).put("calibration_required",true)
            .put("anchor_ssid", "Lab-AP")
            .put("anchor_bssid", "AA:BB:CC:DD:EE:01")
            .put("anchor_band", "5 GHz")
            .put("threshold_meters", 10));

        String code = configured.path("room_code").asText();
        JsonNode proxState = configured.path("proximity");
        assertTrue(proxState.path("enabled").asBoolean());
        assertEquals("aa:bb:cc:dd:ee:01", proxState.path("anchor_bssid").asText());
        assertEquals("5 GHz", proxState.path("anchor_band").asText());
        int epoch = proxState.path("config_epoch").asInt();

        Client s1 = guest(code, "Sinh viên 1");
        Client s2 = guest(code, "Sinh viên 2");

        // Legacy or uncalibrated student cannot set Ready
        error(s1, "PROXIMITY_CALIBRATION_REQUIRED", "READY_SET", obj().put("ready", true));

        // Student reporting permission denied gets exact WIFI_PERMISSION_REQUIRED error
        s1.request("PROXIMITY_REPORT", obj()
            .put("config_epoch", epoch)
            .put("seq", 1)
            .put("device_status", "PERMISSION_DENIED"));
        error(s1, "WIFI_PERMISSION_REQUIRED", "READY_SET", obj().put("ready", true));

        // Student reporting wrong BSSID gets WRONG_ANCHOR
        Thread.sleep(15L);
        s1.request("PROXIMITY_REPORT", reportPayload(epoch, 2, "ff:ff:ff:ff:ff:ff", "5 GHz", -50, ""));
        error(s1, "WRONG_ANCHOR", "READY_SET", obj().put("ready", true));

        // Both students calibrate and send fresh reports on the anchor AP
        assertTrue(s1.request("PROXIMITY_CALIBRATION", calibrationPayload(epoch, "aa:bb:cc:dd:ee:01", "5 GHz")).path("calibrated").asBoolean());
        assertTrue(s2.request("PROXIMITY_CALIBRATION", calibrationPayload(epoch, "aa:bb:cc:dd:ee:01", "5 GHz")).path("calibrated").asBoolean());

        Thread.sleep(15L);
        s1.request("PROXIMITY_REPORT", reportPayload(epoch, 3, "aa:bb:cc:dd:ee:01", "5 GHz", -50, ""));
        s2.request("PROXIMITY_REPORT", reportPayload(epoch, 1, "aa:bb:cc:dd:ee:01", "5 GHz", -52, ""));

        s1.request("READY_SET", obj().put("ready", true));
        s2.request("READY_SET", obj().put("ready", true));
        assertEquals(2, host.request("ROOM_STATE", obj()).path("ready_count").asInt());

        // Switching to wrong AP in waiting room automatically clears Ready, invalidates calibration, and blocks START_GAME
        Thread.sleep(15L);
        s1.request("PROXIMITY_REPORT", reportPayload(epoch, 4, "aa:bb:cc:dd:ee:99", "5 GHz", -50, ""));
        assertEquals(1, host.request("ROOM_STATE", obj()).path("ready_count").asInt());
        error(host, "NOT_READY", "START_GAME", obj());

        // Recalibrate (or confirm calibration) after BSSID change, restore valid report and Ready, then start match
        s1.request("PROXIMITY_CALIBRATION", calibrationPayload(epoch, "aa:bb:cc:dd:ee:01", "5 GHz"));
        Thread.sleep(15L);
        s1.request("PROXIMITY_REPORT", reportPayload(epoch, 5, "aa:bb:cc:dd:ee:01", "5 GHz", -50, ""));
        s1.request("READY_SET", obj().put("ready", true));
        host.request("START_GAME", obj());

        // Cannot change proximity configuration while match is running
        error(host, "INVALID_STATE", "PROXIMITY_CONFIGURE", obj().put("threshold_meters", 15));

        var q = s1.event("QUESTION");
        String sessionId = q.path("session_id").asText();
        s2.event("QUESTION");

        // During the match: s1 sends 3 consecutive over-threshold reports (RSSI -78 dBm => > 10 m)
        for (int i = 0; i < 3; i++) {
            Thread.sleep(15L);
            var self = s1.request("PROXIMITY_REPORT", reportPayload(epoch, 10 + i, "aa:bb:cc:dd:ee:01", "5 GHz", -78, sessionId));
            if (i < 2) {
                assertFalse(self.path("over_threshold").asBoolean());
            } else {
                assertTrue(self.path("over_threshold").asBoolean());
                assertEquals("OVER_THRESHOLD", self.path("status").asText());
            }
        }

        // Now s1 sends 3 consecutive recovery reports (< 80% of 10m = < 8m, RSSI -52 dBm => ~2.2 m)
        for (int i = 0; i < 3; i++) {
            Thread.sleep(15L);
            var self = s1.request("PROXIMITY_REPORT", reportPayload(epoch, 20 + i, "aa:bb:cc:dd:ee:01", "5 GHz", -52, sessionId));
            if (i < 2) {
                assertTrue(self.path("over_threshold").asBoolean());
            } else {
                assertFalse(self.path("over_threshold").asBoolean());
                assertEquals("MEASURING", self.path("status").asText());
            }
        }

        // s2 loses Wi-Fi data mid-match (WIFI_DISABLED), then both answer the question correctly
        Thread.sleep(15L);
        s2.request("PROXIMITY_REPORT", obj()
            .put("config_epoch", epoch)
            .put("seq", 5)
            .put("session_id", sessionId)
            .put("device_status", "WIFI_DISABLED"));

        String correct = host.request("QUESTION_GET", obj().put("question_id", q.path("question_id").asInt())).path("correct_answer").asText();
        var ack1=s1.request("ANSWER", obj().put("session_id", sessionId).put("question_id", q.path("question_id").asInt()).put("answer", correct));
        var ack2=s2.request("ANSWER", obj().put("session_id", sessionId).put("question_id", q.path("question_id").asInt()).put("answer", correct));

        var end1 = s1.event("GAME_OVER");
        var end2 = s2.event("GAME_OVER");
        // Neither over-threshold alert nor data loss penalized points or removed players
        assertEquals(AnswerScoring.calculate(true,ack1.path("answer_elapsed_ms").asLong(),5000,0).points(), end1.path("score").asInt());
        assertEquals(AnswerScoring.calculate(true,ack2.path("answer_elapsed_ms").asLong(),5000,0).points(), end2.path("score").asInt());

        // Verify proximity history was persisted and linked by session_id
        server.proximityStore().flush();
        var historyList = host.request("PROXIMITY_HISTORY_LIST", obj());
        assertTrue(historyList.path("total").asInt() >= 1);
        var detail = host.request("PROXIMITY_HISTORY_GET", obj().put("session_id", sessionId));
        assertEquals("COMPLETED", detail.path("status").asText());
        assertTrue(detail.path("has_scoreboard").asBoolean());
        assertEquals(2, detail.path("players").size());
        assertTrue(detail.path("alerts").size() >= 3, "Expected OVER_THRESHOLD, RECOVERED_THRESHOLD, and DATA_LOST alerts");
    }

    @Test
    void abortedMatchStillPersistsProximityLogAndWriteFailureNotifiesManagerWithoutAborting() throws Exception {
        var host = admin();
        var configured = host.request("ROOM_CONFIGURE", obj()
            .put("room_name", "Phòng Hủy Giữa Chừng")
            .put("question_count", 2)
            .put("duration_seconds", 10)
            .put("proximity_enabled", true).put("calibration_required",true)
            .put("anchor_ssid", "Lab-AP")
            .put("anchor_bssid", "AA:BB:CC:DD:EE:01")
            .put("anchor_band", "5 GHz")
            .put("threshold_meters", 10));
        String code = configured.path("room_code").asText();
        int epoch = configured.path("proximity").path("config_epoch").asInt();

        Client s1 = guest(code, "SV A");
        Client s2 = guest(code, "SV B");
        for (Client s : List.of(s1, s2)) {
            s.request("PROXIMITY_CALIBRATION", calibrationPayload(epoch, "aa:bb:cc:dd:ee:01", "5 GHz"));
            s.request("PROXIMITY_REPORT", reportPayload(epoch, 1, "aa:bb:cc:dd:ee:01", "5 GHz", -49, ""));
            s.request("READY_SET", obj().put("ready", true));
        }

        // Simulate a transient DB write failure at match start: match must NOT abort, and warning is exposed to manager
        server.proximityStore().setSimulateWriteFailure(true);
        host.request("START_GAME", obj());
        var q = s1.event("QUESTION");
        String sessionId = q.path("session_id").asText();
        server.proximityStore().flush();
        Thread.sleep(60L);
        var liveSnap = host.request("PROXIMITY_GET", obj());
        assertFalse(liveSnap.path("persistence_warning").asText().isBlank(), "Manager should see persistence warning");
        assertEquals("QUESTION_ACTIVE", host.request("ROOM_STATE", obj()).path("phase").asText(), "Match must continue despite proximity DB write failure");

        // Re-enable writes and abort the match: aborted match proximity log must be saved and viewable
        server.proximityStore().setSimulateWriteFailure(false);
        host.request("ABORT_GAME", obj());
        s1.event("GAME_ABORTED");
        server.proximityStore().flush();

        var abortedDetail = host.request("PROXIMITY_HISTORY_GET", obj().put("session_id", sessionId));
        assertEquals("ABORTED", abortedDetail.path("status").asText());
        assertFalse(abortedDetail.path("has_scoreboard").asBoolean(), "Aborted match has no scoreboard in matches table");
        assertEquals(2, abortedDetail.path("players").size());
    }

    @Test
    void rbacIsolationSpoofedPlayerIdRejectionAndStaleOrDuplicateSequenceChecks() throws Exception {
        var adminClient = admin();
        var teacherA = new Client();
        teacherA.request("REGISTER", obj().put("username", "teacher.a").put("display_name", "GV A").put("password", "Teacher-pass-1"));
        teacherA.request("LOGIN", obj().put("username", "teacher.a").put("password", "Teacher-pass-1"));
        for (int i = 0; i < 3; i++) {
            teacherA.request("QUESTION_CREATE", obj()
                .put("content", "Câu hỏi GV A " + i)
                .put("option_a", "A").put("option_b", "B").put("option_c", "C").put("option_d", "D")
                .put("correct_answer", "A").put("category", "LAN"));
        }

        var teacherB = new Client();
        teacherB.request("REGISTER", obj().put("username", "teacher.b").put("display_name", "GV B").put("password", "Teacher-pass-1"));
        teacherB.request("LOGIN", obj().put("username", "teacher.b").put("password", "Teacher-pass-1"));

        var roomState = teacherA.request("ROOM_CONFIGURE", obj()
            .put("room_name", "Phòng GV A")
            .put("question_count", 1)
            .put("duration_seconds", 5)
            .put("proximity_enabled", true).put("calibration_required",true)
            .put("anchor_ssid", "AP-A")
            .put("anchor_bssid", "11:22:33:44:55:66")
            .put("anchor_band", "5 GHz")
            .put("threshold_meters", 10));
        String code = roomState.path("room_code").asText();
        int epoch = roomState.path("proximity").path("config_epoch").asInt();

        // Teacher B cannot configure or view Teacher A's live proximity monitor
        error(teacherB, "FORBIDDEN", "PROXIMITY_CONFIGURE", obj().put("threshold_meters", 12));
        error(teacherB, "FORBIDDEN", "PROXIMITY_GET", obj());

        // Admin CAN view Teacher A's live proximity monitor
        assertEquals(code, adminClient.request("PROXIMITY_GET", obj()).path("room_code").asText());

        Client student1 = guest(code, "SV 1");
        Client student2 = guest(code, "SV 2");
        String s2Id = teacherA.request("PROXIMITY_GET", obj()).path("players").get(1).path("player_id").asText();

        // Student cannot call manager-only PROXIMITY_GET or PROXIMITY_HISTORY_LIST or PROXIMITY_CONFIGURE
        error(student1, "FORBIDDEN", "PROXIMITY_GET", obj());
        error(student1, "FORBIDDEN", "PROXIMITY_HISTORY_LIST", obj());
        error(student1, "FORBIDDEN", "PROXIMITY_CONFIGURE", obj().put("threshold_meters", 20));

        // Student 1 tries to spoof Student 2's player_id in payload; server binds to Student 1's socket
        ObjectNode spoofedCal = calibrationPayload(epoch, "11:22:33:44:55:66", "5 GHz").put("player_id", s2Id);
        student1.request("PROXIMITY_CALIBRATION", spoofedCal);
        var monitorAfterSpoof = teacherA.request("PROXIMITY_GET", obj());
        for (JsonNode p : monitorAfterSpoof.path("players")) {
            if (p.path("player_id").asText().equals(s2Id)) {
                assertFalse(p.path("calibrated").asBoolean(), "Spoofed player_id must not calibrate another student");
            }
        }

        // Stale epoch, non-increasing sequence, and rate limit checks
        error(student1, "STALE_CONFIG_EPOCH", "PROXIMITY_REPORT",
            reportPayload(epoch - 1, 1, "11:22:33:44:55:66", "5 GHz", -50, ""));

        student1.request("PROXIMITY_REPORT", reportPayload(epoch, 10, "11:22:33:44:55:66", "5 GHz", -50, ""));
        Thread.sleep(15L);
        error(student1, "STALE_SEQUENCE", "PROXIMITY_REPORT",
            reportPayload(epoch, 10, "11:22:33:44:55:66", "5 GHz", -50, ""));

        // Host selecting anchor from candidate student report & recalibrate_all
        Thread.sleep(15L);
        student2.request("PROXIMITY_REPORT", reportPayload(epoch, 1, "66:55:44:33:22:11", "2.4 GHz", -48, ""));
        var updatedFromCandidate = teacherA.request("PROXIMITY_CONFIGURE", obj()
            .put("proximity_enabled", true).put("calibration_required",true)
            .put("from_player_id", s2Id)
            .put("threshold_meters", 12));
        assertEquals("66:55:44:33:22:11", updatedFromCandidate.path("proximity").path("anchor_bssid").asText());
        assertEquals("2.4 GHz", updatedFromCandidate.path("proximity").path("anchor_band").asText());
    }

    @Test
    void sqliteV1ToV2MigrationPreservesExistingDataAndMarksInterruptedSessions() throws Exception {
        Path dbFile = data.resolve("migration-v1.db");
        Path existingProdDb = Path.of("..", "dist", "QuizBattle", "Server", "data", "quiz.db").normalize();
        if (Files.isRegularFile(existingProdDb)) {
            Files.copy(existingProdDb, dbFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // Create a genuine schema v1 database
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                 Statement s = c.createStatement()) {
                s.execute("PRAGMA user_version = 1");
                s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT NOT NULL UNIQUE,normalized_name TEXT NOT NULL,display_name TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('ADMIN','TEACHER')),salt TEXT NOT NULL,hash TEXT NOT NULL,iterations INTEGER NOT NULL,credential_version INTEGER NOT NULL DEFAULT 1,is_active INTEGER NOT NULL DEFAULT 1,must_change_password INTEGER NOT NULL DEFAULT 1,created_at_utc TEXT NOT NULL)");
                s.execute("CREATE TABLE questions(id INTEGER PRIMARY KEY,owner_user_id INTEGER NOT NULL REFERENCES users(id),content TEXT NOT NULL,normalized_content TEXT NOT NULL,option_a TEXT NOT NULL,option_b TEXT NOT NULL,option_c TEXT NOT NULL,option_d TEXT NOT NULL,correct_answer TEXT NOT NULL CHECK(correct_answer IN ('A','B','C','D')),category TEXT NOT NULL,is_active INTEGER NOT NULL,created_at_utc TEXT NOT NULL,updated_at_utc TEXT NOT NULL)");
                s.execute("CREATE TABLE matches(session_id TEXT PRIMARY KEY,host_user_id INTEGER NOT NULL REFERENCES users(id),room_name TEXT NOT NULL,question_count INTEGER NOT NULL,player_count INTEGER NOT NULL,started_at_utc TEXT NOT NULL,ended_at_utc TEXT NOT NULL)");
                s.execute("CREATE TABLE match_results(session_id TEXT NOT NULL REFERENCES matches(session_id),player_id TEXT NOT NULL,nickname TEXT NOT NULL,avatar_id TEXT NOT NULL,score INTEGER NOT NULL,correct_count INTEGER NOT NULL,rank INTEGER NOT NULL,connected INTEGER NOT NULL,PRIMARY KEY(session_id,player_id))");
                s.execute("INSERT INTO users(username,normalized_name,display_name,role,salt,hash,iterations,is_active,must_change_password,credential_version,created_at_utc) VALUES('admin','admin quan tri','Admin','ADMIN','aa','bb',600000,1,0,1,'2026-01-01T00:00:00Z')");
            }
        }

        int usersBefore;
        int questionsBefore;
        int matchesBefore;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement s = c.createStatement()) {
            usersBefore = countRows(s, "SELECT COUNT(*) FROM users");
            questionsBefore = countRows(s, "SELECT COUNT(*) FROM questions");
            matchesBefore = countRows(s, "SELECT COUNT(*) FROM matches");
        }

        // Open QuizStore -> triggers transactional migration to schema 2
        try (QuizStore ignored = new QuizStore(dbFile);
             ProximityStore migratedProxStore = new ProximityStore(dbFile, 64, w -> {})) {
            migratedProxStore.enqueueSnapshot(new ProximityStore.SessionSnapshot(
                "lingering-session",
                1,
                "Phòng dở dang",
                "ABC123",
                true,
                "AP",
                "aa:bb:cc:dd:ee:01",
                "5 GHz",
                10.0,
                "RUNNING",
                "2026-10-26T00:00:00Z",
                "2026-10-26T00:00:10Z",
                "",
                List.of(new ProximityStore.PlayerSummaryRow("p1", "SV 1", "quizling-01", true, "Intel Wi-Fi", 2.3, 1.8, 3.2, 2.5, "MEASURING", 0L, 0, 0, "2026-10-26T00:00:10Z")),
                List.of()
            ));
            migratedProxStore.flush();
        }

        // Reopen QuizStore + ProximityStore -> lingering RUNNING session must be marked INTERRUPTED on startup
        try (QuizStore ignored = new QuizStore(dbFile);
             ProximityStore reopenedProxStore = new ProximityStore(dbFile, 64, w -> {});
             Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement s = c.createStatement()) {
            assertEquals(5, countRows(s, "PRAGMA user_version"));
            assertEquals(usersBefore, countRows(s, "SELECT COUNT(*) FROM users"));
            assertEquals(questionsBefore, countRows(s, "SELECT COUNT(*) FROM questions"));
            assertEquals(matchesBefore, countRows(s, "SELECT COUNT(*) FROM matches"));

            var detail = reopenedProxStore.getHistoryDetail(1, true, "lingering-session");
            assertEquals("INTERRUPTED", detail.path("status").asText());
        }
    }

    @Test
    void tenClientsWithProximityMonitoringStaleDataExpirationAndLegacyClientCompatibility() throws Exception {
        var host = admin();

        // 1. Legacy room (proximity disabled): legacy clients can Ready and play without calibration
        String legacyCode = host.request("ROOM_CONFIGURE", obj()
            .put("room_name", "Phòng Tắt Giám Sát")
            .put("question_count", 1)
            .put("duration_seconds", 5)
            .put("proximity_enabled", false)).path("room_code").asText();
        Client l1 = guest(legacyCode, "Legacy 1");
        Client l2 = guest(legacyCode, "Legacy 2");
        l1.request("READY_SET", obj().put("ready", true));
        l2.request("READY_SET", obj().put("ready", true));
        l1.close();
        l2.close();

        // 2. Enable proximity with fast stale timeout (250ms) and test 10 concurrent students
        server.proximityTracker().setTimingsForTest(15_000L, 5L, 80L);
        var configured = host.request("PROXIMITY_CONFIGURE", obj()
            .put("proximity_enabled", true).put("calibration_required",true)
            .put("anchor_ssid", "Hall-AP")
            .put("anchor_bssid", "AA:BB:CC:11:22:33")
            .put("anchor_band", "5 GHz")
            .put("threshold_meters", 10));
        int epoch = configured.path("proximity").path("config_epoch").asInt();
        String code = configured.path("room_code").asText();

        List<Client> students = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Client c = guest(code, "SV " + i);
            c.request("PROXIMITY_CALIBRATION", calibrationPayload(epoch, "aa:bb:cc:11:22:33", "5 GHz"));
            c.request("PROXIMITY_REPORT", reportPayload(epoch, 1, "aa:bb:cc:11:22:33", "5 GHz", -50 - (i % 5), ""));
            c.request("READY_SET", obj().put("ready", true));
            students.add(c);
        }
        assertEquals(10, host.request("ROOM_STATE", obj()).path("ready_count").asInt());

        // Shorten freshness only after TLS setup; connection speed must not determine the initial count.
        server.proximityTracker().setTimingsForTest(250L,5L,80L);
        // Wait for data to expire (> 250ms): tick() automatically clears Ready in waiting room and marks NO_DATA
        Thread.sleep(360L);
        assertEquals(0, host.request("ROOM_STATE", obj()).path("ready_count").asInt(), "Stale proximity data must clear Ready in waiting room");
        var expiredSnap = host.request("PROXIMITY_GET", obj());
        assertEquals("NO_DATA", expiredSnap.path("players").get(0).path("status").asText());

        // Restore longer timeout, send fresh reports from all 10 clients, Ready up, and complete a 10-client round
        server.proximityTracker().setTimingsForTest(15_000L, 5L, 80L);
        for (int i = 0; i < 10; i++) {
            Client c = students.get(i);
            c.request("PROXIMITY_REPORT", reportPayload(epoch, 2, "aa:bb:cc:11:22:33", "5 GHz", -51 - (i % 4), ""));
            c.request("READY_SET", obj().put("ready", true));
        }
        host.request("START_GAME", obj());
        for (int i = 0; i < 10; i++) {
            Client c = students.get(i);
            var q = c.event("QUESTION");
            String sid = q.path("session_id").asText();
            c.request("PROXIMITY_REPORT", reportPayload(epoch, 3, "aa:bb:cc:11:22:33", "5 GHz", -52 - (i % 4), sid));
            assertTrue(c.request("ANSWER", obj().put("session_id", sid).put("question_id", q.path("question_id").asInt()).put("answer", "A")).path("accepted").asBoolean());
        }
        for (Client c : students) {
            var end = c.event("GAME_OVER");
            assertEquals(10, end.path("ranking").size());
            assertTrue(end.path("result_saved").asBoolean());
        }
    }

    private static int countRows(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
