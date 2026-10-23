package vn.edu.hutech.nqb.admin;

/** Opt-in native audio smoke check: decode/open/close both actual clips; never start playback. */
public final class MusicDeviceCheck {
    public static void main(String[] args) throws Exception {
        for (var track : new MusicSlots.Track[]{MusicSlots.Track.GAME, MusicSlots.Track.VICTORY}) {
            try (var player = MusicSlots.openClip(track, () -> { throw new AssertionError("Playback was not requested"); })) {
                player.volume(.20);
                System.out.println("MUSIC_DEVICE_OPEN_CLOSE_OK " + track);
            }
        }
    }
}
