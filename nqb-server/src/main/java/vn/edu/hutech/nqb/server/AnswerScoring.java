package vn.edu.hutech.nqb.server;

/** Server-authoritative scoring; elapsed time is never supplied by a Client. */
final class AnswerScoring {
    static final String VERSION="SPEED_STREAK_V1";
    record Round(int points,int speedBonus,int streakBonus,int streak) { }
    private AnswerScoring() { }
    static Round calculate(boolean correct,long elapsedMillis,long durationMillis,int previousStreak) {
        if(durationMillis<=0||previousStreak<0)throw new IllegalArgumentException("Invalid scoring inputs");
        if(!correct||elapsedMillis<0||elapsedMillis>=durationMillis)return new Round(0,0,0,0);
        int streak=previousStreak+1;
        int speed=(int)(100*(durationMillis-elapsedMillis)/durationMillis);
        int chain=20*Math.min(5,streak-1);
        return new Round(100+speed+chain,speed,chain,streak);
    }
}
