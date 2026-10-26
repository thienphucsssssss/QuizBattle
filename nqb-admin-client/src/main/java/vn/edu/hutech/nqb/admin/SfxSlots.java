package vn.edu.hutech.nqb.admin;

import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;

/** Local, user-supplied PCM WAVs. Loading and device access never run on the FX thread. */
final class SfxSlots implements AutoCloseable {
    enum Cue { UI_PRESS, UI_SELECT, UI_TRANSITION, UI_SUCCESS, UI_WARNING, UI_ERROR,
        CONNECTION_UP, CONNECTION_DOWN, PLAYER_JOIN, PLAYER_READY, COUNTDOWN_TICK,
        ROUND_OPEN, ROUND_CLOSE, ANSWER_ACK, ANSWER_CORRECT, ANSWER_WRONG, MATCH_FINISH }
    @FunctionalInterface interface ClipFactory { Clip open(Cue cue) throws Exception; }
    private final Set<String> recent = new LinkedHashSet<>();
    private final Map<Cue, Clip> clips = new EnumMap<>(Cue.class);
    private final Preferences prefs;
    private final ClipFactory factory;
    private final ScheduledThreadPoolExecutor audio = new ScheduledThreadPoolExecutor(1, r -> {
        var thread = new Thread(r, "nqb-local-sfx"); thread.setDaemon(true); return thread;
    });
    private volatile boolean muted = true, quiet, closed;
    private volatile double volume = .35;
    private volatile String deviceStatus = "Âm thanh sẵn sàng";
    private String profile = "TEACHER";
    private long lastUi;
    private volatile long lastSemantic;
    private volatile long generation;
    SfxSlots() { this(Preferences.userNodeForPackage(SfxSlots.class), SfxSlots::openClip); }
    SfxSlots(Preferences prefs, ClipFactory factory) { this.prefs = prefs; this.factory = factory; audio.setRemoveOnCancelPolicy(true); }
    boolean assetsAvailable() { return Arrays.stream(Cue.values()).allMatch(c -> SfxSlots.class.getResource(asset(c)) != null); }
    static String asset(Cue cue) { return "/ui/audio/" + cue.name().toLowerCase(Locale.ROOT) + ".wav"; }
    synchronized void configure(String role, boolean silentQa) {
        profile = role.equals("STUDENT") ? "STUDENT" : "TEACHER"; quiet = silentQa;
        muted = prefs.getBoolean(profile + ".muted", role.equals("STUDENT"));
        volume = Math.clamp(prefs.getDouble(profile + ".volume", .35), 0, 1);
        reset();
    }
    boolean muted() { return muted; }
    double volume() { return volume; }
    String status() { return deviceStatus; }
    synchronized void mute(boolean value) {
        muted = value; if (!quiet) prefs.putBoolean(profile + ".muted", value);
        if (value) stopPlaying();
    }
    void volume(double value) { volume = Math.clamp(value, 0, 1); if (!quiet) prefs.putDouble(profile + ".volume", volume); }
    synchronized void play(Cue cue) {
        if (muted || quiet || closed || volume == 0) return;
        long now = System.nanoTime();
        boolean ui = cue == Cue.UI_PRESS || cue == Cue.UI_SELECT;
        if (ui && now - lastUi < TimeUnit.MILLISECONDS.toNanos(120)) return;
        if (ui) lastUi = now; else lastSemantic = now;
        long token = generation;
        // Delay generic clicks slightly so semantic confirmation can replace them.
        enqueue(() -> {
            if (ui && lastSemantic >= now - TimeUnit.MILLISECONDS.toNanos(100)) return;
            if (!canPlay(token)) return;
            try {
                Clip clip = clips.get(cue);
                boolean opened = clip == null;
                if (opened) clip = factory.open(cue);
                synchronized (this) {
                    // A room change may happen while the device is loading a clip.
                    if (!canPlay(token)) { if (opened) clip.close(); return; }
                    if (opened) clips.put(cue, clip);
                    if (!ui) for (var entry : clips.entrySet()) if (entry.getKey() == Cue.UI_PRESS || entry.getKey() == Cue.UI_SELECT) entry.getValue().stop();
                    if (cue == Cue.MATCH_FINISH || clips.values().stream().filter(Clip::isRunning).count() >= 3) clips.values().forEach(Clip::stop);
                    clip.stop(); clip.setFramePosition(0);
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        var gain = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue((float)Math.clamp(20 * Math.log10(Math.max(.001, volume)), gain.getMinimum(), gain.getMaximum()));
                    }
                    clip.start(); deviceStatus = "Âm thanh sẵn sàng";
                }
            } catch (Exception unavailable) { deviceStatus = "Không mở được thiết bị âm thanh"; }
        }, ui ? 100 : 0);
    }
    private boolean canPlay(long token) { return token == generation && !muted && !quiet && !closed && volume > 0; }
    private void enqueue(Runnable work, long delayMs) {
        if (closed || audio.getQueue().size() >= 24) return;
        try { audio.schedule(work, delayMs, TimeUnit.MILLISECONDS); } catch (RejectedExecutionException ignored) { }
    }
    synchronized void event(Cue cue, String session, long questionId) {
        String key = session + ":" + questionId + ":" + cue;
        if (!recent.add(key)) return;
        if (recent.size() > 128) recent.remove(recent.iterator().next());
        play(cue);
    }
    synchronized void reset() { recent.clear(); lastUi = lastSemantic = 0; stopPlaying(); }
    synchronized void stopPlaying() {
        if (closed) return;
        generation++;
        audio.getQueue().clear();
        enqueue(() -> clips.values().forEach(Clip::stop), 0);
    }
    private static Clip openClip(Cue cue) throws Exception {
        try (var input = AudioSystem.getAudioInputStream(Objects.requireNonNull(SfxSlots.class.getResource(asset(cue))))) {
            var clip = AudioSystem.getClip();
            try { clip.open(input); return clip; }
            catch (Exception failure) { clip.close(); throw failure; }
        }
    }
    @Override public synchronized void close() {
        if (closed) return; muted = true; closed = true;
        generation++;
        audio.getQueue().clear();
        try { audio.execute(() -> { clips.values().forEach(Clip::close); clips.clear(); }); } catch (RejectedExecutionException ignored) { }
        audio.shutdown();
    }
}
