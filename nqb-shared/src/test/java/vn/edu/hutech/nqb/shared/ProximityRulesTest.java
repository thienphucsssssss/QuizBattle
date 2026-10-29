package vn.edu.hutech.nqb.shared;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProximityRulesTest {
    @Test void floatingPointNoiseAtCalibrationBoundaryIsNotExtrapolation(){
        assertFalse(ProximityRules.isExtrapolated(5.000000000000001));assertFalse(ProximityRules.isExtrapolated(.9999999999999999));
        assertTrue(ProximityRules.isExtrapolated(5.001));assertTrue(ProximityRules.isExtrapolated(.999));
    }

    @Test void malformedBandsAndNonFiniteInputsNeverBecomeDistanceReadings() {
        assertEquals("5 GHz",ProximityRules.normalizeBand("5GHz"));
        assertEquals("2.4 GHz",ProximityRules.normalizeBand("2,4 GHz"));
        for(String invalid:List.of("56GHz","16GHz","2.4 garbage","5GHz extra"))assertEquals("",ProximityRules.normalizeBand(invalid));
        assertThrows(IllegalArgumentException.class,()->ProximityRules.estimateDistanceMeters(-45,Double.NaN,-60));
        assertThrows(IllegalArgumentException.class,()->ProximityRules.estimateDistanceMeters(-45,2,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->ProximityRules.estimateDistanceMeters(Double.POSITIVE_INFINITY,2,-60));
    }

    @Test
    void logDistanceCalibrationAndEstimationFollowFormulas() {
        // Suppose RSSI_1m = -45 dBm, RSSI_5m = -59 dBm -> diff = 14 dB
        // n = 14 / (10 * log10(5)) = 14 / 6.989700043 = 2.002947...
        List<Integer> s1 = Collections.nCopies(25, -45);
        List<Integer> s5 = Collections.nCopies(25, -59);

        var eval = ProximityRules.evaluateCalibration(s1, s5);
        assertTrue(eval.valid(), eval.message());
        assertEquals(25, eval.step1m().validSampleCount());
        assertEquals(-45.0, eval.step1m().medianRssiDbm(), 1e-6);
        assertEquals(0.0, eval.step1m().madDb(), 1e-6);
        assertEquals(-59.0, eval.step5m().medianRssiDbm(), 1e-6);
        assertEquals(2.0029, eval.pathLossN(), 0.001);

        // At 1m (-45 dBm), d = 1.0 m -> "≈ 1 m", not extrapolated
        double d1 = ProximityRules.estimateDistanceMeters(eval.step1m().medianRssiDbm(), eval.pathLossN(), -45.0);
        assertEquals(1.0, d1, 1e-6);
        assertFalse(ProximityRules.isExtrapolated(d1));
        assertEquals("≈ 1 m", ProximityRules.formatEstimatedDistance(d1));

        // At 5m (-59 dBm), d = 5.0 m -> "≈ 5 m", not extrapolated
        double d5 = ProximityRules.estimateDistanceMeters(eval.step1m().medianRssiDbm(), eval.pathLossN(), -59.0);
        assertEquals(5.0, d5, 1e-4);
        assertFalse(ProximityRules.isExtrapolated(d5));
        assertEquals("≈ 5 m", ProximityRules.formatEstimatedDistance(d5));

        // At -65 dBm -> d = 10^(( -45 - (-65) ) / (10 * 2.002947)) = 10^(20 / 20.02947) ≈ 9.966 m -> "≈ 10 m", extrapolated
        double d10 = ProximityRules.estimateDistanceMeters(eval.step1m().medianRssiDbm(), eval.pathLossN(), -65.0);
        assertEquals(9.97, d10, 0.05);
        assertTrue(ProximityRules.isExtrapolated(d10));
        assertEquals("≈ 10 m", ProximityRules.formatEstimatedDistance(d10));
    }

    @Test
    void rejectsInsufficientSamplesExcessiveMadAndOutOfBoundsExponent() {
        List<Integer> tooFew = Collections.nCopies(19, -45);
        List<Integer> good5m = Collections.nCopies(20, -60);
        assertFalse(ProximityRules.evaluateCalibration(tooFew, good5m).valid());

        // Out-of-range RSSI values (< -100 or > -15) are filtered out
        List<Integer> withInvalid = new ArrayList<>(Collections.nCopies(15, -45));
        withInvalid.addAll(List.of(0, -5, -120, -200, 99));
        assertFalse(ProximityRules.evaluateCalibration(withInvalid, good5m).valid());

        // High MAD (> 6 dB): 12 samples at -40, 12 samples at -56 -> median = -48, deviations all 8 dB > 6 dB
        List<Integer> highMad = new ArrayList<>();
        highMad.addAll(Collections.nCopies(12, -40));
        highMad.addAll(Collections.nCopies(12, -56));
        var badMadEval = ProximityRules.evaluateCalibration(highMad, good5m);
        assertFalse(badMadEval.valid());
        assertEquals(8.0, badMadEval.step1m().madDb(), 1e-6);

        // Exponent n < 1.0 (e.g. 1m = -50, 5m = -53 -> diff = 3 dB -> n = 0.43 < 1.0)
        assertFalse(ProximityRules.evaluateCalibration(Collections.nCopies(20, -50), Collections.nCopies(20, -53)).valid());

        // Exponent n > 6.0 (e.g. 1m = -35, 5m = -85 -> diff = 50 dB -> n = 7.15 > 6.0)
        assertFalse(ProximityRules.evaluateCalibration(Collections.nCopies(20, -35), Collections.nCopies(20, -85)).valid());

        // Inverted signal (1m weaker than 5m)
        assertFalse(ProximityRules.evaluateCalibration(Collections.nCopies(20, -65), Collections.nCopies(20, -50)).valid());
    }
}
