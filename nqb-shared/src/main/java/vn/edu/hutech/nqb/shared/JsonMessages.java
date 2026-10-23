package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Envelope validation only; per-message payload and authorization belong to handlers. */
public final class JsonMessages {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonMessages() { }

    public static JsonNode parse(String frame) throws IOException {
        if (frame.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds 64 KiB");
        }
        JsonNode message = MAPPER.readTree(frame);
        if (message == null || !message.isObject()
                || !message.path("protocol_version").isIntegralNumber()
                || !message.path("protocol_version").canConvertToInt()
                || message.path("protocol_version").intValue() != Protocol.VERSION
                || !message.path("type").isTextual()
                || message.path("type").textValue().isBlank()
                || !message.path("payload").isObject()) {
            throw new IOException("Invalid protocol envelope");
        }
        return message;
    }

    public static byte[] encode(JsonNode message) throws IOException {
        String frame = MAPPER.writeValueAsString(message);
        parse(frame);
        return (frame + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
