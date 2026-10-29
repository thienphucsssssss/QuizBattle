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
    @Test void defaultProfileUsesAutomaticLanWithoutAHotspotAddress() throws Exception {
        var profile=ClientConnectionProfile.read(directory.resolve("missing.properties"));
        assertTrue(profile.automaticLan());assertEquals(5051,profile.discoveryPort());
        Path file=directory.resolve("auto.properties");Files.writeString(file,"server.host=auto\n");
        assertTrue(ClientConnectionProfile.read(file).automaticLan());
    }
    @Test void selectedOverlayDestinationNeverPersistsOrOverridesTrust()throws Exception {
        Path file=directory.resolve("client.properties"),selected=directory.resolve("user/settings.properties");
        Files.writeString(file,"tls.truststore=certs/public.p12\ntls.truststorePassword=profile-public-secret\n");
        String beforeConfig=System.getProperty("nqb.client.config"),beforeSettings=System.getProperty("nqb.connection.settings");
        try {
            System.setProperty("nqb.client.config",file.toString());System.setProperty("nqb.connection.settings",selected.toString());
            ClientConnectionProfile.load().destination("100.92.12.3",5050).saveSelection();
            var value=ClientConnectionProfile.loadSelected();assertEquals("100.92.12.3",value.host());
            assertEquals(directory.resolve("certs/public.p12"),value.truststore());
            String saved=Files.readString(selected);assertFalse(saved.contains("password"));assertFalse(saved.contains("profile-public-secret"));
            value.destination("auto",5050).saveSelection();assertTrue(ClientConnectionProfile.loadSelected().automaticLan());
        }finally{
            if(beforeConfig==null)System.clearProperty("nqb.client.config");else System.setProperty("nqb.client.config",beforeConfig);
            if(beforeSettings==null)System.clearProperty("nqb.connection.settings");else System.setProperty("nqb.connection.settings",beforeSettings);
        }
    }
}
