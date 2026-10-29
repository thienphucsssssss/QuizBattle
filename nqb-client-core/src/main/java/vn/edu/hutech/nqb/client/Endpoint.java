package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.Protocol;

/** A destination address, not a server bind address. */
public record Endpoint(String host, int port) {
    public Endpoint {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        host = host.trim();
        if(host.length()>253||host.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||host.contains("/")||host.contains("@")||host.contains("\\"))
            throw new IllegalArgumentException("Nhập IP hoặc hostname máy chủ, không nhập URL.");
        if (host.equals("0.0.0.0") || host.equals("::")) {
            throw new IllegalArgumentException("Use the server LAN IP, not a bind address");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be 1..65535");
        }
    }

    public static Endpoint localTest() {
        return new Endpoint("127.0.0.1", Protocol.DEFAULT_PORT);
    }
}
