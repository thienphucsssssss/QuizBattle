package vn.edu.hutech.nqb.server;

import javax.net.ssl.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.Properties;

public record ServerConfig(String bind, int port, Path database, Path keyStore, int maxPlayers, int maxConnections,
                           int countdownMillis, int resultMillis, int rankingMillis, boolean registrationEnabled, int discoveryPort, int resumeGraceSeconds) {
    public ServerConfig(String bind,int port,Path database,Path keyStore,int maxPlayers,int maxConnections,
                        int countdownMillis,int resultMillis,int rankingMillis,boolean registrationEnabled,int discoveryPort) {
        this(bind,port,database,keyStore,maxPlayers,maxConnections,countdownMillis,resultMillis,rankingMillis,registrationEnabled,discoveryPort,60);
    }
    public ServerConfig(String bind, int port, Path database, Path keyStore, int maxPlayers, int maxConnections,
                        int countdownMillis, int resultMillis, int rankingMillis, boolean registrationEnabled) {
        this(bind,port,database,keyStore,maxPlayers,maxConnections,countdownMillis,resultMillis,rankingMillis,registrationEnabled,0);
    }
    public ServerConfig(String bind, int port, Path database, Path keyStore, int maxPlayers, int maxConnections,
                        int countdownMillis, int resultMillis, int rankingMillis) {
        this(bind,port,database,keyStore,maxPlayers,maxConnections,countdownMillis,resultMillis,rankingMillis,true);
    }
    public ServerConfig {
        if (bind != null && bind.strip().equalsIgnoreCase("auto")) bind="0.0.0.0";
        if (bind == null || bind.isBlank() || port < 0 || port > 65535 || discoveryPort < 0 || discoveryPort > 65535 || maxPlayers < 2 || maxPlayers > 10 || maxConnections < maxPlayers + 1 || maxConnections > 128
            || countdownMillis < 1 || resultMillis < 1 || rankingMillis < 1 || resumeGraceSeconds<1 || resumeGraceSeconds>300) throw new IllegalArgumentException("Invalid server limits");
    }
    public static ServerConfig load(Path file) throws Exception {
        var p = new Properties(); try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        Path base = file.toAbsolutePath().getParent().getParent();
        return new ServerConfig(p.getProperty("server.bind","auto"),Integer.parseInt(p.getProperty("server.port","5050")),
            base.resolve(p.getProperty("database.path","data/quiz.db")),base.resolve(p.getProperty("tls.keystore","certs/server.p12")),
            Integer.parseInt(p.getProperty("server.maxPlayers","10")),Integer.parseInt(p.getProperty("server.maxConnections","32")),3000,3000,4500,Boolean.parseBoolean(p.getProperty("registration.enabled","true")),Integer.parseInt(p.getProperty("discovery.port","5051")),Integer.parseInt(p.getProperty("session.resumeGraceSeconds","60")));
    }
    public SSLContext tls(char[] password) throws Exception {
        var ks=KeyStore.getInstance("PKCS12"); try(var in=Files.newInputStream(keyStore)) { ks.load(in,password); }
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(ks,password);
        var ctx=SSLContext.getInstance("TLS"); ctx.init(km.getKeyManagers(),null,null); return ctx;
    }
}
