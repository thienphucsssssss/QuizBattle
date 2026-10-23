package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.shared.JsonMessages;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;
import static org.junit.jupiter.api.Assertions.*;

/** Ephemeral localhost TLS peer, not the production Server or multi-machine LAN. */
class TlsQuizGatewayTest {
    @TempDir static Path temporary;
    private static char[] password;
    private static Path valid, wrongName, emptyTrust;
    @BeforeAll static void certificates() throws Exception {
        password = UUID.randomUUID().toString().toCharArray();
        valid = certificate("valid", "dns:localhost,ip:127.0.0.1");
        wrongName = certificate("wrong-name", "dns:not-local.invalid");
        emptyTrust = temporary.resolve("empty.p12");
        var empty = KeyStore.getInstance("PKCS12"); empty.load(null, password);
        try (var out = Files.newOutputStream(emptyTrust)) { empty.store(out, password); }
    }
    private static Path certificate(String name, String san) throws Exception {
        Path path = temporary.resolve(name + ".p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "nqb-test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=Quiz Test", "-ext", "SAN=" + san,
            "-storetype", "PKCS12", "-keystore", path.toString(), "-storepass", new String(password), "-keypass", new String(password), "-noprompt").redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Test certificate generation timed out"); }
        assertEquals(0, process.exitValue(), "Unable to generate ephemeral test certificate"); return path;
    }
    private static Path trust(Path key) throws Exception {
        Path file = key.resolveSibling(key.getFileName()+".trust.p12");
        var privateStore = KeyStore.getInstance("PKCS12"); try (var input = Files.newInputStream(key)) { privateStore.load(input,password); }
        var publicStore = KeyStore.getInstance("PKCS12"); publicStore.load(null,password); publicStore.setCertificateEntry("nqb-test",privateStore.getCertificate("nqb-test"));
        try (var output = Files.newOutputStream(file)) { publicStore.store(output,password); } return file;
    }
    private static SSLContext context(Path path) throws Exception {
        var keys = KeyStore.getInstance("PKCS12"); try (var in = Files.newInputStream(path)) { keys.load(in, password); }
        var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); factory.init(keys, password);
        var context = SSLContext.getInstance("TLS"); context.init(factory.getKeyManagers(), null, null); return context;
    }
    @FunctionalInterface private interface Handler { void run(SSLSocket socket) throws Exception; }
    private static final class Peer implements AutoCloseable {
        final SSLServerSocket server; final CompletableFuture<Void> served;
        Peer(Path certificate, Handler handler) throws Exception {
            server = (SSLServerSocket)context(certificate).getServerSocketFactory().createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(5000);
            served = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> {
                try (var socket = (SSLSocket)server.accept()) { socket.setSoTimeout(5000); socket.startHandshake(); handler.run(socket); served.complete(null); }
                catch (Exception error) { served.completeExceptionally(error); }
            });
        }
        int port() { return server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); }
    }
    private JsonNode read(SSLSocket socket) throws Exception {
        var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        return JsonMessages.parse(input.readLine());
    }
    private void reply(SSLSocket socket, JsonNode request, String type, JsonNode payload) throws Exception {
        var frame = object().put("protocol_version", 1).put("type", type).put("request_id", request.path("request_id").asText()); frame.set("payload", payload);
        socket.getOutputStream().write(JsonMessages.encode(frame)); socket.getOutputStream().flush();
    }
    @Test void verifiesDnsCorrelatesResponsesAndDecodesFragmentedVietnamesePush() throws Exception {
        var keep = new CountDownLatch(1);
        try (var peer = new Peer(valid, socket -> {
            var request = read(socket); assertEquals("LOGIN", request.path("type").asText());
            reply(socket, request, "LOGIN_OK", object().put("role", "TEACHER").put("display_name", "Phúc Thiện"));
            var push = object().put("protocol_version", 1).put("type", "QUESTION").put("session_id", "s1"); push.set("payload", object().put("content", "Câu hỏi tiếng Việt"));
            byte[] bytes = JsonMessages.encode(push);
            for (byte b : bytes) socket.getOutputStream().write(new byte[]{b}); socket.getOutputStream().flush();
            keep.await(3, TimeUnit.SECONDS);
        }); var gateway = TlsQuizGateway.connect(new Endpoint("localhost", peer.port()), trust(valid), password)) {
            var event = new CompletableFuture<JsonNode>(); gateway.onEvent(message -> { if (message.path("type").asText().equals("QUESTION")) event.complete(message); });
            assertFalse(gateway.isPreview());
            var response = gateway.request("LOGIN", object().put("username", "test").put("password", "test-only")).get(3, TimeUnit.SECONDS);
            assertEquals("Phúc Thiện", response.path("display_name").asText());
            assertEquals("Câu hỏi tiếng Việt", event.get(3, TimeUnit.SECONDS).path("payload").path("content").asText());
            assertEquals("s1", event.get().path("payload").path("session_id").asText()); keep.countDown(); peer.served.get(3, TimeUnit.SECONDS);
        } finally { keep.countDown(); }
    }
    @Test void verifiesIpAndPlacesRoomSessionInTheEnvelope() throws Exception {
        try (var peer = new Peer(valid, socket -> {
            var request = read(socket); assertEquals("QZ7K2", request.path("room_code").asText()); assertEquals("s1", request.path("session_id").asText());
            assertFalse(request.path("payload").has("room_code")); assertFalse(request.path("payload").has("session_id"));
            reply(socket, request, "ANSWER_ACK", object().put("question_id", 1).put("accepted", true));
        }); var gateway = TlsQuizGateway.connect(new Endpoint("127.0.0.1", peer.port()), trust(valid), password)) {
            var ack = gateway.request("ANSWER", object().put("room_code", "QZ7K2").put("session_id", "s1").put("question_id", 1).put("answer", "B")).get(3, TimeUnit.SECONDS);
            assertTrue(ack.path("accepted").asBoolean()); peer.served.get(3, TimeUnit.SECONDS);
        }
    }
    @Test void trustedCertificateWithWrongSanIsRejectedWithoutPlaintextFallback() throws Exception {
        try (var peer = new Peer(wrongName, socket -> fail("Wrong SAN must not complete a usable connection"))) {
            assertThrows(SSLHandshakeException.class, () -> TlsQuizGateway.connect(new Endpoint("127.0.0.1", peer.port()), trust(wrongName), password));
            assertThrows(ExecutionException.class, () -> peer.served.get(3, TimeUnit.SECONDS));
        }
    }
    @Test void untrustedCertificateIsRejected() throws Exception {
        try (var peer = new Peer(valid, socket -> fail("Untrusted certificate must not complete a usable connection"))) {
            assertThrows(SSLException.class, () -> TlsQuizGateway.connect(new Endpoint("localhost", peer.port()), emptyTrust, password));
        }
    }
    @Test void correlatedErrorPreservesDuplicateDetailsWithoutSwitchingToPreview() throws Exception {
        try (var peer = new Peer(valid, socket -> {
            var request = read(socket); reply(socket, request, "ERROR", object().put("code", "DUPLICATE_QUESTION").put("message", "Chưa lưu câu trùng").put("total_matches", 2));
        }); var gateway = TlsQuizGateway.connect(new Endpoint("localhost", peer.port()), trust(valid), password)) {
            var error = assertThrows(ExecutionException.class, () -> gateway.request("QUESTION_CREATE", object()).get(3, TimeUnit.SECONDS));
            var domain = assertInstanceOf(GatewayException.class, error.getCause()); assertEquals("DUPLICATE_QUESTION", domain.code()); assertEquals(2, domain.details().path("total_matches").asInt()); assertFalse(gateway.isPreview());
            peer.served.get(3, TimeUnit.SECONDS);
        }
    }
    @Test void correlatedStartReplyAlsoReachesStateEventListener() throws Exception {
        var hold = new CountDownLatch(1);
        try (var peer = new Peer(valid, socket -> {
            var request = read(socket); reply(socket, request, "GAME_START", object().put("session_id", "s42").put("remaining_ms", 3000)); hold.await(3, TimeUnit.SECONDS);
        }); var gateway = TlsQuizGateway.connect(new Endpoint("localhost", peer.port()), trust(valid), password)) {
            var event = new CompletableFuture<JsonNode>(); gateway.onEvent(message -> { if (message.path("type").asText().equals("GAME_START")) event.complete(message); });
            assertEquals("s42", gateway.request("START_GAME", object()).get(3, TimeUnit.SECONDS).path("session_id").asText());
            assertEquals("s42", event.get(3, TimeUnit.SECONDS).path("payload").path("session_id").asText()); hold.countDown(); peer.served.get(3, TimeUnit.SECONDS);
        } finally { hold.countDown(); }
    }
}
