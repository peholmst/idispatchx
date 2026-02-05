package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MunicipalityCodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void of_validCode_createsInstance() {
        var code = MunicipalityCode.of("091");
        assertEquals("091", code.code());
    }

    @Test
    void of_allZeros_createsInstance() {
        var code = MunicipalityCode.of("000");
        assertEquals("000", code.code());
    }

    @Test
    void of_allNines_createsInstance() {
        var code = MunicipalityCode.of("999");
        assertEquals("999", code.code());
    }

    @Test
    void of_leadingZeros_preservesLeadingZeros() {
        var code = MunicipalityCode.of("005");
        assertEquals("005", code.code());
    }

    // === Validation Tests ===

    @Test
    void of_nullCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MunicipalityCode.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "09", "0912", "abc", "09a", "a91", " 91", "91 ", " 9 ", "09!", "0.1"})
    void of_invalidCode_throwsIllegalArgumentException(String code) {
        var exception = assertThrows(IllegalArgumentException.class, () -> MunicipalityCode.of(code));
        assertTrue(exception.getMessage().contains("invalid municipality code"));
        assertTrue(exception.getMessage().contains("must be exactly 3 digits"));
    }

    // === Equality Tests ===

    @Test
    void equals_sameCode_returnsTrue() {
        assertEquals(MunicipalityCode.of("091"), MunicipalityCode.of("091"));
    }

    @Test
    void equals_differentCode_returnsFalse() {
        assertNotEquals(MunicipalityCode.of("091"), MunicipalityCode.of("049"));
    }

    @Test
    void hashCode_sameCode_sameHashCode() {
        assertEquals(MunicipalityCode.of("091").hashCode(), MunicipalityCode.of("091").hashCode());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serialize_correctJson() throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(MunicipalityCode.of("091"));
        assertEquals("\"091\"", json);
    }

    @Test
    void jackson_deserialize_correctObject() throws JsonProcessingException {
        var code = objectMapper.readValue("\"091\"", MunicipalityCode.class);
        assertEquals(MunicipalityCode.of("091"), code);
    }

    @Test
    void jackson_roundTrip_preservesData() throws JsonProcessingException {
        var original = MunicipalityCode.of("049");
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, MunicipalityCode.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeInvalidCode_throwsException() {
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue("\"invalid\"", MunicipalityCode.class));
    }

    // === toString Tests ===

    @Test
    void toString_returnsCode() {
        assertEquals("091", MunicipalityCode.of("091").toString());
    }
}
