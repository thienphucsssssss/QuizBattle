package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnswerMetaTest {
    @Test void timeIsCompactVietnameseAndDoesNotInventAMissingAnswer() {
        assertEquals("",AnswerMeta.time(-1));assertEquals("0,00 s",AnswerMeta.time(0));
        assertEquals("1,23 s",AnswerMeta.time(1234));assertEquals("12,50 s",AnswerMeta.time(12500));
    }
    @Test void streakOnlyAppearsAfterTwoConsecutiveCorrectAnswers() {
        assertEquals("",AnswerMeta.streak(0));assertEquals("",AnswerMeta.streak(1));assertEquals("Chuỗi ×2",AnswerMeta.streak(2));
    }
}
