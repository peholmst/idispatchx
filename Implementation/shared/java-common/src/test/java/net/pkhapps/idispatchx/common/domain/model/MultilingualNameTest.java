package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultilingualNameTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void of_validIso639_1Codes_createsInstance() {
        var name = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors", "en", "Helsinki"));
        assertEquals(3, name.size());
        assertEquals("Helsinki", name.get("fi").orElseThrow());
        assertEquals("Helsingfors", name.get("sv").orElseThrow());
        assertEquals("Helsinki", name.get("en").orElseThrow());
    }

    @Test
    void of_validIso639_3Code_createsInstance() {
        // Northern Sami (ISO 639-3: sme)
        var name = MultilingualName.of("sme", "Helsset");
        assertEquals("Helsset", name.get("sme").orElseThrow());
    }

    @Test
    void of_emptyMap_createsEmptyInstance() {
        var name = MultilingualName.of(Map.of());
        assertTrue(name.isEmpty());
        assertEquals(0, name.size());
    }

    @Test
    void of_multipleLanguages_preservesAll() {
        var name = MultilingualName.of(Map.of(
                "fi", "Turku",
                "sv", "Åbo",
                "en", "Turku"
        ));
        assertEquals(3, name.size());
        assertTrue(name.hasLanguage("fi"));
        assertTrue(name.hasLanguage("sv"));
        assertTrue(name.hasLanguage("en"));
    }

    @Test
    void of_singleLanguage_createsInstance() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertEquals(1, name.size());
        assertEquals("Helsinki", name.get("fi").orElseThrow());
    }

    @Test
    void withUnspecifiedLanguage_createsInstanceWithEmptyStringKey() {
        var name = MultilingualName.withUnspecifiedLanguage("Manually Entered Name");
        assertEquals(1, name.size());
        assertEquals("Manually Entered Name", name.getUnspecified().orElseThrow());
        assertEquals("Manually Entered Name", name.get("").orElseThrow());
    }

    @Test
    void empty_returnsSameInstance() {
        var empty1 = MultilingualName.empty();
        var empty2 = MultilingualName.empty();
        assertSame(empty1, empty2);
    }

    @Test
    void of_emptyMapReturnsEmptyInstance() {
        var name = MultilingualName.of(Map.of());
        assertSame(MultilingualName.empty(), name);
    }

    // === Validation Tests ===

    @Test
    void of_nullMap_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> MultilingualName.of(null));
    }

    @Test
    void of_nullLanguageCode_throwsNullPointerException() {
        var map = new HashMap<String, String>();
        map.put(null, "value");
        assertThrows(NullPointerException.class, () -> MultilingualName.of(map));
    }

    @Test
    void of_nullValue_throwsNullPointerException() {
        var map = new HashMap<String, String>();
        map.put("fi", null);
        assertThrows(NullPointerException.class, () -> MultilingualName.of(map));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void of_blankValue_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of("fi", value));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd", "12", "f1", "1f", "ab-cd", "a_b"})
    void of_invalidLanguageCode_throwsIllegalArgumentException(String code) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(code, "value"));
        assertTrue(exception.getMessage().contains("invalid language code"));
    }

    @Test
    void of_valueTooLong_throwsIllegalArgumentException() {
        var longValue = "a".repeat(201);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of("fi", longValue));
        assertTrue(exception.getMessage().contains("must not exceed"));
    }

    @Test
    void of_valueAtMaxLength_succeeds() {
        var maxValue = "a".repeat(200);
        var name = MultilingualName.of("fi", maxValue);
        assertEquals(maxValue, name.get("fi").orElseThrow());
    }

    // === Normalization Tests ===

    @Test
    void of_languageCodeNormalizedToLowercase() {
        var name = MultilingualName.of("FI", "Helsinki");
        assertEquals("Helsinki", name.get("fi").orElseThrow());
        assertEquals("Helsinki", name.get("FI").orElseThrow());
    }

    @Test
    void of_mixedCaseLanguageCodes_normalizedToLowercase() {
        var name = MultilingualName.of(Map.of("FI", "Helsinki", "SV", "Helsingfors"));
        assertTrue(name.hasLanguage("fi"));
        assertTrue(name.hasLanguage("sv"));
        assertTrue(name.hasLanguage("FI"));
        assertTrue(name.hasLanguage("SV"));
    }

    // === Query Method Tests ===

    @Test
    void get_existingLanguage_returnsValue() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertTrue(name.get("fi").isPresent());
        assertEquals("Helsinki", name.get("fi").orElseThrow());
    }

    @Test
    void get_nonExistingLanguage_returnsEmpty() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertTrue(name.get("sv").isEmpty());
    }

    @Test
    void get_nullLanguage_returnsEmpty() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertTrue(name.get(null).isEmpty());
    }

    @Test
    void hasLanguage_existingLanguage_returnsTrue() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertTrue(name.hasLanguage("fi"));
    }

    @Test
    void hasLanguage_nonExistingLanguage_returnsFalse() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertFalse(name.hasLanguage("sv"));
    }

    @Test
    void hasLanguage_nullLanguage_returnsFalse() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertFalse(name.hasLanguage(null));
    }

    @Test
    void isEmpty_emptyName_returnsTrue() {
        assertTrue(MultilingualName.empty().isEmpty());
    }

    @Test
    void isEmpty_nonEmptyName_returnsFalse() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertFalse(name.isEmpty());
    }

    @Test
    void languageCodes_excludesUnspecifiedLanguage() {
        var name = MultilingualName.of(Map.of("fi", "Helsinki", "", "Manual"));
        var codes = name.languageCodes();
        assertEquals(1, codes.size());
        assertTrue(codes.contains("fi"));
        assertFalse(codes.contains(""));
    }

    @Test
    void languageCodes_returnsAllSpecifiedLanguages() {
        var name = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        var codes = name.languageCodes();
        assertEquals(2, codes.size());
        assertTrue(codes.contains("fi"));
        assertTrue(codes.contains("sv"));
    }

    @Test
    void anyValue_nonEmptyName_returnsAValue() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertTrue(name.anyValue().isPresent());
        assertEquals("Helsinki", name.anyValue().orElseThrow());
    }

    @Test
    void anyValue_emptyName_returnsEmpty() {
        assertTrue(MultilingualName.empty().anyValue().isEmpty());
    }

    // === Immutability Tests ===

    @Test
    void values_returnsImmutableMap() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertThrows(UnsupportedOperationException.class,
                () -> name.values().put("sv", "Helsingfors"));
    }

    @Test
    void languageCodes_returnsImmutableSet() {
        var name = MultilingualName.of("fi", "Helsinki");
        assertThrows(UnsupportedOperationException.class,
                () -> name.languageCodes().add("sv"));
    }

    @Test
    void sourceMapModifications_doNotAffectInstance() {
        var sourceMap = new HashMap<String, String>();
        sourceMap.put("fi", "Helsinki");
        var name = MultilingualName.of(sourceMap);

        sourceMap.put("sv", "Helsingfors");
        sourceMap.put("fi", "Modified");

        assertEquals(1, name.size());
        assertEquals("Helsinki", name.get("fi").orElseThrow());
        assertFalse(name.hasLanguage("sv"));
    }

    // === Equality Tests ===

    @Test
    void equals_sameValues_returnsTrue() {
        var name1 = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        var name2 = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        assertEquals(name1, name2);
    }

    @Test
    void equals_differentValues_returnsFalse() {
        var name1 = MultilingualName.of("fi", "Helsinki");
        var name2 = MultilingualName.of("fi", "Turku");
        assertNotEquals(name1, name2);
    }

    @Test
    void equals_differentLanguages_returnsFalse() {
        var name1 = MultilingualName.of("fi", "Helsinki");
        var name2 = MultilingualName.of("sv", "Helsinki");
        assertNotEquals(name1, name2);
    }

    @Test
    void hashCode_sameValues_sameHashCode() {
        var name1 = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        var name2 = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        assertEquals(name1.hashCode(), name2.hashCode());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeMultipleLanguages_correctJson() throws JsonProcessingException {
        var name = MultilingualName.of(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        var json = objectMapper.writeValueAsString(name);
        assertTrue(json.contains("\"values\""));
        assertTrue(json.contains("\"fi\""));
        assertTrue(json.contains("\"Helsinki\""));
        assertTrue(json.contains("\"sv\""));
        assertTrue(json.contains("\"Helsingfors\""));
    }

    @Test
    void jackson_deserializeValidJson_correctObject() throws JsonProcessingException {
        var json = """
                {"values":{"fi":"Helsinki","sv":"Helsingfors"}}
                """;
        var name = objectMapper.readValue(json, MultilingualName.class);
        assertEquals("Helsinki", name.get("fi").orElseThrow());
        assertEquals("Helsingfors", name.get("sv").orElseThrow());
    }

    @Test
    void jackson_serializeEmpty_correctJson() throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(MultilingualName.empty());
        assertEquals("{\"values\":{}}", json);
    }

    @Test
    void jackson_deserializeEmpty_correctObject() throws JsonProcessingException {
        var json = """
                {"values":{}}
                """;
        var name = objectMapper.readValue(json, MultilingualName.class);
        assertTrue(name.isEmpty());
    }

    @Test
    void jackson_serializeUnspecifiedLanguage_correctJson() throws JsonProcessingException {
        var name = MultilingualName.withUnspecifiedLanguage("Manual Entry");
        var json = objectMapper.writeValueAsString(name);
        assertTrue(json.contains("\"\""));
        assertTrue(json.contains("\"Manual Entry\""));
    }

    @Test
    void jackson_deserializeUnspecifiedLanguage_correctObject() throws JsonProcessingException {
        var json = """
                {"values":{"":"Manual Entry"}}
                """;
        var name = objectMapper.readValue(json, MultilingualName.class);
        assertEquals("Manual Entry", name.getUnspecified().orElseThrow());
    }

    @Test
    void jackson_roundTrip_preservesData() throws JsonProcessingException {
        var original = MultilingualName.of(Map.of(
                "fi", "Helsinki",
                "sv", "Helsingfors",
                "", "Manual"
        ));
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, MultilingualName.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeInvalidLanguageCode_throwsException() {
        var json = """
                {"values":{"invalid-code":"value"}}
                """;
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, MultilingualName.class));
    }

    @Test
    void jackson_deserializeValueTooLong_throwsException() {
        var longValue = "a".repeat(201);
        var json = """
                {"values":{"fi":"%s"}}
                """.formatted(longValue);
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, MultilingualName.class));
    }
}
