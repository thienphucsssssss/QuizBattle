package vn.edu.hutech.nqb.server;

import vn.edu.hutech.nqb.shared.DiscoveryProtocol;
import java.net.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small, rate-bounded LAN-only UDP responder; never touches JDBC or game state. */
final class LanDiscoveryServer implements AutoCloseable {
    private final DatagramSocket socket;
    private final int tcpPort;
    private final AtomicBoolean closed = new AtomicBoolean();
    LanDiscoveryServer(String bind, int discoveryPort, int tcpPort) throws Exception {
        this.tcpPort = tcpPort;
        socket = new DatagramSocket(null);
        try { socket.bind(new InetSocketAddress(InetAddress.getByName(bind).isLoopbackAddress() ? bind : "0.0.0.0", discoveryPort)); }
        catch (Exception error) { socket.close(); throw error; }
    }
    int port() { return socket.getLocalPort(); }
    void start() { Thread.ofPlatform().daemon().name("lan-discovery").start(this::receive); }
    private void receive() {
        long window = System.nanoTime(); int replies = 0;
        while (!closed.get()) try {
            var packet = new DatagramPacket(new byte[DiscoveryProtocol.MAX_BYTES + 1], DiscoveryProtocol.MAX_BYTES + 1); socket.receive(packet);
            long now = System.nanoTime(); if (now - window > TimeUnit.SECONDS.toNanos(1)) { window = now; replies = 0; }
            if (replies >= 30) continue;
            var body = DiscoveryProtocol.parse(packet, "DISCOVER", null);
            byte[] response = DiscoveryProtocol.message("DISCOVER_OK", body.path("nonce").asText(), tcpPort);
            socket.send(new DatagramPacket(response, response.length, packet.getSocketAddress())); replies++;
        } catch (Exception ignored) { /* Malformed/private-network probes never affect the game worker. */ }
    }
    @Override public void close() { if (closed.compareAndSet(false, true)) socket.close(); }
}
