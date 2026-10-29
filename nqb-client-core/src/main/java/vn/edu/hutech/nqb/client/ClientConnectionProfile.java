package vn.edu.hutech.nqb.client;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Public connection profile supplied by the LAN operator, never a Server private key. */
public record ClientConnectionProfile(String host, int port, Path truststore, String truststorePassword, int discoveryPort) {
    public ClientConnectionProfile(String host,int port,Path truststore,String truststorePassword){this(host,port,truststore,truststorePassword,5051);}
    public boolean automaticLan() { return host.equalsIgnoreCase("auto"); }
    public ClientConnectionProfile destination(String host,int port) {
        var endpoint=new Endpoint(host,port);
        return new ClientConnectionProfile(endpoint.host(),endpoint.port(),truststore,truststorePassword,discoveryPort);
    }
    private static Path selectionFile() {
        return Path.of(System.getProperty("nqb.connection.settings",Path.of(System.getProperty("user.home"),".quizbattle","connection.properties").toString()));
    }
    /** Only the destination is persisted; trust always comes from the distributed profile. */
    public static ClientConnectionProfile loadSelected() throws IOException {
        var base=load();var path=selectionFile();
        if(!Files.isRegularFile(path))return base;
        var p=new Properties();try(var in=Files.newBufferedReader(path)){p.load(in);}
        try{return base.destination(p.getProperty("host","auto"),Integer.parseInt(p.getProperty("port","5050")));}
        catch(IllegalArgumentException invalid){throw new IOException("Địa chỉ máy chủ đã lưu không hợp lệ. Hãy nhập lại.",invalid);}
    }
    public void saveSelection() throws IOException {
        var target=selectionFile().toAbsolutePath();Files.createDirectories(target.getParent());
        var temp=Files.createTempFile(target.getParent(),"connection-",".tmp");
        try {
            var p=new Properties();p.setProperty("host",host);p.setProperty("port",String.valueOf(port));
            try(var out=Files.newBufferedWriter(temp)){p.store(out,"QuizBattle destination (no credentials)");}
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
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
