package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionProfileTest {
    @TempDir Path directory;
    @Test void bundledProfileResolvesBesideAppRatherThanWorkingDirectory() throws Exception {
        Path file = directory.resolve("client.properties");
        Files.writeString(file,"server.host=10.12.89.105\nserver.port=5050\ntls.truststore=certs/public.p12\ntls.truststorePassword=changeit\n");
        var profile = ClientConnectionProfile.read(file);
        assertEquals("10.12.89.105",profile.host()); assertEquals(5050,profile.port());
        assertEquals(directory.resolve("certs/public.p12"),profile.truststore());
    }
    @Test void invalidProfileFailsRatherThanSilentlySwitchingServers() throws Exception {
        Path file = directory.resolve("client.properties"); Files.writeString(file,"server.port=invalid\n");
        assertThrows(java.io.IOException.class,()->ClientConnectionProfile.read(file));
    }
}
