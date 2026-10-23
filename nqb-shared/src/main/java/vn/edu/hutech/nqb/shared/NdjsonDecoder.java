package vn.edu.hutech.nqb.shared;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** One decoder per connection, called only by its reader thread. */
public final class NdjsonDecoder {
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public List<String> feed(byte[] bytes) throws IOException {
        List<String> frames = new ArrayList<>();
        for (byte value : bytes) {
            if (value == '\n') {
                if (pending.size() == 0) {
                    throw new IOException("Empty frame");
                }
                var decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                frames.add(decoder.decode(ByteBuffer.wrap(pending.toByteArray())).toString());
                pending.reset();
            } else {
                if (pending.size() >= Protocol.MAX_FRAME_BYTES) {
                    throw new IOException("Frame exceeds 64 KiB");
                }
                pending.write(value);
            }
        }
        return frames;
    }

    public void endOfInput() throws IOException {
        if (pending.size() != 0) {
            throw new IOException("Connection ended with an incomplete frame");
        }
    }
}
