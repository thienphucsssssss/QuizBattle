package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.edu.hutech.nqb.shared.WifiSurvey;
import vn.edu.hutech.nqb.shared.fingerprint.FingerprintDemo;
import static org.junit.jupiter.api.Assertions.*;
import vn.edu.hutech.nqb.server.Wire.Failure;

class FloorTrackerTest {
    private FloorTracker tracker(){var sample=FingerprintDemo.create();var t=new FloorTracker();t.configure(FloorTracker.prepare(WifiSurvey.Bundle.of(sample.bundle().areas(),sample.bundle().runs().stream().filter(r->!sample.heldOut().contains(r.id())).toList())));return t;}
    private com.fasterxml.jackson.databind.node.ObjectNode report(FloorTracker tracker,int seq,int run,int scan){
        var p=Wire.obj().put("epoch",tracker.config().path("epoch").asText()).put("sequence",seq);p.set("scan",WifiSurvey.JSON.valueToTree(FingerprintDemo.create().bundle().runs().get(run).scans().get(scan)));return p;
    }
    @Test void expiresHidesRejectedAndResumesWithoutDroppingPlayer(){
        var t=tracker();long now=TimeUnit.SECONDS.toNanos(1);t.report("one",report(t,1,5,0),now);
        var first=t.player("one","Alice",true,now);assertTrue(first.path("located").asBoolean());assertTrue(first.has("distance_m"));
        var expired=t.player("one","Alice",true,now+FloorTracker.EXPIRY+1);assertEquals("STALE",expired.path("status").asText());assertFalse(expired.has("x"));
        t.report("one",report(t,2,7,0),now+TimeUnit.SECONDS.toNanos(5));assertFalse(t.player("one","Alice",true,now+TimeUnit.SECONDS.toNanos(5)).has("distance_m"));
        t.disconnect("one");assertEquals("DISCONNECTED",t.player("one","Alice",false,now).path("status").asText());
        t.report("one",report(t,1,5,1),now+TimeUnit.SECONDS.toNanos(10));assertTrue(t.player("one","Alice",true,now+TimeUnit.SECONDS.toNanos(10)).path("located").asBoolean());
    }
    @Test void refusesReplayWrongEpochAndMalformedSamples(){
        var t=tracker();long now=TimeUnit.SECONDS.toNanos(1);var sample=report(t,1,5,0);t.report("one",sample,now);
        assertThrows(Failure.class,()->t.report("one",sample,now+TimeUnit.SECONDS.toNanos(5)));
        assertThrows(Failure.class,()->t.report("one",report(t,2,5,0),now+TimeUnit.SECONDS.toNanos(5)));
        assertThrows(Failure.class,()->t.report("one",report(t,2,5,1).put("epoch","wrong"),now+TimeUnit.SECONDS.toNanos(5)));
        assertThrows(IllegalArgumentException.class,()->t.report("one",report(t,2,5,1).put("scan","bad"),now+TimeUnit.SECONDS.toNanos(5)));
        t.clear();assertFalse(t.config().path("enabled").asBoolean());assertFalse(t.player("one","Alice",true,now).has("distance_m"));
    }
}
