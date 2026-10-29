package vn.edu.hutech.nqb.client;

/** An ACK does not imply correctness. Used only on the UI thread. */
public final class AnswerState {
    public enum Status { OPEN, SENDING, RECORDED, EXPIRED, REJECTED }
    private final long questionId;
    private final long deadline;
    private Status status = Status.OPEN;
    private String answer = "";
    public AnswerState(long questionId, long remainingMillis, long now) {
        this.questionId = questionId;
        deadline = now + Math.max(0, remainingMillis) * 1_000_000L;
    }
    public boolean submit(String choice, long now) {
        expire(now);
        if (status != Status.OPEN || choice == null || !choice.matches("[ABCD]")) return false;
        answer = choice;
        status = Status.SENDING;
        return true;
    }
    public void acknowledge(long id, boolean accepted) {
        if (id == questionId && status == Status.SENDING) {
            status = accepted ? Status.RECORDED : Status.REJECTED;
        }
    }
    /** Only an authoritative Server snapshot can restore an already recorded choice. */
    public void restoreRecorded(String choice){
        if(choice==null||!choice.matches("[ABCD]"))throw new IllegalArgumentException("Invalid recorded answer");
        answer=choice;status=Status.RECORDED;
    }
    public void expire(long now) {
        if (now >= deadline && status == Status.OPEN) status = Status.EXPIRED;
    }
    public long remainingMillis(long now) { return Math.max(0, (deadline - now) / 1_000_000L); }
    public Status status() { return status; }
    public String answer() { return answer; }
    public long questionId() { return questionId; }
}
