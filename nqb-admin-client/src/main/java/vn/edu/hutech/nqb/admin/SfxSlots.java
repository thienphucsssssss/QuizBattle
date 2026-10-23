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
    private final Set<String> recent = new LinkedHashSet<>();
    private final Map<Cue, Clip> clips = new EnumMap<>(Cue.class);
    private final Preferences prefs = Preferences.userNodeForPackage(SfxSlots.class);
    private final ScheduledThreadPoolExecutor audio = new ScheduledThreadPoolExecutor(1, r -> {
        var thread = new Thread(r, "nqb-local-sfx"); thread.setDaemon(true); return thread;
    });
    private volatile boolean muted = true, quiet, closed;
    private volatile double volume = .35;
    private volatile String deviceStatus = "Âm thanh sẵn sàng";
    private String profile = "TEACHER";
    private long lastUi;
    private volatile long lastSemantic;
    SfxSlots() { audio.setRemoveOnCancelPolicy(true); }
    boolean assetsAvailable() { return Arrays.stream(Cue.values()).allMatch(c -> SfxSlots.class.getResource(asset(c)) != null); }
    static String asset(Cue cue) { return "/ui/audio/" + cue.name().toLowerCase(Locale.ROOT) + ".wav"; }
    void configure(String role, boolean silentQa) {
        profile = role.equals("STUDENT") ? "STUDENT" : "TEACHER"; quiet = silentQa;
        muted = prefs.getBoolean(profile + ".muted", role.equals("STUDENT"));
        volume = Math.clamp(prefs.getDouble(profile + ".volume", .35), 0, 1);
        reset();
    }
    boolean muted() { return muted; }
    double volume() { return volume; }
    String status() { return deviceStatus; }
    void mute(boolean value) {
        muted = value; if (!quiet) prefs.putBoolean(profile + ".muted", value);
        if (value) enqueue(() -> clips.values().forEach(Clip::stop), 0);
    }
    void volume(double value) { volume = Math.clamp(value, 0, 1); if (!quiet) prefs.putDouble(profile + ".volume", volume); }
    void play(Cue cue) {
        if (muted || quiet || closed || volume == 0) return;
        long now = System.nanoTime();
        boolean ui = cue == Cue.UI_PRESS || cue == Cue.UI_SELECT;
        if (ui && now - lastUi < TimeUnit.MILLISECONDS.toNanos(120)) return;
        if (ui) lastUi = now; else lastSemantic = now;
        // Delay generic clicks slightly so semantic confirmation can replace them.
        enqueue(() -> {
            if (ui && lastSemantic >= now - TimeUnit.MILLISECONDS.toNanos(100)) return;
            if (muted || quiet || closed || volume == 0) return;
            try {
                Clip clip = clips.get(cue);
                if (clip == null) {
                    var resource = SfxSlots.class.getResource(asset(cue)); if (resource == null) return;
                    try (var input = AudioSystem.getAudioInputStream(resource)) {
                        clip = AudioSystem.getClip(); clip.open(input); clips.put(cue, clip);
                    }
                }
                if (!ui) for (var entry : clips.entrySet()) if (entry.getKey() == Cue.UI_PRESS || entry.getKey() == Cue.UI_SELECT) entry.getValue().stop();
                if (cue == Cue.MATCH_FINISH || clips.values().stream().filter(Clip::isRunning).count() >= 3) clips.values().forEach(Clip::stop);
                clip.stop(); clip.setFramePosition(0);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    var gain = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue((float)Math.clamp(20 * Math.log10(Math.max(.001, volume)), gain.getMinimum(), gain.getMaximum()));
                }
                clip.start(); deviceStatus = "Âm thanh sẵn sàng";
            } catch (Exception unavailable) { deviceStatus = "Không mở được thiết bị âm thanh"; }
        }, ui ? 100 : 0);
    }
    private void enqueue(Runnable work, long delayMs) {
        if (closed || audio.getQueue().size() >= 24) return;
        try { audio.schedule(work, delayMs, TimeUnit.MILLISECONDS); } catch (RejectedExecutionException ignored) { }
    }
    void event(Cue cue, String session, long questionId) {
        String key = session + ":" + questionId + ":" + cue;
        if (!recent.add(key)) return;
        if (recent.size() > 128) recent.remove(recent.iterator().next());
        play(cue);
    }
    void reset() { recent.clear(); }
    void stopPlaying() { enqueue(() -> clips.values().forEach(Clip::stop), 0); }
    @Override public void close() {
        if (closed) return; muted = true; closed = true;
        audio.getQueue().clear();
        try { audio.execute(() -> { clips.values().forEach(Clip::close); clips.clear(); }); } catch (RejectedExecutionException ignored) { }
        audio.shutdown();
    }
}
