package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import javax.sound.sampled.AudioSystem;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class SfxAssetsTest {
    @Test void allSeventeenUserWavsMatchManifestAndArePlayablePcm() throws Exception {
        try (var manifest = getClass().getResourceAsStream("/ui/audio/manifest.json")) {
            var entries = new ObjectMapper().readTree(manifest); assertEquals(17, entries.size());
            for (var cue : SfxSlots.Cue.values()) {
                var url = getClass().getResource(SfxSlots.asset(cue)); assertNotNull(url);
                var metadata = entries.findValues("file"); assertTrue(metadata.stream().anyMatch(v -> SfxSlots.asset(cue).endsWith(v.asText())));
                try (var raw = url.openStream(); var pcm = AudioSystem.getAudioInputStream(url)) {
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.readAllBytes()));
                    var entry = java.util.stream.StreamSupport.stream(entries.spliterator(), false).filter(v -> SfxSlots.asset(cue).endsWith(v.path("file").asText())).findFirst().orElseThrow();
                    assertEquals(entry.path("sha256").asText(), hash);
                    assertEquals(44100, pcm.getFormat().getSampleRate()); assertEquals(1, pcm.getFormat().getChannels()); assertEquals(16, pcm.getFormat().getSampleSizeInBits());
                    assertEquals(entry.path("duration_seconds").asDouble(), pcm.getFrameLength() / pcm.getFormat().getFrameRate(), .01);
                }
            }
        }
    }
}
