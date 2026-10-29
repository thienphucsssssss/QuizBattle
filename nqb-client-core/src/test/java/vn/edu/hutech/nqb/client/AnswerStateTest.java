package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnswerStateTest {
    @Test void serverSnapshotRestoresRecordedAnswerEvenAtDeadlineWithoutSubmittingAgain(){
        var state=new AnswerState(8,0,0);state.restoreRecorded("C");
        assertEquals(AnswerState.Status.RECORDED,state.status());assertEquals("C",state.answer());
        assertFalse(state.submit("A",1));assertThrows(IllegalArgumentException.class,()->state.restoreRecorded("X"));
    }
    @Test void locksFirstChoiceAndOnlyAckRecordsIt() {
        var state = new AnswerState(25, 1000, 0);
        assertTrue(state.submit("B", 1));
        assertEquals(AnswerState.Status.SENDING, state.status());
        assertFalse(state.submit("C", 2)); assertEquals("B", state.answer());
        state.acknowledge(25, true); assertEquals(AnswerState.Status.RECORDED, state.status());
    }
    @Test void ignoresWrongQuestionAck() {
        var state = new AnswerState(25, 1000, 0); state.submit("A", 1);
        state.acknowledge(24, true); assertEquals(AnswerState.Status.SENDING, state.status());
    }
    @Test void equalDeadlineIsExpiredAndUnsolicitedAckCannotRecord() {
        var state = new AnswerState(1, 1000, 0);
        assertFalse(state.submit("A", 1_000_000_000L));
        state.acknowledge(1, true); assertEquals(AnswerState.Status.EXPIRED, state.status());
        assertEquals(0, state.remainingMillis(1_000_000_001L));
    }
    @Test void lateAckIsAuthoritativeForAnAlreadySubmittedChoice() {
        var state = new AnswerState(1, 1000, 0); state.submit("D", 1);
        state.expire(2_000_000_000L); state.acknowledge(1, true);
        assertEquals(AnswerState.Status.RECORDED, state.status());
    }
    @Test void rejectionCannotBeOverwrittenByAnotherAckOrChoice() {
        var state = new AnswerState(1, 1000, 0); state.submit("D", 1); state.acknowledge(1, false);
        state.acknowledge(1, true); assertEquals(AnswerState.Status.REJECTED, state.status()); assertFalse(state.submit("A", 2));
    }
    @Test void rejectsInvalidChoicesWithoutLocking() {
        var state = new AnswerState(1, 1000, 0); assertFalse(state.submit(null, 1)); assertFalse(state.submit("E", 1)); assertFalse(state.submit("b", 1));
        assertEquals(AnswerState.Status.OPEN, state.status());
    }
}
