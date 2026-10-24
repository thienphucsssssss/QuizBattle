package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SpeedScoringTest {
    private static long seconds(long value) { return TimeUnit.SECONDS.toNanos(value); }

    @Test void fasterCorrectAnswersReceiveMorePoints() {
        assertEquals(95,SpeedScoring.points(true,seconds(1),seconds(10)));
        assertEquals(75,SpeedScoring.points(true,seconds(5),seconds(10)));
        assertEquals(55,SpeedScoring.points(true,seconds(9),seconds(10)));
    }
    @Test void correctAwardsStayBetweenFiftyAndOneHundred() {
        assertEquals(100,SpeedScoring.points(true,0,seconds(10)));
        assertEquals(50,SpeedScoring.points(true,seconds(10)-1,seconds(10)));
    }
    @Test void wrongMissingAndExpiredAnswersReceiveZero() {
        assertEquals(0,SpeedScoring.points(false,0,seconds(10)));
        assertEquals(0,SpeedScoring.points(true,-1,seconds(10)));
        assertEquals(0,SpeedScoring.points(true,seconds(10),seconds(10)));
        assertEquals(0,SpeedScoring.points(true,seconds(11),seconds(10)));
    }
    @Test void bonusUsesTheConfiguredQuestionDuration() {
        assertEquals(90,SpeedScoring.points(true,seconds(1),seconds(5)));
        assertEquals(90,SpeedScoring.points(true,seconds(24),seconds(120)));
        assertEquals(75,SpeedScoring.points(true,seconds(60),seconds(120)));
    }
    @Test void awardsRoundToWholePoints() {
        assertEquals(88,SpeedScoring.points(true,TimeUnit.MILLISECONDS.toNanos(2500),seconds(10)));
        assertEquals(63,SpeedScoring.points(true,TimeUnit.MILLISECONDS.toNanos(7500),seconds(10)));
    }
    @Test void invalidDurationsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->SpeedScoring.points(true,0,0));
        assertThrows(IllegalArgumentException.class,()->SpeedScoring.points(true,0,-1));
    }
}
