package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class MusicSlotsTest {
    private Preferences prefs;
    private MusicSlots music;
    private final List<FakePlayer> opened = new CopyOnWriteArrayList<>();
    private class FakePlayer implements MusicSlots.Player {
        final MusicSlots.Track track;
        final Runnable finished;
        volatile boolean started, loop, closed;
        volatile double volume;
        FakePlayer(MusicSlots.Track track, Runnable finished) { this.track = track; this.finished = finished; }
        public void start(boolean loop) { this.loop = loop; started = true; }
        public void volume(double value) { volume = value; }
        public void close() { closed = true; }
    }
    @BeforeEach void setUp() {
        prefs = Preferences.userRoot().node("nqb-music-test-" + UUID.randomUUID());
        music = new MusicSlots(prefs, (track, finished) -> { var clip = new FakePlayer(track, finished); opened.add(clip); return clip; });
        music.configure("TEACHER", false);
    }
    @AfterEach void tearDown() throws Exception { music.close(); prefs.removeNode(); }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= until) fail("Music worker timed out"); Thread.sleep(5); }
    }
    private FakePlayer startGame() throws Exception { music.update("one", "COUNTDOWN", false); await(() -> !opened.isEmpty() && opened.getLast().started); return opened.getLast(); }

    @Test void loopContinuesAcrossQuestionsResultsAndRankingWithoutReloading() throws Exception {
        var clip = startGame(); assertTrue(clip.loop);
        for (String phase : List.of("QUESTION_ACTIVE", "SHOW_RESULT", "SHOW_RANKING", "QUESTION_ACTIVE")) music.update("one", phase, false);
        music.volume(.13); await(() -> clip.volume == .13);
        assertEquals(1, opened.size()); assertFalse(clip.closed); assertEquals(MusicSlots.Track.GAME, music.desiredTrack());
    }
    @Test void finishedMatchStopsMusicAndRepeatedStateOrSettingsCannotRestartIt() throws Exception {
        var game = startGame(); music.update("one", "FINISHED", false);
        await(() -> game.closed);
        assertEquals(MusicSlots.Track.NONE, music.desiredTrack());
        music.update("one", "FINISHED", false); music.volume(.17); music.mute(true); music.mute(false);
        music.close(); assertEquals(1, opened.size());
    }
    @Test void newMatchCanPlayAfterPreviousMatchFinished() throws Exception {
        var game = startGame(); music.update("one", "FINISHED", false); await(() -> game.closed);
        music.update("two", "COUNTDOWN", false); await(() -> opened.size() >= 2 && opened.getLast().track == MusicSlots.Track.GAME && opened.getLast().started);
        assertEquals(2, opened.size());
        assertEquals(MusicSlots.Track.GAME, opened.getLast().track);
    }
    @Test void abortDoesNotPlayVictoryAndWaitingStopsMusic() throws Exception {
        var game = startGame(); music.update("one", "FINISHED", true); await(() -> game.closed);
        assertEquals(MusicSlots.Track.NONE, music.desiredTrack()); assertEquals(1, opened.size());
        music.update("two", "QUESTION_ACTIVE", false); await(() -> opened.size() == 2 && opened.getLast().started);
        var second = opened.getLast(); music.update("two", "WAITING", false); await(() -> second.closed);
    }
    @Test void volumeUpdatesCurrentClipAndZeroReleasesIt() throws Exception {
        var clip = startGame(); music.volume(.32); await(() -> clip.volume == .32);
        assertEquals(1, opened.size()); music.volume(0); await(() -> clip.closed);
        music.volume(.1); await(() -> opened.size() == 2 && opened.getLast().started);
        assertEquals(.1, opened.getLast().volume);
    }
    @Test void muteStopsAndUnmuteResumesCurrentGame() throws Exception {
        var clip = startGame(); music.mute(true); await(() -> clip.closed);
        music.mute(false); await(() -> opened.size() == 2 && opened.getLast().started);
        music.stop(); await(() -> opened.getLast().closed); assertEquals(MusicSlots.Track.NONE, music.desiredTrack());
    }
    @Test void teacherAndStudentHaveIndependentPreferencesAndQaNeverWritesThem() {
        assertFalse(music.muted()); assertEquals(.20, music.volume());
        music.volume(.12); music.configure("STUDENT", false); assertTrue(music.muted());
        music.mute(false); music.volume(.25); music.configure("ADMIN", false); assertFalse(music.muted()); assertEquals(.12, music.volume());
        music.configure("STUDENT", true); music.mute(true); music.volume(.99);
        music.configure("STUDENT", false); assertFalse(music.muted()); assertEquals(.25, music.volume());
    }
    @Test void qaAndDefaultStudentNeverOpenAudioEvenWhenAStateIsRequested() throws Exception {
        music.configure("STUDENT", false); music.update("one", "QUESTION_ACTIVE", false);
        music.configure("TEACHER", true); music.mute(false); music.update("one", "FINISHED", false);
        music.close(); assertTrue(opened.isEmpty());
        assertEquals(MusicSlots.Track.NONE, music.desiredTrack());
    }
    @Test void staleLoadCannotStartAfterLeavingRoom() throws Exception {
        music.close(); var loading = new CountDownLatch(1); var release = new CountDownLatch(1);
        music = new MusicSlots(prefs, (track, finished) -> { loading.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); var clip = new FakePlayer(track, finished); opened.add(clip); return clip; });
        music.configure("TEACHER", false); music.update("one", "COUNTDOWN", false); assertTrue(loading.await(3, TimeUnit.SECONDS));
        music.stop(); release.countDown(); await(() -> opened.size() == 1 && opened.getFirst().closed);
        assertFalse(opened.getFirst().started);
    }
    @Test void staleLoadCannotStartAfterMatchFinished() throws Exception {
        music.close(); var loading = new CountDownLatch(1); var release = new CountDownLatch(1);
        music = new MusicSlots(prefs, (track, finished) -> { loading.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); var clip = new FakePlayer(track, finished); opened.add(clip); return clip; });
        music.configure("TEACHER", false); music.update("one", "COUNTDOWN", false); assertTrue(loading.await(3, TimeUnit.SECONDS));
        music.update("one", "FINISHED", false); release.countDown(); await(() -> opened.size() == 1 && opened.getFirst().closed);
        assertFalse(opened.getFirst().started);
    }
    @Test void closeReleasesActiveMusicAndIgnoresLaterEvents() throws Exception {
        var clip = startGame(); music.close(); await(() -> clip.closed);
        music.update("one", "FINISHED", false); assertEquals(1, opened.size());
    }
    @Test void unavailableAudioDeviceDoesNotEscapeIntoGameThread() throws Exception {
        music.close(); var attempted = new CountDownLatch(1);
        music = new MusicSlots(prefs, (track, finished) -> { attempted.countDown(); throw new IllegalStateException("Test-only unavailable device"); });
        music.configure("TEACHER", false); assertDoesNotThrow(() -> music.update("one", "COUNTDOWN", false));
        assertTrue(attempted.await(3, TimeUnit.SECONDS)); await(() -> music.status().startsWith("Không mở"));
    }
}
