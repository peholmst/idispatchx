package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserIdTest {

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new UserId(null));
    }

    @Test
    void constructor_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new UserId(""));
        assertThrows(IllegalArgumentException.class, () -> new UserId("   "));
    }

    @Test
    void constructor_acceptsValidValue() {
        var userId = new UserId("user@example.com");
        assertEquals("user@example.com", userId.value());
    }

    @Test
    void of_createsInstance() {
        var userId = UserId.of("user@example.com");
        assertEquals("user@example.com", userId.value());
    }

    @Test
    void system_isNotNull() {
        assertNotNull(UserId.SYSTEM);
    }

    @Test
    void system_hasExpectedValue() {
        assertEquals("SYSTEM", UserId.SYSTEM.value());
    }

    @Test
    void toString_returnsValue() {
        assertEquals("user@example.com", new UserId("user@example.com").toString());
    }

    @Test
    void equality_basedOnValue() {
        assertEquals(new UserId("user123"), new UserId("user123"));
        assertNotEquals(new UserId("user123"), new UserId("other"));
    }
}
