package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class MusicAssetsTest {
    @Test void bothOriginalMusicFilesAreBundledUnchangedAndDecodeAsStereoPcm() throws Exception {
        try (var input = getClass().getResourceAsStream("/ui/audio/music-manifest.json")) {
            assertNotNull(input); var manifest = new ObjectMapper().readTree(input); assertEquals(2, manifest.size());
            for (var entry : manifest) {
                var track = MusicSlots.Track.valueOf(entry.path("track").asText());
                var url = getClass().getResource(MusicSlots.asset(track)); assertNotNull(url);
                assertTrue(MusicSlots.asset(track).endsWith(entry.path("file").asText()));
                try (var raw = url.openStream(); var pcm = AudioSystem.getAudioInputStream(url)) {
                    assertEquals(entry.path("sha256").asText(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.readAllBytes())));
                    assertEquals(AudioFormat.Encoding.PCM_SIGNED, pcm.getFormat().getEncoding());
                    assertEquals(44100, pcm.getFormat().getSampleRate()); assertEquals(2, pcm.getFormat().getChannels()); assertEquals(16, pcm.getFormat().getSampleSizeInBits());
                    double seconds = pcm.getFrameLength() / pcm.getFormat().getFrameRate();
                    assertTrue(seconds > 60 && seconds < 100); assertEquals(track == MusicSlots.Track.GAME, entry.path("loop").asBoolean());
                }
            }
        }
    }
}
