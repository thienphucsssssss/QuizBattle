package vn.edu.hutech.nqb.shared;

public final class LanTelemetryRules {
    public static final String CAPABILITY="LAN_TOPOLOGY_V1";
    public static final long PROBE_INTERVAL_MS=5000,PROBE_TIMEOUT_MS=3000,STALE_MS=15000,REFRESH_TIMEOUT_MS=10000,REFRESH_INTERVAL_MS=10000;
    public static final int REFRESH_SAMPLES=5;
    private LanTelemetryRules() { }
}
