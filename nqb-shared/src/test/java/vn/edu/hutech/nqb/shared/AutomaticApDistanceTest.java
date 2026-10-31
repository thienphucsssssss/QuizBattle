package vn.edu.hutech.nqb.shared;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticApDistanceTest {
    private static final String AP="aa:bb:cc:dd:ee:01";
    @Test void estimatesWithoutSurveyOrCalibrationAndRejectsOutlierWithMedian(){
        var model=new AutomaticApDistance();model.accept("adapter","E1",AP,"5 GHz",List.of(-46,-46,-99,-46,-46),0);
        var near=model.snapshot(true,1);assertEquals(1,near.path("estimated_meters").asDouble());assertFalse(near.path("calibrated").asBoolean());
        model.accept("adapter","E1",AP,"5 GHz",List.of(-76,-76,-76,-76,-76),2);
        assertEquals(10,model.snapshot(true,3).path("estimated_meters").asDouble());
    }
    @Test void roamingAdapterAndBandChangesDiscardPreviousSamples(){
        var model=new AutomaticApDistance();model.accept("a","E1",AP,"5 GHz",List.of(-76,-76,-76,-76,-76),0);
        model.accept("a","E1","aa:bb:cc:dd:ee:02","5 GHz",List.of(-46),1);
        assertEquals(1,model.snapshot(true,2).path("estimated_meters").asDouble());
        model.accept("b","E1","aa:bb:cc:dd:ee:02","5 GHz",List.of(-76),3);
        assertEquals(10,model.snapshot(true,4).path("estimated_meters").asDouble());
        model.accept("b","E1","aa:bb:cc:dd:ee:02","2.4 GHz",List.of(-40),5);
        assertEquals(1,model.snapshot(true,6).path("estimated_meters").asDouble());assertEquals(1,model.snapshot(true,6).path("sample_count").asInt());
    }
    @Test void staleOrDeniedReadingsNeverExposeAnOldDistance(){
        var model=new AutomaticApDistance();model.accept("a","E1",AP,"5 GHz",List.of(-76,-76,-76,-76,-76),0);
        assertFalse(model.snapshot(true,16_000_000_000L).has("estimated_meters"));assertFalse(model.snapshot(false,1).has("estimated_meters"));
        model.accept("a","E1",AP,"5 GHz",List.of(-46),17_000_000_000L);
        assertEquals(1,model.snapshot(true,17_000_000_001L).path("estimated_meters").asDouble());
        model.unavailable("PERMISSION_DENIED",18_000_000_000L);
        assertFalse(model.snapshot(true,18_000_000_001L).has("estimated_meters"));assertEquals("PERMISSION_DENIED",model.snapshot(true,18_000_000_001L).path("status").asText());
    }
    @Test void invalidInputCannotPoisonTheLastValidSample(){
        var model=new AutomaticApDistance();model.accept("a","E1",AP,"5 GHz",List.of(-46),0);
        assertThrows(IllegalArgumentException.class,()->model.accept("a","E1",AP,"unknown",List.of(-50),1));
        assertThrows(IllegalArgumentException.class,()->model.accept("a","E1",AP,"5 GHz",List.of(0),1));
        assertEquals(1,model.snapshot(true,2).path("estimated_meters").asDouble());
    }
}
