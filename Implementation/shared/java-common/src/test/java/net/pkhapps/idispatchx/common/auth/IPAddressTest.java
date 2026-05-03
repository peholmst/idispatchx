package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IPAddressTest {

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new IPAddress(null));
    }

    @Test
    void constructor_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new IPAddress(""));
        assertThrows(IllegalArgumentException.class, () -> new IPAddress("   "));
    }

    @Test
    void constructor_acceptsIPv4() {
        var ip = new IPAddress("192.168.1.1");
        assertEquals("192.168.1.1", ip.value());
    }

    @Test
    void constructor_acceptsIPv6() {
        var ip = new IPAddress("2001:db8::1");
        assertEquals("2001:db8::1", ip.value());
    }

    @Test
    void of_createsInstance() {
        var ip = IPAddress.of("10.0.0.1");
        assertEquals("10.0.0.1", ip.value());
    }

    @Test
    void toString_returnsValue() {
        assertEquals("192.168.0.1", new IPAddress("192.168.0.1").toString());
    }

    @Test
    void equality_basedOnValue() {
        assertEquals(new IPAddress("192.168.1.1"), new IPAddress("192.168.1.1"));
        assertNotEquals(new IPAddress("192.168.1.1"), new IPAddress("10.0.0.1"));
    }
}
