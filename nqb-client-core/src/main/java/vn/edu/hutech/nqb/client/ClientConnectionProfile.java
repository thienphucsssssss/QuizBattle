package vn.edu.hutech.nqb.client;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Public connection profile supplied by the LAN operator, never a Server private key. */
public record ClientConnectionProfile(String host, int port, Path truststore, String truststorePassword, int discoveryPort) {
    public ClientConnectionProfile(String host,int port,Path truststore,String truststorePassword){this(host,port,truststore,truststorePassword,5051);}
    public boolean automaticLan() { return host.equalsIgnoreCase("auto"); }
    public static ClientConnectionProfile load() throws IOException {
        Path directory = Path.of(System.getProperty("nqb.app.dir", ".")).toAbsolutePath();
        Path file = Path.of(System.getProperty("nqb.client.config", directory.resolve("client.properties").toString())).toAbsolutePath();
        return read(file);
    }
    public static ClientConnectionProfile read(Path file) throws IOException {
        var properties = new Properties();
        if (Files.exists(file)) try (var input = Files.newBufferedReader(file)) { properties.load(input); }
        Path base = file.toAbsolutePath().getParent();
        try {
            var endpoint = new Endpoint(properties.getProperty("server.host", "auto").strip(), Integer.parseInt(properties.getProperty("server.port", "5050")));
            int discovery=Integer.parseInt(properties.getProperty("discovery.port","5051"));
            if(discovery<1||discovery>65535)throw new IllegalArgumentException("Invalid discovery port");
            return new ClientConnectionProfile(endpoint.host(), endpoint.port(), base.resolve(properties.getProperty("tls.truststore", "certs/client-truststore.p12")).normalize(), properties.getProperty("tls.truststorePassword", "changeit"),discovery);
        } catch (RuntimeException invalid) { throw new IOException("Cấu hình kết nối Client không hợp lệ. Liên hệ người quản lý máy chủ.", invalid); }
    }
}
