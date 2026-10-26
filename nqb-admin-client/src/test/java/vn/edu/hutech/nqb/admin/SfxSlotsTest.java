package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.*;
import javax.sound.sampled.Clip;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class SfxSlotsTest {
    private Preferences prefs;
    private SfxSlots sfx;
    private final Map<SfxSlots.Cue, TestClip> clips = new ConcurrentHashMap<>();
    private final CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
    private volatile SfxSlots.Cue blocked;

    private static final class TestClip {
        final AtomicInteger starts = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        volatile boolean running;
        final Clip clip = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[]{Clip.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "start" -> { starts.incrementAndGet(); running = true; started.countDown(); yield null; }
                case "stop", "close" -> { running = false; yield null; }
                case "isRunning" -> running;
                case "isControlSupported" -> false;
                case "setFramePosition" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    @BeforeEach void setUp() {
        prefs = Preferences.userRoot().node("nqb-sfx-test-" + UUID.randomUUID());
        for (var cue : SfxSlots.Cue.values()) clips.put(cue, new TestClip());
        sfx = new SfxSlots(prefs, cue -> {
            if (cue == blocked) { loading.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); }
            return clips.get(cue).clip;
        });
        sfx.configure("TEACHER", false);
    }
    @AfterEach void tearDown() throws Exception { release.countDown(); sfx.close(); prefs.removeNode(); }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= until) fail("SFX worker timed out"); Thread.sleep(5); }
    }

    @Test void stopCancelsDelayedClickInsteadOfPlayingItAfterLeavingRoom() throws Exception {
        sfx.play(SfxSlots.Cue.UI_PRESS); sfx.stopPlaying();
        assertFalse(clips.get(SfxSlots.Cue.UI_PRESS).started.await(350, TimeUnit.MILLISECONDS));
    }
    @Test void stopDiscardsLoadingAndQueuedRoomCuesButAllowsNewUiCues() throws Exception {
        blocked = SfxSlots.Cue.ROUND_OPEN;
        sfx.play(blocked); assertTrue(loading.await(3, TimeUnit.SECONDS));
        sfx.play(SfxSlots.Cue.COUNTDOWN_TICK); sfx.stopPlaying();
        sfx.play(SfxSlots.Cue.UI_SUCCESS); release.countDown();
        assertTrue(clips.get(SfxSlots.Cue.UI_SUCCESS).started.await(3, TimeUnit.SECONDS));
        assertEquals(0, clips.get(SfxSlots.Cue.ROUND_OPEN).starts.get());
        assertEquals(0, clips.get(SfxSlots.Cue.COUNTDOWN_TICK).starts.get());
    }
    @Test void resetStopsActiveEffectsAndAllowsSameEventInNextRoom() throws Exception {
        var clip = clips.get(SfxSlots.Cue.COUNTDOWN_TICK);
        sfx.event(SfxSlots.Cue.COUNTDOWN_TICK, "one", 1);
        assertTrue(clip.started.await(3, TimeUnit.SECONDS));
        sfx.reset(); await(() -> !clip.running);
        sfx.event(SfxSlots.Cue.COUNTDOWN_TICK, "one", 1);
        await(() -> clip.starts.get() == 2);
    }
    @Test void muteDuringLoadCannotStartAnEffectAfterUnmuting() throws Exception {
        blocked = SfxSlots.Cue.ROUND_OPEN;
        sfx.play(blocked); assertTrue(loading.await(3, TimeUnit.SECONDS));
        sfx.mute(true); sfx.mute(false); sfx.play(SfxSlots.Cue.UI_SUCCESS); release.countDown();
        assertTrue(clips.get(SfxSlots.Cue.UI_SUCCESS).started.await(3, TimeUnit.SECONDS));
        assertEquals(0, clips.get(SfxSlots.Cue.ROUND_OPEN).starts.get());
    }
}
