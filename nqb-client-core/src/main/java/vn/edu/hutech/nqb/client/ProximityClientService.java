package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.ProximityRules;
import vn.edu.hutech.nqb.shared.LanTelemetryRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static vn.edu.hutech.nqb.client.QuizGateway.object;

/**
 * Manages client-side Wi-Fi sampling (1 Hz), throttled PROXIMITY_REPORT transmission (max 1 per 5s),
 * and 30-second calibration sample capture on a single non-overlapping worker thread.
 */
public final class ProximityClientService implements AutoCloseable {

    public record CalibrationProgress(
        int distanceMeters,
        int elapsedSeconds,
        int totalSeconds,
        int validSamples,
        WifiSignalProvider.Reading latestReading,
        double currentMedianDbm,
        double currentMadDb
    ) { }

    public record CalibrationStepResult(
        int distanceMeters,
        List<Integer> validSamples,
        ProximityRules.StepEvaluation evaluation,
        WifiSignalProvider.Reading lastReading
    ) { }

    private final WifiSignalProvider provider;
    private final ProximityCalibrationStore calibrationStore;
    private final long sampleIntervalMs;
    private final long reportIntervalMs;
    private final int calibrationStepTicks;
    private final ScheduledExecutorService worker;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger calibrationGeneration = new AtomicInteger();
    private final AtomicLong reportSequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean scanQueued = new AtomicBoolean();
    private final AtomicBoolean surveyReserved = new AtomicBoolean();
    private final Deque<Integer> recentSamples = new ArrayDeque<>(ProximityRules.MEDIAN_WINDOW_SIZE);

    private ScheduledFuture<?> reportingTask;
    private ScheduledFuture<?> calibrationTask;
    private String lastSampleKey = "";
    private long lastReportSentNano;
    private volatile WifiSignalProvider.Reading latestReading;
    private static final class FreshCapture {
        final String id;final int generation;final long started=System.nanoTime();final List<Integer> samples=new ArrayList<>();String key="";ScheduledFuture<?> task;
        FreshCapture(String id,int generation){this.id=id;this.generation=generation;}
    }
    private FreshCapture freshCapture;

    /** Shares the native worker and report throttle with normal sampling. */
    public void requestFreshSamples(QuizGateway gateway,String id,int epoch,String session,
            IntSupplier currentEpoch,Supplier<String> currentSession,Consumer<JsonNode> onSelfUpdate) {
        if(closed.get()||gateway==null)return;
        int gen=generation.get();
        try{worker.execute(()->{
            if(closed.get()||gen!=generation.get()||epoch!=currentEpoch.getAsInt()||!Objects.equals(session,currentSession.get()))return;
            if(freshCapture!=null&&freshCapture.id.equals(id))return;
            cancelFresh();
            if(calibrationTask!=null||surveyReserved.get()){freshStatus(gateway,id,"CALIBRATING");return;}
            var capture=new FreshCapture(id,gen);freshCapture=capture;
            capture.task=worker.scheduleWithFixedDelay(()->{
                if(closed.get()||capture.generation!=generation.get()||epoch!=currentEpoch.getAsInt()||!Objects.equals(session,currentSession.get())){cancelFresh();return;}
                if(calibrationTask!=null){cancelFresh();freshStatus(gateway,id,"CALIBRATING");return;}
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-capture.started)>=LanTelemetryRules.REFRESH_TIMEOUT_MS){cancelFresh();freshStatus(gateway,id,"CANCELLED");return;}
                var reading=readSafely();latestReading=reading;
                if(closed.get()||capture.generation!=generation.get()||epoch!=currentEpoch.getAsInt()||!Objects.equals(session,currentSession.get())){cancelFresh();return;}
                if(!reading.isConnected()){cancelFresh();freshStatus(gateway,id,reading.status().name());return;}
                String key=reading.adapterId()+"|"+reading.bssid()+"|"+reading.band();if(!capture.key.equals(key)){capture.samples.clear();capture.key=key;}
                capture.samples.add(reading.rssiDbm());
                if(capture.samples.size()>LanTelemetryRules.REFRESH_SAMPLES)capture.samples.removeFirst();
                long now=System.nanoTime();
                if(capture.samples.size()<LanTelemetryRules.REFRESH_SAMPLES||(lastReportSentNano!=0&&TimeUnit.NANOSECONDS.toMillis(now-lastReportSentNano)<reportIntervalMs))return;
                var report=buildReportPayload(reading,List.copyOf(capture.samples),epoch,session,reportSequence.incrementAndGet()).put("refresh_id",id);
                lastReportSentNano=now;recentSamples.clear();recentSamples.addAll(capture.samples);lastSampleKey=reading.adapterId()+"|"+reading.bssid()+"|"+reading.band();cancelFresh();
                gateway.request("PROXIMITY_REPORT",report).whenComplete((response,error)->{
                    if(error==null&&gen==generation.get()&&epoch==currentEpoch.getAsInt()&&Objects.equals(session,currentSession.get())&&onSelfUpdate!=null)onSelfUpdate.accept(response);
                });
            },0,sampleIntervalMs,TimeUnit.MILLISECONDS);
        });}catch(RejectedExecutionException ignored){}
    }
    private void freshStatus(QuizGateway gateway,String id,String reason){gateway.request("PROXIMITY_REFRESH_STATUS",object().put("refresh_id",id).put("reason",reason)).exceptionally(error->null);}
    private void cancelFresh(){if(freshCapture!=null){if(freshCapture.task!=null)freshCapture.task.cancel(false);freshCapture=null;}}

    public ProximityClientService() {
        this(
            WifiSignalProvider.createDefault(),
            new ProximityCalibrationStore(),
            ProximityRules.SAMPLE_INTERVAL_MS,
            ProximityRules.REPORT_INTERVAL_MS,
            ProximityRules.CALIBRATION_STEP_SECONDS
        );
    }

    public ProximityClientService(
        WifiSignalProvider provider,
        ProximityCalibrationStore calibrationStore,
        long sampleIntervalMs,
        long reportIntervalMs,
        int calibrationStepTicks
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.calibrationStore = Objects.requireNonNull(calibrationStore, "calibrationStore");
        this.sampleIntervalMs = Math.max(10L, sampleIntervalMs);
        this.reportIntervalMs = Math.max(this.sampleIntervalMs, reportIntervalMs);
        this.calibrationStepTicks = Math.max(1, calibrationStepTicks);
        this.worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proximity-client-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public WifiSignalProvider provider() {
        return provider;
    }

    public ProximityCalibrationStore calibrationStore() {
        return calibrationStore;
    }

    public WifiSignalProvider.Reading latestReading() {
        return latestReading;
    }

    /**
     * Reads current Wi-Fi signal once on the calling thread or worker.
     * Never invoked automatically at login; only when user initiates measurement/calibration/Ready.
     */
    public WifiSignalProvider.Reading readNow() {
        int gen = generation.get();
        WifiSignalProvider.Reading reading = readSafely();
        if (gen == generation.get() && !closed.get()) latestReading = reading;
        return reading;
    }

    private WifiSignalProvider.Reading readSafely() {
        try {
            var reading = Objects.requireNonNull(provider.readSignal());
            if(reading.status()==WifiSignalProvider.Status.CONNECTED && !ProximityRules.isValidRssi(reading.rssiDbm())) {
                return WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.API_ERROR,reading.adapterId(),reading.adapterName(),
                    "Tín hiệu Wi-Fi nằm ngoài dải đo hỗ trợ.",System.currentTimeMillis());
            }
            return reading;
        }
        catch (RuntimeException error) {
            return WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.API_ERROR,
                "Không đọc được dữ liệu Wi-Fi.", System.currentTimeMillis());
        }
    }

    /** Serializes native reads with sampling without blocking the JavaFX thread. */
    public CompletableFuture<WifiSignalProvider.Reading> readNowAsync() {
        int gen = generation.get();
        var future = new CompletableFuture<WifiSignalProvider.Reading>();
        try {
            worker.execute(() -> {
                if (closed.get() || gen != generation.get()) { future.cancel(false); return; }
                var reading = readSafely();
                if (closed.get() || gen != generation.get()) { future.cancel(false); return; }
                latestReading = reading;
                future.complete(reading);
            });
        } catch (RejectedExecutionException stopped) { future.cancel(false); }
        return future;
    }
    public CompletableFuture<List<WifiScanProvider.Adapter>> scanAdaptersAsync(){
        var result=new CompletableFuture<List<WifiScanProvider.Adapter>>();
        try{worker.execute(()->{try{result.complete(provider instanceof WifiScanProvider scanner?scanner.scanAdapters():List.of());}catch(Exception e){result.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){result.completeExceptionally(e);}return result;
    }
    /** Runs on the same native worker as RSSI/calibration; only one scan may be queued. */
    public CompletableFuture<WifiScanSnapshot> scanAsync(String adapter,String ssid){
        return scanAsync(adapter,ssid,false);
    }
    public CompletableFuture<Void> reserveSurvey(){
        var future=new CompletableFuture<Void>();
        try{worker.execute(()->{if(closed.get()||calibrationTask!=null||freshCapture!=null||scanQueued.get()||!surveyReserved.compareAndSet(false,true))future.completeExceptionally(new IllegalStateException("Hoàn tất lượt quét/hiệu chuẩn hiện tại trước khi khảo sát."));else future.complete(null);});}
        catch(RejectedExecutionException e){future.completeExceptionally(e);}return future;
    }
    public void releaseSurvey(){surveyReserved.set(false);}
    public CompletableFuture<WifiScanSnapshot> surveyScanAsync(String adapter,String ssid){return scanAsync(adapter,ssid,true);}
    private CompletableFuture<WifiScanSnapshot> scanAsync(String adapter,String ssid,boolean survey){
        var result=new CompletableFuture<WifiScanSnapshot>();int gen=generation.get();
        if(survey!=surveyReserved.get())return CompletableFuture.failedFuture(new IllegalStateException("Card Wi-Fi đang dành cho lượt khảo sát."));
        if(!scanQueued.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Đang có lượt quét Wi-Fi."));
        try{worker.execute(()->{
            try {
                if(closed.get()||(!survey&&gen!=generation.get())||result.isCancelled()){result.cancel(false);return;}
                if(calibrationTask!=null||freshCapture!=null)throw new IllegalStateException("Hoàn tất hoặc hủy lượt hiệu chuẩn/đo hiện tại trước khi quét.");
                if(!(provider instanceof WifiScanProvider scanner)){result.complete(WifiScanSnapshot.failure(WifiScanSnapshot.Status.UNSUPPORTED,"Thiết bị chưa hỗ trợ quét nhiều BSSID."));return;}
                var value=scanner.scan(adapter,ssid);
                // Do not mix pre-scan and post-scan readings into one median window.
                recentSamples.clear();lastSampleKey="";
                if(closed.get()||(!survey&&gen!=generation.get())||result.isCancelled()){result.cancel(false);return;}
                if(value.freshCount()>0)new WifiObservationCatalog().record(value);
                result.complete(value);
            }catch(Exception e){result.completeExceptionally(e);}finally{scanQueued.set(false);}
        });}catch(RejectedExecutionException e){scanQueued.set(false);result.completeExceptionally(e);}
        result.whenComplete((v,e)->{if(result.isCancelled()&&provider instanceof WifiScanProvider scanner)scanner.cancelScan();});return result;
    }

    public synchronized void startReporting(
        QuizGateway gateway,
        IntSupplier configEpochSupplier,
        Supplier<String> sessionIdSupplier,
        Consumer<JsonNode> onServerSelfUpdate,
        Consumer<WifiSignalProvider.Reading> onLocalReading
    ) {
        if (closed.get() || gateway == null) {
            return;
        }
        cancelTasksOnly();
        int gen = generation.incrementAndGet();
        // The deque belongs to the worker, including cleanup between sessions.
        worker.execute(() -> {
            recentSamples.clear();
            lastSampleKey = "";
        });

        reportingTask = worker.scheduleWithFixedDelay(
            () -> sampleAndMaybeReport(gen, gateway, configEpochSupplier, sessionIdSupplier, onServerSelfUpdate, onLocalReading),
            0L,
            sampleIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    private void sampleAndMaybeReport(
        int gen,
        QuizGateway gateway,
        IntSupplier configEpochSupplier,
        Supplier<String> sessionIdSupplier,
        Consumer<JsonNode> onServerSelfUpdate,
        Consumer<WifiSignalProvider.Reading> onLocalReading
    ) {
        if (closed.get() || gen != generation.get()) {
            return;
        }
        int sampleEpoch = configEpochSupplier.getAsInt();
        String sampleSession = sessionIdSupplier.get();
        WifiSignalProvider.Reading reading = readSafely();
        if (gen != generation.get()) {
            return;
        }
        latestReading = reading;
        if (onLocalReading != null) {
            onLocalReading.accept(reading);
        }

        if (reading.isConnected() && ProximityRules.isValidRssi(reading.rssiDbm())) {
            String key = reading.adapterId() + "|" + reading.bssid() + "|" + reading.band();
            if (!key.equals(lastSampleKey)) {
                recentSamples.clear();
                lastSampleKey = key;
            }
            if (recentSamples.size() >= ProximityRules.MEDIAN_WINDOW_SIZE) {
                recentSamples.removeFirst();
            }
            recentSamples.addLast(reading.rssiDbm());
        } else {
            recentSamples.clear();
            lastSampleKey = "";
        }

        long nowNano = System.nanoTime();
        if(freshCapture!=null)return;
        long elapsedMs = lastReportSentNano == 0L ? reportIntervalMs : TimeUnit.NANOSECONDS.toMillis(nowNano - lastReportSentNano);
        if (elapsedMs < reportIntervalMs) {
            return;
        }

        lastReportSentNano = nowNano;
        ObjectNode payload = buildReportPayload(
            reading,
            new ArrayList<>(recentSamples),
            sampleEpoch,
            sampleSession,
            reportSequence.incrementAndGet()
        );
        gateway.request("PROXIMITY_REPORT", payload).whenComplete((response, error) -> {
            if (gen != generation.get() || closed.get() || sampleEpoch != configEpochSupplier.getAsInt()
                    || !Objects.equals(sampleSession, sessionIdSupplier.get())) {
                return;
            }
            if (error == null && response != null && onServerSelfUpdate != null) {
                onServerSelfUpdate.accept(response);
            }
        });
    }

    public static ObjectNode buildReportPayload(
        WifiSignalProvider.Reading reading,
        List<Integer> recentSamples,
        int configEpoch,
        String sessionId,
        long seq
    ) {
        ObjectNode payload = object()
            .put("config_epoch", configEpoch)
            .put("seq", seq)
            .put("device_status", reading.status().name())
            .put("adapter_id", reading.adapterId())
            .put("adapter_name", reading.adapterName())
            .put("ssid", reading.ssid())
            .put("bssid", reading.bssid())
            .put("band", reading.band())
            .put("channel", reading.channel())
            .put("rssi_dbm", reading.rssiDbm())
            .put("signal_quality", reading.signalQuality());
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("session_id", sessionId);
        }
        ArrayNode arr = payload.putArray("samples");
        if (recentSamples != null) {
            for (Integer s : recentSamples) {
                if (s != null && ProximityRules.isValidRssi(s)) {
                    arr.add(s);
                }
            }
        }
        return payload;
    }

    public synchronized void startCalibrationStep(
        int distanceMeters,
        String expectedAdapterId,
        String anchorBssid,
        String anchorBand,
        Consumer<CalibrationProgress> onProgress,
        Consumer<CalibrationStepResult> onComplete
    ) {
        if(surveyReserved.get())throw new IllegalStateException("Hoàn tất khảo sát trước khi hiệu chuẩn.");
        if (closed.get()) {
            return;
        }
        int calibrationGen = calibrationGeneration.incrementAndGet();
        if (calibrationTask != null) {
            calibrationTask.cancel(false);
            calibrationTask = null;
        }
        int gen = generation.get();
        List<Integer> collected = new ArrayList<>(calibrationStepTicks);
        AtomicInteger ticks = new AtomicInteger();

        calibrationTask = worker.scheduleWithFixedDelay(() -> {
            if (closed.get() || gen != generation.get() || calibrationGen != calibrationGeneration.get()) {
                return;
            }
            int currentTick = ticks.incrementAndGet();
            WifiSignalProvider.Reading reading = readSafely();
            if (closed.get() || gen != generation.get() || calibrationGen != calibrationGeneration.get()) return;
            latestReading = reading;

            boolean matchesAdapter = expectedAdapterId == null || expectedAdapterId.isBlank() || expectedAdapterId.equalsIgnoreCase(reading.adapterId());
            if (reading.isConnected() && matchesAdapter && reading.matchesAnchor(anchorBssid, anchorBand) && ProximityRules.isValidRssi(reading.rssiDbm())) {
                collected.add(reading.rssiDbm());
            }

            double med = collected.isEmpty() ? 0.0 : ProximityRules.median(collected);
            double mad = collected.isEmpty() ? 0.0 : ProximityRules.mad(collected);
            if (onProgress != null && gen == generation.get() && calibrationGen == calibrationGeneration.get()) {
                onProgress.accept(new CalibrationProgress(
                    distanceMeters,
                    currentTick,
                    calibrationStepTicks,
                    collected.size(),
                    reading,
                    med,
                    mad
                ));
            }

            if (currentTick >= calibrationStepTicks) {
                synchronized (this) {
                    if (calibrationGen != calibrationGeneration.get()) return;
                    if (calibrationTask != null) calibrationTask.cancel(false);
                    calibrationTask = null;
                }
                ProximityRules.StepEvaluation eval = ProximityRules.evaluateStep(collected, distanceMeters);
                if (onComplete != null && gen == generation.get() && calibrationGen == calibrationGeneration.get()) {
                    onComplete.accept(new CalibrationStepResult(distanceMeters, List.copyOf(collected), eval, reading));
                }
            }
        }, sampleIntervalMs, sampleIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void cancelCalibrationStep() {
        calibrationGeneration.incrementAndGet();
        if (calibrationTask != null) {
            calibrationTask.cancel(false);
            calibrationTask = null;
        }
    }

    private void cancelTasksOnly() {
        calibrationGeneration.incrementAndGet();
        if (reportingTask != null) {
            reportingTask.cancel(false);
            reportingTask = null;
        }
        if (calibrationTask != null) {
            calibrationTask.cancel(false);
            calibrationTask = null;
        }
    }

    public synchronized void stopSession() {
        generation.incrementAndGet();
        if((closed.get()||!surveyReserved.get())&&provider instanceof WifiScanProvider scanner)scanner.cancelScan();
        cancelTasksOnly();
        latestReading = null;
        try { worker.execute(() -> {
            cancelFresh();
            recentSamples.clear();
            lastSampleKey = "";
            lastReportSentNano = 0L;
            if(!surveyReserved.get())provider.releaseHandle();
        }); } catch (RejectedExecutionException ignored) { }
    }

    public int currentGeneration() {
        return generation.get();
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            stopSession();
            worker.execute(provider::close);
            worker.shutdown();
        }
    }
}
