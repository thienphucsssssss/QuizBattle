package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path temp;
    @Test void autoAndMissingBindingUseWildcardWhileExplicitLoopbackRemainsIsolated() throws Exception {
        Path config=temp.resolve("config/server.properties");Files.createDirectories(config.getParent());
        Files.writeString(config,"");assertEquals("0.0.0.0",ServerConfig.load(config).bind());
        Files.writeString(config,"server.bind=auto\n");assertEquals("0.0.0.0",ServerConfig.load(config).bind());
        Files.writeString(config,"server.bind=127.0.0.1\n");assertEquals("127.0.0.1",ServerConfig.load(config).bind());
        Files.writeString(config,"server.bind=10.122.30.170\n");assertEquals("10.122.30.170",ServerConfig.load(config).bind());
    }
}
