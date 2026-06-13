package net.pkhapps.idispatchx.cad.domain.model.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescriptionTest {

    @Test
    void acceptsEmpty() {
        assertDoesNotThrow(() -> new Description(""));
    }

    @Test
    void accepts1000Chars() {
        assertDoesNotThrow(() -> new Description("A".repeat(1000)));
    }

    @Test
    void rejectsMoreThan1000CodePoints() {
        // Use a BMP char that is exactly 1 code point
        assertThrows(IllegalArgumentException.class, () -> new Description("A".repeat(1001)));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new Description(null));
    }
}
