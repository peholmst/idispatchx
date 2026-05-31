package net.pkhapps.idispatchx.cad.domain.model.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallIdTest {

    @Test
    void acceptsValidNanoId() {
        assertDoesNotThrow(() -> new CallId("V1StGXR8_Z5jdHi6B-myT"));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new CallId(null));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new CallId("short"));
    }

    @Test
    void rejectsTooLong() {
        assertThrows(IllegalArgumentException.class, () -> new CallId("V1StGXR8_Z5jdHi6B-myTX"));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new CallId("V1StGXR8_Z5jdHi6B-my!"));
    }

    @Test
    void generatedNanoIdIsValid() {
        var id = new CallId(NanoIdGenerator.generate());
        assertEquals(21, id.value().length());
    }
}
