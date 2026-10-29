package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnswerScoringTest {
    @Test void speedBonusFallsWithElapsedTimeAndStaysInRange() {
        assertEquals(200,AnswerScoring.calculate(true,0,10000,0).points());
        assertEquals(150,AnswerScoring.calculate(true,5000,10000,0).points());
        assertEquals(100,AnswerScoring.calculate(true,9999,10000,0).points());
        int previous=201;
        for(long time=0;time<10000;time++){
            var score=AnswerScoring.calculate(true,time,10000,0);
            assertTrue(score.points()>=100&&score.points()<=200);
            assertTrue(score.points()<=previous);previous=score.points();
        }
    }
    @Test void streakStartsOnSecondCorrectAnswerAndBonusIsCapped() {
        assertEquals(new AnswerScoring.Round(150,50,0,1),AnswerScoring.calculate(true,5000,10000,0));
        assertEquals(new AnswerScoring.Round(170,50,20,2),AnswerScoring.calculate(true,5000,10000,1));
        assertEquals(new AnswerScoring.Round(250,50,100,20),AnswerScoring.calculate(true,5000,10000,19));
    }
    @Test void wrongMissingAndExpiredAnswersEarnNothingAndBreakStreak() {
        for(var value:new AnswerScoring.Round[]{AnswerScoring.calculate(false,100,10000,4),AnswerScoring.calculate(false,-1,10000,4),AnswerScoring.calculate(true,10000,10000,4),AnswerScoring.calculate(true,-1,10000,4)})assertEquals(new AnswerScoring.Round(0,0,0,0),value);
        assertThrows(IllegalArgumentException.class,()->AnswerScoring.calculate(true,0,0,0));
        assertThrows(IllegalArgumentException.class,()->AnswerScoring.calculate(true,0,10000,-1));
    }
}
