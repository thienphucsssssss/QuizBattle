package vn.edu.hutech.nqb.shared;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private static final String MESSAGE = "{\"protocol_version\":1,\"type\":\"JOIN\","
            + "\"payload\":{\"nickname\":\"Nguyễn Trường Thi\"}}";

    @Test void vietnameseBytesMayBeSplitAnywhere() throws Exception {
        var decoder = new NdjsonDecoder();
        var frames = new ArrayList<String>();
        for (byte value : (MESSAGE + "\n").getBytes(StandardCharsets.UTF_8)) {
            frames.addAll(decoder.feed(new byte[]{value}));
        }
        assertEquals(List.of(MESSAGE), frames);
        decoder.endOfInput();
    }

    @Test void coalescedFramesAndPartialTail() throws Exception {
        var decoder = new NdjsonDecoder();
        assertEquals(List.of(MESSAGE, MESSAGE), decoder.feed(
                (MESSAGE + "\n" + MESSAGE + "\n{\"type\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, decoder::endOfInput);
    }

    @Test void emptyAndMalformedUtf8Rejected() {
        assertThrows(IOException.class, () -> new NdjsonDecoder().feed(new byte[]{'\n'}));
        assertThrows(IOException.class, () -> new NdjsonDecoder().feed(new byte[]{(byte) 0xc3, '\n'}));
    }

    @Test void exactFrameLimitAndOversize() throws Exception {
        byte[] frame = new byte[Protocol.MAX_FRAME_BYTES + 1];
        Arrays.fill(frame, (byte) 'a');
        frame[frame.length - 1] = '\n';
        assertEquals(Protocol.MAX_FRAME_BYTES, new NdjsonDecoder().feed(frame).getFirst().length());
        frame[frame.length - 1] = 'a';
        assertThrows(IOException.class, () -> new NdjsonDecoder().feed(frame));
    }

    @Test void jsonRoundTripUsesOneUtf8Line() throws Exception {
        var message = JsonMessages.parse(MESSAGE);
        var decoder = new NdjsonDecoder();
        var frames = decoder.feed(JsonMessages.encode(message));
        assertEquals(1, frames.size());
        assertEquals(message, JsonMessages.parse(frames.getFirst()));
    }

    @Test void invalidEnvelopeAndTrailingJsonRejected() {
        for (String value : List.of("[]", "{}", "null", MESSAGE + " {}",
                MESSAGE.replace("\"protocol_version\":1", "\"protocol_version\":2"),
                MESSAGE.replace("\"JOIN\"", "\"\""))) {
            assertThrows(IOException.class, () -> JsonMessages.parse(value));
        }
    }
}
