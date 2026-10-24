package vn.edu.hutech.nqb.server;

/** Uses only elapsed time recorded by the Server for an accepted answer. */
final class SpeedScoring {
    private SpeedScoring() { }

    static int points(boolean correct,long elapsedNanos,long durationNanos) {
        if(durationNanos<=0)throw new IllegalArgumentException("Question duration must be positive");
        if(!correct||elapsedNanos<0||elapsedNanos>=durationNanos)return 0;
        double remainingRatio=(double)(durationNanos-elapsedNanos)/durationNanos;
        return 50+(int)Math.round(50*remainingRatio);
    }
}
