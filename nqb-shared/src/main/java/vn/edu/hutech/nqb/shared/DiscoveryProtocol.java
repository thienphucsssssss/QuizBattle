package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** UDP advertises only a TCP endpoint. Room/account data requires verified TLS. */
public final class DiscoveryProtocol {
    public static final int MAX_BYTES = 512;
    private DiscoveryProtocol() { }
    public static boolean localAddress(InetAddress address) {
        return address instanceof Inet4Address && (address.isSiteLocalAddress() || address.isLoopbackAddress());
    }
    public static byte[] message(String type, String nonce, int port) throws IOException {
        var body = JsonNodeFactory.instance.objectNode().put("service", "network-quiz-battle").put("nonce", nonce);
        if (port > 0) body.put("tcp_port", port);
        var frame = JsonNodeFactory.instance.objectNode().put("protocol_version", Protocol.VERSION).put("type", type).set("payload", body);
        return JsonMessages.encode(frame);
    }
    public static JsonNode parse(DatagramPacket packet, String type, String expectedNonce) throws IOException {
        if (!localAddress(packet.getAddress()) || packet.getLength() > MAX_BYTES) throw new IOException("Invalid discovery source/size");
        var message = JsonMessages.parse(new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8));
        var body = message.path("payload");
        String nonce = body.path("nonce").asText();
        if (!message.path("type").asText().equals(type) || !body.path("service").asText().equals("network-quiz-battle") || !nonce.matches("[a-f0-9]{32}") || (expectedNonce != null && !expectedNonce.equals(nonce))) throw new IOException("Unrelated discovery response");
        return body;
    }
}
