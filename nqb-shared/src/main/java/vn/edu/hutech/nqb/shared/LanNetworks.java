package vn.edu.hutech.nqb.shared;

import java.net.*;
import java.util.*;

/** Current physical IPv4 LANs, re-enumerated on every scan (no cached hotspot IP). */
public final class LanNetworks {
    private LanNetworks() { }
    public record Link(String name, InetAddress address, InetAddress broadcast, int prefixLength) { }

    public static List<Link> current() throws SocketException {
        var result = new ArrayList<Link>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return List.of();
        while (interfaces.hasMoreElements()) {
            var network = interfaces.nextElement();
            String name = network.getDisplayName() == null ? network.getName() : network.getDisplayName();
            if (!network.isUp() || network.isLoopback() || network.isVirtual() || excluded(name)) continue;
            for (var address : network.getInterfaceAddresses()) {
                if (DiscoveryProtocol.localAddress(address.getAddress()) && !address.getAddress().isLoopbackAddress()
                        && address.getBroadcast() != null) {
                    result.add(new Link(name, address.getAddress(), address.getBroadcast(), address.getNetworkPrefixLength()));
                }
            }
        }
        result.sort(Comparator.comparingInt((Link link) -> wifi(link.name()) ? 0 : 1)
            .thenComparing(Link::name).thenComparing(link -> link.address().getHostAddress()));
        return List.copyOf(result);
    }

    private static boolean wifi(String name) { return name.toLowerCase(Locale.ROOT).matches(".*(wi-fi|wifi|wireless|wlan|802\\.11).*?"); }
    private static boolean excluded(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*(loopback|vethernet|hyper-v|virtualbox|vmware|vpn|wireguard|tailscale|zerotier|wintun|tap-windows).*?");
    }
}
