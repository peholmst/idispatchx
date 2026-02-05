package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MunicipalityTest {

    private static final Language FINNISH = Language.of("fi");
    private static final Language SWEDISH = Language.of("sv");

    private static final MunicipalityCode HELSINKI_CODE = MunicipalityCode.of("091");
    private static final MunicipalityCode ESPOO_CODE = MunicipalityCode.of("049");

    private static final MultilingualName HELSINKI_NAME = MultilingualName.of(
            Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
    private static final MultilingualName ESPOO_NAME = MultilingualName.of(
            Map.of(FINNISH, "Espoo", SWEDISH, "Esbo"));
    private static final MultilingualName MANUAL_NAME = MultilingualName.withUnspecifiedLanguage("Helsinki");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void of_withCodeAndName_createsInstance() {
        var municipality = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        assertEquals(HELSINKI_CODE, municipality.code());
        assertEquals(HELSINKI_NAME, municipality.name());
    }

    @Test
    void of_withNullCodeAndName_createsInstance() {
        var municipality = Municipality.of(null, MANUAL_NAME);
        assertNull(municipality.code());
        assertEquals(MANUAL_NAME, municipality.name());
    }

    @Test
    void withoutCode_createsInstanceWithoutCode() {
        var municipality = Municipality.withoutCode(MANUAL_NAME);
        assertNull(municipality.code());
        assertEquals(MANUAL_NAME, municipality.name());
    }

    // === Validation Tests ===

    @Test
    void of_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Municipality.of(HELSINKI_CODE, null));
    }

    @Test
    void of_emptyName_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Municipality.of(HELSINKI_CODE, MultilingualName.empty()));
        assertTrue(exception.getMessage().contains("name must not be empty"));
    }

    @Test
    void withoutCode_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Municipality.withoutCode(null));
    }

    @Test
    void withoutCode_emptyName_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Municipality.withoutCode(MultilingualName.empty()));
        assertTrue(exception.getMessage().contains("name must not be empty"));
    }

    // === Query Method Tests ===

    @Test
    void hasCode_withCode_returnsTrue() {
        var municipality = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        assertTrue(municipality.hasCode());
    }

    @Test
    void hasCode_withoutCode_returnsFalse() {
        var municipality = Municipality.withoutCode(MANUAL_NAME);
        assertFalse(municipality.hasCode());
    }

    @Test
    void optionalCode_withCode_returnsPresent() {
        var municipality = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        assertTrue(municipality.optionalCode().isPresent());
        assertEquals(HELSINKI_CODE, municipality.optionalCode().orElseThrow());
    }

    @Test
    void optionalCode_withoutCode_returnsEmpty() {
        var municipality = Municipality.withoutCode(MANUAL_NAME);
        assertTrue(municipality.optionalCode().isEmpty());
    }

    // === Equality Tests ===

    @Test
    void equals_sameValues_returnsTrue() {
        var m1 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var m2 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        assertEquals(m1, m2);
    }

    @Test
    void equals_differentCode_returnsFalse() {
        var m1 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var m2 = Municipality.of(ESPOO_CODE, HELSINKI_NAME);
        assertNotEquals(m1, m2);
    }

    @Test
    void equals_differentName_returnsFalse() {
        var m1 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var m2 = Municipality.of(HELSINKI_CODE, ESPOO_NAME);
        assertNotEquals(m1, m2);
    }

    @Test
    void equals_nullCodeVsPresentCode_returnsFalse() {
        var m1 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var m2 = Municipality.withoutCode(HELSINKI_NAME);
        assertNotEquals(m1, m2);
    }

    @Test
    void equals_bothNullCode_returnsTrue() {
        var m1 = Municipality.withoutCode(MANUAL_NAME);
        var m2 = Municipality.withoutCode(MANUAL_NAME);
        assertEquals(m1, m2);
    }

    @Test
    void hashCode_sameValues_sameHashCode() {
        var m1 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var m2 = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeWithCode_correctJson() throws JsonProcessingException {
        var municipality = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var json = objectMapper.writeValueAsString(municipality);
        assertTrue(json.contains("\"code\""));
        assertTrue(json.contains("\"091\""));
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"fi\""));
        assertTrue(json.contains("\"Helsinki\""));
    }

    @Test
    void jackson_serializeWithoutCode_correctJson() throws JsonProcessingException {
        var municipality = Municipality.withoutCode(MANUAL_NAME);
        var json = objectMapper.writeValueAsString(municipality);
        assertTrue(json.contains("\"code\":null") || !json.contains("\"code\""));
        assertTrue(json.contains("\"name\""));
    }

    @Test
    void jackson_deserializeWithCode_correctObject() throws JsonProcessingException {
        var json = """
                {"code":"091","name":{"fi":"Helsinki","sv":"Helsingfors"}}
                """;
        var municipality = objectMapper.readValue(json, Municipality.class);
        assertEquals(HELSINKI_CODE, municipality.code());
        assertEquals("Helsinki", municipality.name().get(FINNISH).orElseThrow());
        assertEquals("Helsingfors", municipality.name().get(SWEDISH).orElseThrow());
    }

    @Test
    void jackson_deserializeWithExplicitNullCode_correctObject() throws JsonProcessingException {
        var json = """
                {"code":null,"name":{"":"Helsinki"}}
                """;
        var municipality = objectMapper.readValue(json, Municipality.class);
        assertNull(municipality.code());
        assertFalse(municipality.hasCode());
        assertEquals("Helsinki", municipality.name().getUnspecified().orElseThrow());
    }

    @Test
    void jackson_deserializeWithOmittedCode_correctObject() throws JsonProcessingException {
        var json = """
                {"name":{"":"Helsinki"}}
                """;
        var municipality = objectMapper.readValue(json, Municipality.class);
        assertNull(municipality.code());
        assertFalse(municipality.hasCode());
        assertEquals("Helsinki", municipality.name().getUnspecified().orElseThrow());
    }

    @Test
    void jackson_roundTripWithCode_preservesData() throws JsonProcessingException {
        var original = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, Municipality.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_roundTripWithoutCode_preservesData() throws JsonProcessingException {
        var original = Municipality.withoutCode(MANUAL_NAME);
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, Municipality.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeEmptyName_throwsException() {
        var json = """
                {"code":"091","name":{}}
                """;
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Municipality.class));
    }

    @Test
    void jackson_deserializeInvalidCode_throwsException() {
        var json = """
                {"code":"invalid","name":{"fi":"Helsinki"}}
                """;
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Municipality.class));
    }

    @Test
    void jackson_deserializeMissingName_throwsException() {
        var json = """
                {"code":"091"}
                """;
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, Municipality.class));
    }

    // === toString Tests ===

    @Test
    void toString_withCode_includesCodeAndName() {
        var municipality = Municipality.of(HELSINKI_CODE, HELSINKI_NAME);
        var str = municipality.toString();
        assertTrue(str.startsWith("091 "));
    }

    @Test
    void toString_withoutCode_includesOnlyName() {
        var municipality = Municipality.withoutCode(MANUAL_NAME);
        var str = municipality.toString();
        assertFalse(str.contains("091"));
        assertEquals(MANUAL_NAME.toString(), str);
    }
}
