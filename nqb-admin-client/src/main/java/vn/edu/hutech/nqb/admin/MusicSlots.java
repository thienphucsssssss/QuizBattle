package vn.edu.hutech.nqb.admin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;

/** One local music voice, separate from short SFX. All device work stays off the FX thread. */
final class MusicSlots implements AutoCloseable {
    enum Track { NONE, GAME, VICTORY }
    private record Playback(String session, Track track) { }
    interface Player extends AutoCloseable {
        void start(boolean loop);
        void volume(double value);
        @Override void close();
    }
    @FunctionalInterface interface PlayerFactory { Player open(Track track, Runnable finished) throws Exception; }

    private static final Playback SILENCE = new Playback("", Track.NONE);
    private final Preferences prefs;
    private final PlayerFactory factory;
    private final ExecutorService audio = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "nqb-local-music"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean();
    private final Set<String> completed = new LinkedHashSet<>();
    private Playback desired = SILENCE;
    private long generation;
    private boolean muted = true, quiet, closed;
    private double volume = .20;
    private String profile = "TEACHER";
    private volatile String status = "Nhạc nền sẵn sàng";
    // Only the audio worker owns the native player.
    private Playback active = SILENCE;
    private long activeGeneration = -1;
    private Player player;

    MusicSlots() { this(Preferences.userNodeForPackage(MusicSlots.class).node("music"), MusicSlots::openClip); }
    MusicSlots(Preferences prefs, PlayerFactory factory) { this.prefs = prefs; this.factory = factory; }
    static String asset(Track track) {
        return switch (track) {
            case GAME -> "/ui/audio/music_game.wav";
            case VICTORY -> "/ui/audio/music_victory.wav";
            case NONE -> throw new IllegalArgumentException("Silence has no asset");
        };
    }
    boolean assetsAvailable() { return getClass().getResource(asset(Track.GAME)) != null && getClass().getResource(asset(Track.VICTORY)) != null; }
    synchronized void configure(String role, boolean silentQa) {
        profile = role.equals("STUDENT") ? "STUDENT" : "TEACHER";
        quiet = silentQa;
        muted = prefs.getBoolean(profile + ".muted", role.equals("STUDENT"));
        volume = Math.clamp(prefs.getDouble(profile + ".volume", .20), 0, 1);
        desired = SILENCE; generation++; reconcileLater();
    }
    synchronized boolean muted() { return muted; }
    synchronized double volume() { return volume; }
    synchronized boolean enabled() { return !closed && !muted && !quiet && volume > 0 && assetsAvailable(); }
    String status() { return status; }
    synchronized Track desiredTrack() { return desired.track(); }
    synchronized void mute(boolean value) {
        muted = value; generation++;
        if (!quiet) prefs.putBoolean(profile + ".muted", value);
        reconcileLater();
    }
    synchronized void volume(double value) {
        volume = Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
        if (!quiet) prefs.putDouble(profile + ".volume", volume);
        reconcileLater();
    }
    synchronized void update(String session, String phase, boolean aborted) {
        Track track = session == null || session.isBlank() || aborted ? Track.NONE : switch (phase) {
            case "COUNTDOWN", "QUESTION_ACTIVE", "SHOW_RESULT", "SHOW_RANKING" -> Track.GAME;
            default -> Track.NONE;
        };
        Playback next = track == Track.NONE ? SILENCE : new Playback(session, track);
        if (closed || next.equals(desired)) return;
        desired = next; generation++; reconcileLater();
    }
    synchronized void stop() { update("", "WAITING", false); }
    private void reconcileLater() {
        if (closed || !queued.compareAndSet(false, true)) return;
        try { audio.execute(() -> { queued.set(false); reconcile(); }); }
        catch (RejectedExecutionException ignored) { queued.set(false); }
    }
    private void reconcile() {
        Playback next; long token; double level;
        synchronized (this) {
            next = desired; token = generation; level = volume;
            if (closed || muted || quiet || level == 0 || next.track() == Track.NONE ||
                    (next.track() == Track.VICTORY && completed.contains(next.session()))) next = SILENCE;
        }
        if (next.equals(active) && token == activeGeneration && player != null) {
            try { player.volume(level); } catch (Exception unavailable) { release(); status = "Không mở được thiết bị phát nhạc"; }
            return;
        }
        release();
        if (next.track() == Track.NONE) {
            synchronized (this) {
                status = muted ? "Nhạc nền đã tắt" : quiet ? "Kiểm thử im lặng" : volume == 0 ? "Âm lượng nhạc: 0%" :
                        desired.track() == Track.VICTORY && completed.contains(desired.session()) ? "Đã phát xong nhạc chung cuộc" : "Nhạc nền sẵn sàng";
            }
            return;
        }
        final Playback requested = next;
        Player opened = null;
        try {
            opened = factory.open(next.track(), () -> finished(requested, token));
            synchronized (this) {
                // Loading can finish after logout/mute/another phase: never start that stale clip.
                if (closed || muted || quiet || volume == 0 || token != generation || !requested.equals(desired)) { opened.close(); return; }
                player = opened; active = requested; activeGeneration = token;
                player.volume(volume); player.start(next.track() == Track.GAME);
                status = next.track() == Track.GAME ? "Đang phát nhạc ván đấu" : "Đang phát nhạc chung cuộc";
            }
        } catch (Exception unavailable) {
            if (opened != null) try { opened.close(); } catch (Exception ignored) { }
            player = null; active = SILENCE; status = "Không mở được thiết bị phát nhạc";
        }
    }
    private synchronized void finished(Playback playback, long token) {
        if (closed || token != generation || !playback.equals(desired) || playback.track() != Track.VICTORY) return;
        completed.add(playback.session());
        if (completed.size() > 128) completed.remove(completed.iterator().next());
        status = "Đã phát xong nhạc chung cuộc";
        reconcileLater();
    }
    private void release() {
        if (player != null) try { player.close(); } catch (Exception ignored) { }
        player = null; active = SILENCE;
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; desired = SILENCE; generation++;
        audio.execute(this::release); audio.shutdown();
    }
    static Player openClip(Track track, Runnable finished) throws Exception {
        Clip clip = AudioSystem.getClip();
        try (var input = AudioSystem.getAudioInputStream(Objects.requireNonNull(MusicSlots.class.getResource(asset(track))))) {
            clip.open(input);
        } catch (Exception failure) { clip.close(); throw failure; }
        return new Player() {
            private volatile boolean released;
            {
                clip.addLineListener(event -> {
                    if (!released && track == Track.VICTORY && event.getType() == LineEvent.Type.STOP && clip.getFramePosition() >= clip.getFrameLength()) finished.run();
                });
            }
            public void start(boolean loop) {
                clip.setFramePosition(0);
                if (loop) clip.loop(Clip.LOOP_CONTINUOUSLY); else clip.start();
            }
            public void volume(double value) {
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    var gain = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue((float)Math.clamp(20 * Math.log10(Math.max(.0001, value)), gain.getMinimum(), gain.getMaximum()));
                }
            }
            public void close() { released = true; clip.stop(); clip.close(); }
        };
    }
}
