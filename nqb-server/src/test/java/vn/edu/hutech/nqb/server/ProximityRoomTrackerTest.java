package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class ProximityRoomTrackerTest {
    private static final String AP="aa:bb:cc:dd:ee:01";
    private final ProximityRoomTracker tracker=new ProximityRoomTracker();
    private int epoch;
    private long sequence;
    private long time=TimeUnit.SECONDS.toNanos(1);

    @BeforeEach void setup() {
        tracker.configure(obj().put("enabled",true).put("anchor_bssid",AP).put("anchor_band","5 GHz"),true);
        tracker.onPlayerJoin("p","Student","avatar");
        epoch=tracker.configEpoch();
        calibrate();
    }
    private void calibrate() {
        tracker.handleCalibration("p",ProximityServerIntegrationTest.calibrationPayload(epoch,AP,"5 GHz"),time);
    }
    private ObjectNode report(int rssi, boolean running) {
        time+=TimeUnit.SECONDS.toNanos(5);
        var payload=ProximityServerIntegrationTest.reportPayload(epoch,++sequence,AP,"5 GHz",rssi,running?"match":"");
        return tracker.handleReport("p",obj().set("payload",payload),running,"match",time);
    }
    private ObjectNode self() {
        return (ObjectNode)tracker.buildRoomStateSection("p",false,time).get("self");
    }
    private void error(String code, ObjectNode payload, boolean running) {
        var failure=assertThrows(Failure.class,()->tracker.handleReport("p",obj().set("payload",payload),running,"match",time));
        assertEquals(code,failure.code);
    }
    @Test void invalidReportCannotMutateAcceptedStateOrConsumeSequence() {
        report(-50,false);
        time+=TimeUnit.SECONDS.toNanos(5);
        var valid=ProximityServerIntegrationTest.reportPayload(epoch,2,AP,"5 GHz",-52,"");
        error("INVALID_INPUT",valid.deepCopy().put("adapter_id",""),false);
        var tooMany=valid.deepCopy(); tooMany.withArray("samples").add(-52);
        error("INVALID_INPUT",tooMany,false);
        error("INVALID_INPUT",valid.deepCopy().put("rssi_dbm",4294967244L),false);
        error("INVALID_INPUT",valid.deepCopy().put("seq",2.5),false);
        assertTrue(self().path("ready_eligible").asBoolean());
        tracker.handleReport("p",obj().set("payload",valid),false,"",time);
        assertTrue(self().path("ready_eligible").asBoolean());
    }
    @Test void reportsRequireCurrentEpochAndRunningSession() {
        var valid=ProximityServerIntegrationTest.reportPayload(epoch,1,AP,"5 GHz",-52,"match");
        var absent=valid.deepCopy(); absent.remove("config_epoch");
        error("STALE_CONFIG_EPOCH",absent,true);
        error("INVALID_INPUT",valid.deepCopy().put("config_epoch",0),true);
        error("WRONG_SESSION",valid.deepCopy().put("session_id",""),true);
        error("WRONG_SESSION",valid.deepCopy().put("session_id","old"),true);
        tracker.handleReport("p",obj().set("payload",valid),true,"match",time);
    }
    @Test void reportLimitIsFiveSecondsAndRejectedReportDoesNotExtendTheWindow() {
        report(-50,false);
        var next=ProximityServerIntegrationTest.reportPayload(epoch,2,AP,"5 GHz",-52,"");
        time+=TimeUnit.MILLISECONDS.toNanos(4999);
        error("RATE_LIMITED",next,false);
        time+=TimeUnit.MILLISECONDS.toNanos(1);
        tracker.handleReport("p",obj().set("payload",next),false,"",time);
    }
    @Test void expiryIsOneTransitionInsteadOfTwentyUpdatesPerSecond() {
        report(-50,false);
        time+=TimeUnit.SECONDS.toNanos(16);
        assertTrue(tracker.tick(false,"",1,"room","code","",null,time));
        assertFalse(tracker.tick(false,"",1,"room","code","",null,time+1));
        assertFalse(self().has("estimated_meters"));
        assertFalse(self().path("ready_eligible").asBoolean());
        assertTrue(report(-50,false).path("ready_eligible").asBoolean());
    }
    @Test void dataLossBreaksThresholdAndRecoverySequences() {
        assertFalse(report(-75,true).path("over_threshold").asBoolean());
        assertFalse(report(-75,true).path("over_threshold").asBoolean());
        time+=TimeUnit.SECONDS.toNanos(5);
        var lost=obj().put("config_epoch",epoch).put("seq",++sequence).put("device_status","PERMISSION_DENIED").put("session_id","match");
        tracker.handleReport("p",obj().set("payload",lost),true,"match",time);
        assertFalse(report(-75,true).path("over_threshold").asBoolean());
        report(-75,true);
        assertTrue(report(-75,true).path("over_threshold").asBoolean());
        report(-50,true); report(-50,true);
        time+=TimeUnit.SECONDS.toNanos(16);
        tracker.tick(true,"match",1,"room","code","",null,time);
        assertTrue(report(-50,true).path("over_threshold").asBoolean());
        report(-50,true);
        assertFalse(report(-50,true).path("over_threshold").asBoolean());
    }
    @Test void recalibrationRequiresAFreshMeasurement() {
        report(-50,false);
        assertNull(tracker.checkReadyEligibility("p",time));
        calibrate();
        assertEquals("PROXIMITY_DATA_STALE",tracker.checkReadyEligibility("p",time).code);
    }
}
