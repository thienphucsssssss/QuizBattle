package vn.edu.hutech.nqb.client;

import static vn.edu.hutech.nqb.client.QuizGateway.object;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.JsonMessages;
import vn.edu.hutech.nqb.shared.NdjsonDecoder;
import vn.edu.hutech.nqb.shared.Protocol;
import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** TLS-only transport. No trust-all, no plaintext retry and no fixture fallback. */
public final class TlsQuizGateway implements QuizGateway {
    private final SSLSocket socket;
    private final BlockingQueue<byte[]> outbound = new ArrayBlockingQueue<>(100);
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "quiz-timeouts"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Consumer<JsonNode> listener = ignored -> { };

    public static TlsQuizGateway connect(Endpoint endpoint, Path truststore, char[] password) throws Exception {
        return connect(endpoint,truststore,password,5000);
    }
    public static TlsQuizGateway connect(Endpoint endpoint, Path truststore, char[] password, int timeoutMillis) throws Exception {
        if(timeoutMillis<1||timeoutMillis>5000)throw new IllegalArgumentException("Invalid TLS timeout");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(truststore)) { store.load(input, password); }
        for (var aliases = store.aliases(); aliases.hasMoreElements();) {
            if (store.isKeyEntry(aliases.nextElement())) throw new IllegalArgumentException("Client chỉ sử dụng chứng chỉ công khai, không sử dụng khóa bí mật Server.");
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        Socket transport = new Socket();
        SSLSocket socket = null;
        try {
            transport.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMillis);
            socket = (SSLSocket) context.getSocketFactory().createSocket(transport, endpoint.host(), endpoint.port(), true);
            socket.setSoTimeout(timeoutMillis);
            socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            socket.setSoTimeout(0);
            return new TlsQuizGateway(socket);
        } catch (Exception error) { if (socket != null) socket.close(); else transport.close(); throw error; }
    }

    private TlsQuizGateway(SSLSocket socket) {
        this.socket = socket;
        Thread.ofVirtual().name("quiz-reader").start(this::read);
        Thread.ofVirtual().name("quiz-writer").start(this::write);
        timer.scheduleAtFixedRate(() -> {
            if (!closed.get()) request("PING", object()).exceptionally(error -> { fail(); return null; });
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override public synchronized CompletableFuture<JsonNode> request(String type, ObjectNode payload) {
        if (closed.get()) return CompletableFuture.failedFuture(new GatewayException("DISCONNECTED", "Đã mất kết nối. Hãy kết nối lại.", object()));
        String id = UUID.randomUUID().toString();
        var result = new CompletableFuture<JsonNode>();
        if (pending.size() >= 64) return CompletableFuture.failedFuture(new GatewayException("BUSY", "Có quá nhiều yêu cầu đang chờ.", object()));
        var message = object().put("protocol_version", Protocol.VERSION).put("type", type).put("request_id", id);
        var body = payload.deepCopy();
        if (body.has("room_code")) message.set("room_code", body.remove("room_code"));
        if (body.has("session_id") && !type.startsWith("HISTORY_")) message.set("session_id", body.remove("session_id"));
        message.set("payload", body);
        pending.put(id, result);
        try {
            if (!outbound.offer(JsonMessages.encode(message))) throw new IllegalStateException("Outbound queue full");
        } catch (Exception error) {
            pending.remove(id);
            result.completeExceptionally(new GatewayException("SEND_FAILED", "Không gửi được yêu cầu. Hãy thử lại.", object()));
        }
        try {
            ScheduledFuture<?> timeout = timer.schedule(() -> {
                var waiting = pending.remove(id);
                if (waiting != null) waiting.completeExceptionally(new GatewayException("TIMEOUT", "Máy chủ chưa phản hồi. Kiểm tra kết nối trước khi thử lại.", object()));
            }, type.equals("PING") ? 20 : 10, TimeUnit.SECONDS);
            result.whenComplete((value, error) -> timeout.cancel(false));
        } catch (RejectedExecutionException stopped) {
            pending.remove(id); result.completeExceptionally(new GatewayException("DISCONNECTED", "Đã mất kết nối với máy chủ.", object()));
        }
        return result;
    }

    private void read() {
        try {
            var decoder = new NdjsonDecoder();
            byte[] buffer = new byte[8192];
            int count;
            while (!closed.get() && (count = socket.getInputStream().read(buffer)) != -1) {
                for (String frame : decoder.feed(java.util.Arrays.copyOf(buffer, count))) {
                    JsonNode message = JsonMessages.parse(frame);
                    var future = pending.remove(message.path("request_id").asText());
                    JsonNode payload = message.path("payload");
                    if (payload instanceof ObjectNode body && message.has("session_id")) body.set("session_id", message.get("session_id"));
                    String type = message.path("type").asText();
                    if (future != null) {
                        if (type.equals("ERROR") || type.equals("LOGIN_ERROR")) {
                            future.completeExceptionally(new GatewayException(payload.path("code").asText("ERROR"), payload.path("message").asText("Yêu cầu bị từ chối."), payload));
                        } else {
                            future.complete(payload);
                            // Some control replies are also state transitions, not only ACKs.
                            if (type.equals("GAME_START") || type.equals("GAME_ABORTED")) listener.accept(message);
                        }
                    } else listener.accept(message);
                }
            }
        } catch (Exception ignored) { /* Never log authentication payloads or credentials. */ }
        finally { fail(); }
    }
    private void write() {
        try {
            while (!closed.get()) {
                byte[] message = outbound.poll(1, TimeUnit.SECONDS);
                if (message != null) { socket.getOutputStream().write(message); socket.getOutputStream().flush(); }
            }
        } catch (Exception ignored) { fail(); }
    }
    private synchronized void fail() {
        if (closed.compareAndSet(false, true)) {
            try { socket.close(); } catch (Exception ignored) { }
            timer.shutdownNow(); outbound.clear();
            pending.values().forEach(f -> f.completeExceptionally(new GatewayException("DISCONNECTED", "Đã mất kết nối với máy chủ.", object())));
            pending.clear();
            listener.accept(object().put("type", "DISCONNECTED").set("payload", object()));
        }
    }
    @Override public void onEvent(Consumer<JsonNode> listener) { this.listener = listener; }
    @Override public boolean isPreview() { return false; }
    @Override public void close() { fail(); }
}
