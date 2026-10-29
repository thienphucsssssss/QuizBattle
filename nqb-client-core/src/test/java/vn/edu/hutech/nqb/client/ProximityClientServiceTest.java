package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ProximityClientServiceTest {

    @TempDir
    Path tempDir;

    private static WifiSignalProvider.Reading goodReading() {
        return WifiSignalProvider.Reading.connected("adapter-1","Wi-Fi","AP","aa:bb:cc:dd:ee:01","5 GHz",36,5180000,-50,80,System.currentTimeMillis());
    }

    @Test void cancelledCalibrationCannotCompleteOrCancelItsReplacement() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var calls=new AtomicInteger(); var staleCallbacks=new AtomicInteger(); var completed=new CountDownLatch(1);
        WifiSignalProvider provider=new WifiSignalProvider() {
            public Reading readSignal() {
                if(calls.incrementAndGet()==1) { entered.countDown(); try { release.await(3,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
                return goodReading();
            }
            public void close() { }
        };
        try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(tempDir.resolve("cancel.json")),10,100,1)) {
            service.startCalibrationStep(1,"adapter-1","aa:bb:cc:dd:ee:01","5 GHz",p->staleCallbacks.incrementAndGet(),r->staleCallbacks.incrementAndGet());
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            service.cancelCalibrationStep();
            service.startCalibrationStep(5,"adapter-1","aa:bb:cc:dd:ee:01","5 GHz",null,r->completed.countDown());
            release.countDown();
            assertTrue(completed.await(2,TimeUnit.SECONDS));
            assertEquals(0,staleCallbacks.get());
        } finally { release.countDown(); }
    }

    @Test void asyncReadAndStopNeverWaitForSlowNativeCallOrPublishOldResult() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        WifiSignalProvider provider=new WifiSignalProvider() {
            public synchronized Reading readSignal() { entered.countDown(); try { release.await(3,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } return goodReading(); }
            public synchronized void releaseHandle() { }
            public void close() { }
        };
        try(var service=new ProximityClientService(provider,new ProximityCalibrationStore(tempDir.resolve("async.json")),10,100,1)) {
            var read=service.readNowAsync();
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofMillis(300),service::stopSession);
            release.countDown();
            assertThrows(java.util.concurrent.CancellationException.class,()->read.get(2,TimeUnit.SECONDS));
            assertNull(service.latestReading());
        } finally { release.countDown(); }
    }

    @Test
    void localCalibrationStoreSavesFindsAndInvalidatesOnAdapterBssidBandOrRoomRecalibration() throws Exception {
        ProximityCalibrationStore store = new ProximityCalibrationStore(tempDir.resolve("calibrations.json"));
        List<Integer> s1 = Collections.nCopies(24, -45);
        List<Integer> s5 = Collections.nCopies(24, -61);

        var saved = store.save("adapter-1", "Intel AX201", "Lab-WiFi", "AA-BB-CC-DD-EE-01", "5GHz", s1, s5);
        assertEquals("aa:bb:cc:dd:ee:01", saved.bssid());
        assertEquals("5 GHz", saved.band());

        // Exact match (case-insensitive BSSID/band) finds profile
        assertTrue(store.find("adapter-1", "aa:bb:cc:dd:ee:01", "5 GHz", "").isPresent());

        // Changing adapter, BSSID, or band requires recalibration (returns empty)
        assertTrue(store.find("adapter-2", "aa:bb:cc:dd:ee:01", "5 GHz", "").isEmpty());
        assertTrue(store.find("adapter-1", "aa:bb:cc:dd:ee:02", "5 GHz", "").isEmpty());
        assertTrue(store.find("adapter-1", "aa:bb:cc:dd:ee:01", "2.4 GHz", "").isEmpty());

        // Room recalibrated after profile was saved invalidates profile
        String futureRecal = Instant.now().plusSeconds(10).toString();
        assertTrue(store.find("adapter-1", "aa:bb:cc:dd:ee:01", "5 GHz", futureRecal).isEmpty());

        // Room recalibrated before profile was saved keeps profile valid
        String pastRecal = Instant.now().minusSeconds(60).toString();
        assertTrue(store.find("adapter-1", "aa:bb:cc:dd:ee:01", "5 GHz", pastRecal).isPresent());
    }

    @Test
    void workerSamplesFrequentlyButThrottlesReportsAndStopsCleanlyWithoutCrossSessionLeak() throws Exception {
        AtomicReference<WifiSignalProvider.Reading> readingRef = new AtomicReference<>(
            WifiSignalProvider.Reading.connected("adapter-1", "Wi-Fi 6", "RoomAP", "aa:bb:cc:dd:ee:01", "5 GHz", 36, 5180000, -52, 85, System.currentTimeMillis())
        );
        AtomicInteger providerCalls = new AtomicInteger();
        WifiSignalProvider mockProvider = new WifiSignalProvider() {
            @Override
            public Reading readSignal() {
                providerCalls.incrementAndGet();
                return readingRef.get();
            }

            @Override
            public void close() {
            }
        };

        List<ObjectNode> sentReports = new CopyOnWriteArrayList<>();
        List<CompletableFuture<JsonNode>> pendingResponses = new CopyOnWriteArrayList<>();
        QuizGateway fakeGateway = new QuizGateway() {
            @Override
            public CompletableFuture<JsonNode> request(String type, ObjectNode payload) {
                if ("PROXIMITY_REPORT".equals(type)) {
                    sentReports.add(payload.deepCopy());
                }
                CompletableFuture<JsonNode> f = new CompletableFuture<>();
                pendingResponses.add(f);
                return f;
            }

            @Override
            public void onEvent(Consumer<JsonNode> listener) {
            }

            @Override
            public boolean isPreview() {
                return false;
            }

            @Override
            public void close() {
            }
        };

        ProximityCalibrationStore store = new ProximityCalibrationStore(tempDir.resolve("cal.json"));
        try (ProximityClientService service = new ProximityClientService(mockProvider, store, 20L, 120L, 22)) {
            // Not called before startReporting / readNow
            assertEquals(0, providerCalls.get());

            AtomicInteger selfUpdates = new AtomicInteger();
            service.startReporting(
                fakeGateway,
                () -> 3,
                () -> "sess-1",
                node -> selfUpdates.incrementAndGet(),
                null
            );

            Thread.sleep(210L);
            // Sampled multiple times (~8-10 times at 20ms interval), but throttled reports (at ~0ms and ~120ms => 2 reports)
            assertTrue(providerCalls.get() >= 5, "Expected >= 5 sample ticks, got " + providerCalls.get());
            assertTrue(sentReports.size() >= 2 && sentReports.size() <= 3, "Expected 2-3 throttled reports, got " + sentReports.size());

            // Stop on leave/logout/disconnect and ensure late responses from old generation do not update new state
            service.stopSession();
            int callsAfterStop = providerCalls.get();
            for (CompletableFuture<JsonNode> f : pendingResponses) {
                f.complete(QuizGateway.object().put("status", "MEASURING"));
            }
            Thread.sleep(60L);
            assertEquals(0, selfUpdates.get(), "Responses from stopped session must be ignored");
            assertEquals(callsAfterStop, providerCalls.get(), "No further sampling after stopSession");
        }
    }

    @Test
    void calibrationStepFiltersWrongAnchorOrPermissionErrorsAndCompletesOnWorker() throws Exception {
        AtomicInteger tick = new AtomicInteger();
        WifiSignalProvider mockProvider = new WifiSignalProvider() {
            @Override
            public Reading readSignal() {
                int t = tick.incrementAndGet();
                if (t == 2) {
                    // Temporary wrong BSSID sample should be ignored
                    return Reading.connected("adapter-1", "Wi-Fi", "Other", "ff:ff:ff:ff:ff:ff", "5 GHz", 36, 5180000, -48, 80, System.currentTimeMillis());
                }
                return Reading.connected("adapter-1", "Wi-Fi", "RoomAP", "aa:bb:cc:dd:ee:01", "5 GHz", 36, 5180000, -46, 90, System.currentTimeMillis());
            }

            @Override
            public void close() {
            }
        };

        ProximityCalibrationStore store = new ProximityCalibrationStore(tempDir.resolve("cal-step.json"));
        try (ProximityClientService service = new ProximityClientService(mockProvider, store, 10L, 100L, 22)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<ProximityClientService.CalibrationStepResult> resultRef = new AtomicReference<>();
            List<ProximityClientService.CalibrationProgress> progresses = new ArrayList<>();

            service.startCalibrationStep(
                1,
                "adapter-1",
                "aa:bb:cc:dd:ee:01",
                "5 GHz",
                progresses::add,
                res -> {
                    resultRef.set(res);
                    done.countDown();
                }
            );

            assertTrue(done.await(3, TimeUnit.SECONDS));
            var res = resultRef.get();
            assertNotNull(res);
            assertEquals(22, progresses.size());
            assertEquals(21, res.validSamples().size());
            assertTrue(res.evaluation().valid());
        }
    }
}
