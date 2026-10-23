package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndpointTest {
    @Test void localAndLanDestinations() {
        assertEquals("127.0.0.1", Endpoint.localTest().host());
        assertEquals(new Endpoint("192.168.1.10", 5050), new Endpoint(" 192.168.1.10 ", 5050));
    }

    @Test void bindAddressAndInvalidPortsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("0.0.0.0", 5050));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("::", 5050));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("", 5050));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("localhost", 65536));
    }
}
