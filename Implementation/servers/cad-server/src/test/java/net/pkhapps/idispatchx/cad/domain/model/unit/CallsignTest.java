package net.pkhapps.idispatchx.cad.domain.model.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CallsignTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "REP101",      // Standard rescue unit
            "REPP21",      // Rescue leadership unit
            "IR1234",      // Industrial rescue unit
            "K5101",       // Municipal unit
            "REP_10VV",    // Rescue alert group
            "REP_JOKE",    // Rescue command center
            "REP_TIKE",    // Rescue situation center
            "RSE2116310",  // Foreign rescue unit (Sweden)
            "RNOD11",      // Foreign rescue unit (Norway)
            "ABC",         // Minimum length (3 chars)
            "ABCDEFGHIJKLMNOP" // Maximum length (16 chars)
    })
    void of_acceptsValidCallsigns(String value) {
        var callsign = Callsign.of(value);
        assertEquals(value, callsign.value());
    }

    @Test
    void of_nullValue_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Callsign.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void of_blankValue_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "R1"})
    void of_tooShort_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("at least 3"));
    }

    @Test
    void of_tooLong_throwsIllegalArgumentException() {
        var value = "ABCDEFGHIJKLMNOPQ"; // 17 chars
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("at most 16"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XEP101", "ZEP101", "1EP101", "0EP101"})
    void of_invalidSectorCode_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("invalid sector code"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REP10!", "REP@01", "REP#01", "REP$01", "REP%01", "REP 01"})
    void of_invalidSpecialCharacters_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("invalid character"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REP_1_2", "REP_A_B", "A__BC"})
    void of_multipleUnderscores_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("at most one underscore"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"R_P101", "RE_101"})
    void of_underscoreTooEarly_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of(value));
        assertTrue(exception.getMessage().contains("underscore must appear after position"));
    }

    @Test
    void of_underscoreAtPosition3ForStandardUnit_isValid() {
        // Underscore at position 3 (0-indexed) = position 4 (1-indexed) is valid for non-K units
        var callsign = Callsign.of("REP_101");
        assertEquals("REP_101", callsign.value());
    }

    @Test
    void of_underscoreAtPosition2_throwsIllegalArgumentException() {
        // Underscore at position 2 (0-indexed) = position 3 (1-indexed) is too early
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of("RE_101"));
        assertTrue(exception.getMessage().contains("underscore must appear after position 3"));
    }

    @Test
    void of_underscoreAtPosition3ForKPrefix_throwsIllegalArgumentException() {
        // K-prefix units require underscore after position 4 (1-indexed)
        var exception = assertThrows(IllegalArgumentException.class, () -> Callsign.of("K51_01"));
        assertTrue(exception.getMessage().contains("underscore must appear after position 4"));
    }

    @Test
    void of_underscoreAtPosition4ForKPrefix_isValid() {
        var callsign = Callsign.of("K510_1");
        assertEquals("K510_1", callsign.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rep101", "Rep101", "rEP101", "REp101"})
    void of_normalizesToUppercase(String value) {
        var callsign = Callsign.of(value);
        assertEquals("REP101", callsign.value());
    }

    @Test
    void toString_returnsValue() {
        var callsign = Callsign.of("REP101");
        assertEquals("REP101", callsign.toString());
    }

    @Test
    void equals_sameValue_returnsTrue() {
        var callsign1 = Callsign.of("REP101");
        var callsign2 = Callsign.of("REP101");
        assertEquals(callsign1, callsign2);
    }

    @Test
    void equals_differentCase_equalAfterNormalization() {
        var callsign1 = Callsign.of("REP101");
        var callsign2 = Callsign.of("rep101");
        assertEquals(callsign1, callsign2);
    }

    @Test
    void equals_differentValue_returnsFalse() {
        var callsign1 = Callsign.of("REP101");
        var callsign2 = Callsign.of("REP102");
        assertNotEquals(callsign1, callsign2);
    }

    @Test
    void hashCode_sameValue_sameHashCode() {
        var callsign1 = Callsign.of("REP101");
        var callsign2 = Callsign.of("REP101");
        assertEquals(callsign1.hashCode(), callsign2.hashCode());
    }

    @ParameterizedTest
    @ValueSource(chars = {'R', 'P', 'E', 'B', 'S', 'M', 'C', 'V', 'A', 'I', 'K'})
    void of_allValidSectorCodes_areAccepted(char sectorCode) {
        var callsign = Callsign.of(sectorCode + "AB123");
        assertEquals(sectorCode + "AB123", callsign.value());
    }
}
