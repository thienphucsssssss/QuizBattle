package vn.edu.hutech.nqb.shared;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryProtocolTest {
    private static final String NONCE="1234567890abcdef1234567890abcdef";
    private DatagramPacket packet(byte[] bytes,String address)throws Exception{return new DatagramPacket(bytes,bytes.length,InetAddress.getByName(address),5051);}
    @Test void responseEchoesNonceAndOnlyAdvertisesEndpoint()throws Exception {
        var body=DiscoveryProtocol.parse(packet(DiscoveryProtocol.message("DISCOVER_OK",NONCE,5050),"192.168.1.2"),"DISCOVER_OK",NONCE);
        assertEquals(5050,body.path("tcp_port").asInt());assertEquals(3,body.size());assertFalse(body.has("room_code"));
    }
    @Test void rejectsUnrelatedNonceProtocolAndInternetSource()throws Exception {
        byte[] response=DiscoveryProtocol.message("DISCOVER_OK",NONCE,5050);
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet(response,"127.0.0.1"),"DISCOVER_OK","00000000000000000000000000000000"));
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet(response,"127.0.0.1"),"DISCOVER",NONCE));
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet(response,"8.8.8.8"),"DISCOVER_OK",NONCE));
        byte[] wrongVersion=new String(response,java.nio.charset.StandardCharsets.UTF_8).replace("\"protocol_version\":1","\"protocol_version\":2").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet(wrongVersion,"127.0.0.1"),"DISCOVER_OK",NONCE));
    }
    @Test void rejectsOversizedAndMalformedDatagrams()throws Exception {
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet(new byte[513],"127.0.0.1"),"DISCOVER",null));
        assertThrows(IOException.class,()->DiscoveryProtocol.parse(packet("not-json".getBytes(),"127.0.0.1"),"DISCOVER",null));
    }
}
