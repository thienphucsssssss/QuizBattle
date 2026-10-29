package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import vn.edu.hutech.nqb.shared.DiscoveryProtocol;
import java.net.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LanDiscoveryTest {
    @Test void ignoresWrongNonceAndDeduplicatesValidEndpointHints()throws Exception {
        try(var server=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))) {
            server.setSoTimeout(2000);
            var responder=Thread.ofVirtual().start(()->{try {
                var packet=new DatagramPacket(new byte[513],513);server.receive(packet);
                var request=DiscoveryProtocol.parse(packet,"DISCOVER",null);
                for(var bytes:new byte[][]{DiscoveryProtocol.message("DISCOVER_OK","00000000000000000000000000000000",4242),DiscoveryProtocol.message("DISCOVER_OK",request.path("nonce").asText(),5050),DiscoveryProtocol.message("DISCOVER_OK",request.path("nonce").asText(),5050)})server.send(new DatagramPacket(bytes,bytes.length,packet.getSocketAddress()));
            }catch(Exception failure){throw new RuntimeException(failure);}});
            var result=LanDiscovery.find(new ClientConnectionProfile("127.0.0.1",1,Path.of("unused"),"unused",server.getLocalPort()),400);
            responder.join(2500);assertEquals(java.util.List.of(new Endpoint("127.0.0.1",5050)),result);
        }
    }
    @Test void silentNetworkReturnsEmptyWithinBoundAndRejectsInvalidDuration()throws Exception {
        try(var unused=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))) {
            var profile=new ClientConnectionProfile("127.0.0.1",1,Path.of("unused"),"unused",unused.getLocalPort());
            long start=System.nanoTime();assertTrue(LanDiscovery.find(profile,150).isEmpty());
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500);
            assertThrows(IllegalArgumentException.class,()->LanDiscovery.find(profile,3000));
        }
    }
    @Test void automaticProfileUsesReplySourceAndDoesNotKeepPreviousAddress() throws Exception {
        try(var server=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))) {
            server.setSoTimeout(2500);
            var failure=new java.util.concurrent.CompletableFuture<Void>();
            var responder=Thread.ofVirtual().start(()->{try {
                for(String source:java.util.List.of("127.0.0.1","127.0.0.2")) {
                    var packet=new DatagramPacket(new byte[513],513);server.receive(packet);
                    var request=DiscoveryProtocol.parse(packet,"DISCOVER",null);
                    try(var reply=new DatagramSocket(new InetSocketAddress(source,0))) {
                        byte[] bytes=DiscoveryProtocol.message("DISCOVER_OK",request.path("nonce").asText(),5050);
                        reply.send(new DatagramPacket(bytes,bytes.length,packet.getSocketAddress()));
                    }
                }
                failure.complete(null);
            }catch(Exception error){failure.completeExceptionally(error);}});
            var profile=new ClientConnectionProfile("auto",1,Path.of("unused"),"unused",server.getLocalPort());
            assertEquals(java.util.List.of(new Endpoint("127.0.0.1",5050)),LanDiscovery.find(profile,200));
            assertEquals(java.util.List.of(new Endpoint("127.0.0.2",5050)),LanDiscovery.find(profile,200));
            failure.get(3,java.util.concurrent.TimeUnit.SECONDS);responder.join(1000);
        }
    }
}
