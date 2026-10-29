package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.DiscoveryProtocol;
import vn.edu.hutech.nqb.shared.LanNetworks;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Nonce-correlated UDP discovery. Responses are hints, not trusted identities. */
public final class LanDiscovery {
    private LanDiscovery() { }
    public static List<Endpoint> find(ClientConnectionProfile profile, int waitMillis) throws Exception {
        if(waitMillis<1||waitMillis>2000)throw new IllegalArgumentException("Invalid scan duration");
        var found=new LinkedHashSet<Endpoint>();
        String nonce=UUID.randomUUID().toString().replace("-","");byte[] request=DiscoveryProtocol.message("DISCOVER",nonce,0);
        var sockets=new ArrayList<DatagramSocket>();
        try {
            // Each source is bound explicitly: a VPN/default-route change cannot steal Wi-Fi probes.
            sendFrom(sockets,InetAddress.getByName("127.0.0.1"),List.of(InetAddress.getByName("127.0.0.1")),profile.discoveryPort(),request);
            for(var link:LanNetworks.current()) {
                if(sockets.size()>=8)break;
                sendFrom(sockets,link.address(),List.of(link.broadcast(),InetAddress.getByName("255.255.255.255")),profile.discoveryPort(),request);
            }
            if(!profile.automaticLan())try {
                var configured=InetAddress.getByName(profile.host());
                if(DiscoveryProtocol.localAddress(configured))sendFrom(sockets,null,List.of(configured),profile.discoveryPort(),request);
            } catch(Exception ignored) { }
            if(sockets.isEmpty())return List.of();
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(waitMillis);int received=0;
            scan:while(System.nanoTime()<deadline&&found.size()<8&&received<128&&!Thread.currentThread().isInterrupted()) {
                for(var socket:sockets) {
                    if(System.nanoTime()>=deadline||found.size()>=8||received>=128||Thread.currentThread().isInterrupted())break scan;
                    try {
                        var packet=new DatagramPacket(new byte[DiscoveryProtocol.MAX_BYTES+1],DiscoveryProtocol.MAX_BYTES+1);socket.receive(packet);received++;
                        var body=DiscoveryProtocol.parse(packet,"DISCOVER_OK",nonce);var port=body.path("tcp_port");
                        if(port.isIntegralNumber()&&port.canConvertToInt()&&port.asInt()>0&&port.asInt()<=65535)found.add(new Endpoint(packet.getAddress().getHostAddress(),port.asInt()));
                    } catch(SocketTimeoutException timeout) { } catch(java.io.IOException invalid) { }
                }
            }
        } finally {sockets.forEach(DatagramSocket::close);}
        return List.copyOf(found);
    }
    private static void sendFrom(List<DatagramSocket> sockets,InetAddress source,List<InetAddress> destinations,int port,byte[] request) {
        DatagramSocket socket=null;
        try {
            socket=new DatagramSocket(new InetSocketAddress(source,0));socket.setBroadcast(true);socket.setSoTimeout(15);
            for(var destination:destinations)try{socket.send(new DatagramPacket(request,request.length,destination,port));}catch(Exception ignored) { }
            sockets.add(socket);
        } catch(Exception unavailable) {if(socket!=null)socket.close();}
    }
}
