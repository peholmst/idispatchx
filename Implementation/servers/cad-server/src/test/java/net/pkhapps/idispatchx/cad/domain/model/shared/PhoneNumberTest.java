package net.pkhapps.idispatchx.cad.domain.model.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberTest {

    @Test
    void acceptsE164WithPlus() {
        assertDoesNotThrow(() -> new PhoneNumber("+358401234567"));
    }

    @Test
    void acceptsDomesticNumberWithoutPlus() {
        assertDoesNotThrow(() -> new PhoneNumber("0401234567"));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new PhoneNumber(null));
    }

    @Test
    void rejectsNonDigitChars() {
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("+358-40-123"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("+358 40 123"));
    }

    @Test
    void rejectsMoreThan15Digits() {
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("1234567890123456")); // 16 digits
    }

    @Test
    void accepts15Digits() {
        assertDoesNotThrow(() -> new PhoneNumber("123456789012345")); // 15 digits
    }

    @Test
    void rejectsPlusOnly() {
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("+"));
    }

    @Test
    void toStringReturnsValue() {
        assertEquals("+358401234567", new PhoneNumber("+358401234567").toString());
    }
}
