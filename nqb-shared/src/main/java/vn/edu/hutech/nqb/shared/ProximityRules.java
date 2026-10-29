package vn.edu.hutech.nqb.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared rules and mathematical models for Wi-Fi RSSI calibration,
 * log-distance path loss estimation, median filtering, and threshold alerts.
 */
public final class ProximityRules {

    public static final String CAPABILITY = "PROXIMITY_V1";

    public static final int DEFAULT_THRESHOLD_METERS = 10;
    public static final int MIN_THRESHOLD_METERS = 2;
    public static final int MAX_THRESHOLD_METERS = 100;

    public static final double CALIBRATION_D1_METERS = 1.0;
    public static final double CALIBRATION_D2_METERS = 5.0;
    public static final int CALIBRATION_STEP_SECONDS = 30;
    public static final int MIN_CALIBRATION_SAMPLES = 20;
    public static final int MAX_CALIBRATION_SAMPLES = 120;

    public static final int MIN_VALID_RSSI_DBM = -100;
    public static final int MAX_VALID_RSSI_DBM = -15;

    public static final double MIN_PATH_LOSS_N = 1.0;
    public static final double MAX_PATH_LOSS_N = 6.0;
    public static final double MAX_MAD_DB = 6.0;

    public static final int MEDIAN_WINDOW_SIZE = 5;
    public static final long STALE_TIMEOUT_MS = 15_000L;
    public static final long SAMPLE_INTERVAL_MS = 1_000L;
    public static final long REPORT_INTERVAL_MS = 5_000L;
    public static final long SUMMARY_PERSIST_INTERVAL_MS = 30_000L;

    public static final int ALERT_CONSECUTIVE_REPORTS = 3;
    public static final int RECOVERY_CONSECUTIVE_REPORTS = 3;
    public static final double RECOVERY_THRESHOLD_RATIO = 0.80;

    private static final double TEN_LOG10_D2_OVER_D1 = 10.0 * Math.log10(CALIBRATION_D2_METERS / CALIBRATION_D1_METERS);

    private ProximityRules() { }

    public record StepEvaluation(
        boolean valid,
        int validSampleCount,
        double medianRssiDbm,
        double madDb,
        String message
    ) { }

    public record CalibrationEvaluation(
        boolean valid,
        StepEvaluation step1m,
        StepEvaluation step5m,
        double pathLossN,
        String message
    ) { }

    public static boolean isValidRssi(int rssiDbm) {
        return rssiDbm >= MIN_VALID_RSSI_DBM && rssiDbm <= MAX_VALID_RSSI_DBM;
    }

    public static List<Integer> filterValidSamples(List<Integer> rawSamples) {
        if (rawSamples == null || rawSamples.isEmpty()) return List.of();
        List<Integer> valid = new ArrayList<>(rawSamples.size());
        for (Integer sample : rawSamples) {
            if (sample != null && isValidRssi(sample)) {
                valid.add(sample);
            }
        }
        return List.copyOf(valid);
    }

    public static double median(List<Integer> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Samples must not be empty");
        }
        List<Integer> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    public static double mad(List<Integer> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Samples must not be empty");
        }
        double med = median(samples);
        List<Double> deviations = new ArrayList<>(samples.size());
        for (int s : samples) {
            deviations.add(Math.abs(s - med));
        }
        Collections.sort(deviations);
        int n = deviations.size();
        if (n % 2 == 1) {
            return deviations.get(n / 2);
        }
        return (deviations.get(n / 2 - 1) + deviations.get(n / 2)) / 2.0;
    }

    public static StepEvaluation evaluateStep(List<Integer> rawSamples, int distanceMeters) {
        List<Integer> valid = filterValidSamples(rawSamples);
        if (valid.size() < MIN_CALIBRATION_SAMPLES) {
            return new StepEvaluation(
                false,
                valid.size(),
                0.0,
                0.0,
                "Bước " + distanceMeters + " m mới thu được " + valid.size() + "/" + MIN_CALIBRATION_SAMPLES + " mẫu hợp lệ. Hãy đo lại."
            );
        }
        if (valid.size() > MAX_CALIBRATION_SAMPLES) {
            valid = valid.subList(valid.size() - MAX_CALIBRATION_SAMPLES, valid.size());
        }
        double med = median(valid);
        double mad = mad(valid);
        if (mad > MAX_MAD_DB) {
            return new StepEvaluation(
                false,
                valid.size(),
                med,
                mad,
                String.format(Locale.ROOT, "Tín hiệu ở bước %d m dao động mạnh (độ lệch %.1f dB, tối đa %.0f dB). Hãy giữ máy cố định và đo lại.", distanceMeters, mad, MAX_MAD_DB)
            );
        }
        return new StepEvaluation(
            true,
            valid.size(),
            med,
            mad,
            String.format(Locale.ROOT, "Bước %d m đạt yêu cầu (%d mẫu, trung vị %.0f dBm, độ lệch %.1f dB).", distanceMeters, valid.size(), med, mad)
        );
    }

    public static double computePathLossExponent(double rssi1m, double rssi5m) {
        return (rssi1m - rssi5m) / TEN_LOG10_D2_OVER_D1;
    }

    public static CalibrationEvaluation evaluateCalibration(List<Integer> raw1m, List<Integer> raw5m) {
        StepEvaluation s1 = evaluateStep(raw1m, 1);
        StepEvaluation s5 = evaluateStep(raw5m, 5);
        if (!s1.valid()) {
            return new CalibrationEvaluation(false, s1, s5, 0.0, s1.message());
        }
        if (!s5.valid()) {
            return new CalibrationEvaluation(false, s1, s5, 0.0, s5.message());
        }
        double n = computePathLossExponent(s1.medianRssiDbm(), s5.medianRssiDbm());
        if (Double.isNaN(n) || Double.isInfinite(n) || n < MIN_PATH_LOSS_N || n > MAX_PATH_LOSS_N) {
            String reason = s1.medianRssiDbm() <= s5.medianRssiDbm()
                ? "Tín hiệu ở 1 m không mạnh hơn ở 5 m. Hãy kiểm tra đúng khoảng cách đặt máy và đo lại."
                : String.format(Locale.ROOT, "Hệ số suy hao n = %.2f nằm ngoài khoảng cho phép (1.0–6.0). Hãy đo lại hai bước.", n);
            return new CalibrationEvaluation(false, s1, s5, n, reason);
        }
        return new CalibrationEvaluation(
            true,
            s1,
            s5,
            n,
            String.format(Locale.ROOT, "Đã hoàn tất hiệu chuẩn (1 m: %.0f dBm, 5 m: %.0f dBm, hệ số n = %.2f).", s1.medianRssiDbm(), s5.medianRssiDbm(), n)
        );
    }

    public static double estimateDistanceMeters(double rssi1m, double pathLossN, double currentMedianRssi) {
        if (!Double.isFinite(pathLossN) || !Double.isFinite(rssi1m) || !Double.isFinite(currentMedianRssi)
                || pathLossN < MIN_PATH_LOSS_N || pathLossN > MAX_PATH_LOSS_N
                || rssi1m < MIN_VALID_RSSI_DBM || rssi1m > MAX_VALID_RSSI_DBM
                || currentMedianRssi < MIN_VALID_RSSI_DBM || currentMedianRssi > MAX_VALID_RSSI_DBM) {
            throw new IllegalArgumentException("Invalid path loss exponent n: " + pathLossN);
        }
        double exponent = (rssi1m - currentMedianRssi) / (10.0 * pathLossN);
        double d = CALIBRATION_D1_METERS * Math.pow(10.0, exponent);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 200.0;
        }
        return Math.max(0.2, Math.min(200.0, d));
    }

    public static boolean isExtrapolated(double distanceMeters) {
        return distanceMeters < CALIBRATION_D1_METERS-1e-6 || distanceMeters > CALIBRATION_D2_METERS+1e-6;
    }

    public static String formatEstimatedDistance(double distanceMeters) {
        long rounded = Math.max(1L, Math.round(distanceMeters));
        return "≈ " + rounded + " m";
    }

    public static String normalizeBssid(String raw) {
        if (raw == null) return "";
        String cleaned = raw.strip().toLowerCase(Locale.ROOT).replace('-', ':');
        if (cleaned.matches("([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
            return cleaned;
        }
        return "";
    }

    public static String normalizeBand(String raw) {
        if (raw == null) return "";
        return switch (raw.strip().toLowerCase(Locale.ROOT).replace(" ", "").replace(',', '.')) {
            case "2.4ghz", "2.4" -> "2.4 GHz";
            case "5ghz" -> "5 GHz";
            case "6ghz" -> "6 GHz";
            default -> "";
        };
    }

    public static String bandFromFrequencyKhz(int frequencyKhz) {
        if (frequencyKhz >= 2_400_000 && frequencyKhz <= 2_500_000) return "2.4 GHz";
        if (frequencyKhz >= 4_900_000 && frequencyKhz <= 5_900_000) return "5 GHz";
        if (frequencyKhz >= 5_925_000 && frequencyKhz <= 7_125_000) return "6 GHz";
        return "";
    }

    public static String bandFromChannel(int channel) {
        if (channel >= 1 && channel <= 14) return "2.4 GHz";
        if (channel >= 32 && channel <= 177) return "5 GHz";
        return "";
    }

    public static int channelFromFrequencyKhz(int frequencyKhz) {
        if (frequencyKhz == 2_484_000) return 14;
        if (frequencyKhz >= 2_412_000 && frequencyKhz < 2_484_000) return (frequencyKhz - 2_407_000) / 5_000;
        if (frequencyKhz >= 5_000_000 && frequencyKhz <= 5_895_000) return (frequencyKhz - 5_000_000) / 5_000;
        if (frequencyKhz >= 5_955_000 && frequencyKhz <= 7_115_000) return (frequencyKhz - 5_950_000) / 5_000;
        return 0;
    }
}
