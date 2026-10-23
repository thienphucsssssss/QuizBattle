package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** UI-neutral boundary. Preview is explicitly selected, never a network fallback. */
public interface QuizGateway extends AutoCloseable {
    CompletableFuture<JsonNode> request(String type, ObjectNode payload);
    void onEvent(Consumer<JsonNode> listener);
    boolean isPreview();
    @Override void close();

    static ObjectNode object() { return JsonNodeFactory.instance.objectNode(); }
}
