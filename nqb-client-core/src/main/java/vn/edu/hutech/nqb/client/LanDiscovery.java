package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.DiscoveryProtocol;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Nonce-correlated UDP discovery. Responses are hints, not trusted identities. */
public final class LanDiscovery {
    private LanDiscovery() { }
    public static List<Endpoint> find(ClientConnectionProfile profile, int waitMillis) throws Exception {
        if(waitMillis<1||waitMillis>2000)throw new IllegalArgumentException("Invalid scan duration");
        var found=new LinkedHashSet<Endpoint>();var destinations=new LinkedHashSet<InetAddress>();
        destinations.add(InetAddress.getByName("127.0.0.1"));destinations.add(InetAddress.getByName("255.255.255.255"));
        try { var configured=InetAddress.getByName(profile.host());if(DiscoveryProtocol.localAddress(configured))destinations.add(configured); } catch(Exception ignored) { }
        for(var interfaces=NetworkInterface.getNetworkInterfaces();interfaces.hasMoreElements();) {
            var network=interfaces.nextElement();if(!network.isUp())continue;
            for(var address:network.getInterfaceAddresses())if(address.getBroadcast()!=null)destinations.add(address.getBroadcast());
        }
        String nonce=UUID.randomUUID().toString().replace("-","");byte[] request=DiscoveryProtocol.message("DISCOVER",nonce,0);
        try(var socket=new DatagramSocket(new InetSocketAddress("0.0.0.0",0))) {
            socket.setBroadcast(true);socket.setSoTimeout(100);
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(waitMillis);int received=0;
            for(var destination:destinations)try{socket.send(new DatagramPacket(request,request.length,destination,profile.discoveryPort()));}catch(Exception ignored) { }
            while(System.nanoTime()<deadline&&found.size()<8&&received<128&&!Thread.currentThread().isInterrupted())try {
                var packet=new DatagramPacket(new byte[DiscoveryProtocol.MAX_BYTES+1],DiscoveryProtocol.MAX_BYTES+1);socket.receive(packet);received++;
                var body=DiscoveryProtocol.parse(packet,"DISCOVER_OK",nonce);var port=body.path("tcp_port");
                if(port.isIntegralNumber()&&port.canConvertToInt()&&port.asInt()>0&&port.asInt()<=65535)found.add(new Endpoint(packet.getAddress().getHostAddress(),port.asInt()));
            } catch(SocketTimeoutException timeout) { } catch(java.io.IOException invalid) { }
        }
        return List.copyOf(found);
    }
}
