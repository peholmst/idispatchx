package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultilingualNameTest {

    private static final Locale FINNISH = Locale.of("fi");
    private static final Locale SWEDISH = Locale.of("sv");
    private static final Locale ENGLISH = Locale.of("en");
    private static final Locale NORTHERN_SAMI = Locale.of("sme");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void of_validIso639_1Codes_createsInstance() {
        var name = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors", ENGLISH, "Helsinki"));
        assertEquals(3, name.size());
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
        assertEquals("Helsingfors", name.get(SWEDISH).orElseThrow());
        assertEquals("Helsinki", name.get(ENGLISH).orElseThrow());
    }

    @Test
    void of_validIso639_3Code_createsInstance() {
        var name = MultilingualName.of(NORTHERN_SAMI, "Helsset");
        assertEquals("Helsset", name.get(NORTHERN_SAMI).orElseThrow());
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
                FINNISH, "Turku",
                SWEDISH, "Åbo",
                ENGLISH, "Turku"
        ));
        assertEquals(3, name.size());
        assertTrue(name.hasLanguage(FINNISH));
        assertTrue(name.hasLanguage(SWEDISH));
        assertTrue(name.hasLanguage(ENGLISH));
    }

    @Test
    void of_singleLanguage_createsInstance() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertEquals(1, name.size());
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
    }

    @Test
    void withUnspecifiedLanguage_createsInstanceWithLocaleRoot() {
        var name = MultilingualName.withUnspecifiedLanguage("Manually Entered Name");
        assertEquals(1, name.size());
        assertEquals("Manually Entered Name", name.getUnspecified().orElseThrow());
        assertEquals("Manually Entered Name", name.get(Locale.ROOT).orElseThrow());
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
        assertThrows(NullPointerException.class, () -> MultilingualName.of((Map<Locale, String>) null));
    }

    @Test
    void of_nullLocale_throwsNullPointerException() {
        var map = new HashMap<Locale, String>();
        map.put(null, "value");
        assertThrows(NullPointerException.class, () -> MultilingualName.of(map));
    }

    @Test
    void of_nullValue_throwsNullPointerException() {
        var map = new HashMap<Locale, String>();
        map.put(FINNISH, null);
        assertThrows(NullPointerException.class, () -> MultilingualName.of(map));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void of_blankValue_throwsIllegalArgumentException(String value) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(FINNISH, value));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void of_localeWithCountry_throwsIllegalArgumentException() {
        var localeWithCountry = Locale.of("fi", "FI");
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(localeWithCountry, "value"));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void of_localeWithScript_throwsIllegalArgumentException() {
        var localeWithScript = new Locale.Builder().setLanguage("zh").setScript("Hans").build();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(localeWithScript, "value"));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void of_localeWithVariant_throwsIllegalArgumentException() {
        var localeWithVariant = new Locale.Builder().setLanguage("en").setVariant("posix").build();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(localeWithVariant, "value"));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void of_valueTooLong_throwsIllegalArgumentException() {
        var longValue = "a".repeat(201);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MultilingualName.of(FINNISH, longValue));
        assertTrue(exception.getMessage().contains("must not exceed"));
    }

    @Test
    void of_valueAtMaxLength_succeeds() {
        var maxValue = "a".repeat(200);
        var name = MultilingualName.of(FINNISH, maxValue);
        assertEquals(maxValue, name.get(FINNISH).orElseThrow());
    }

    // === Normalization Tests ===

    @Test
    void of_localeNormalizedToLanguageOnly() {
        // Even though Locale.ENGLISH has country info in some JVMs, we normalize
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
        assertEquals("Helsinki", name.get(Locale.of("FI")).orElseThrow());
    }

    // === Query Method Tests ===

    @Test
    void get_existingLanguage_returnsValue() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertTrue(name.get(FINNISH).isPresent());
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
    }

    @Test
    void get_nonExistingLanguage_returnsEmpty() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertTrue(name.get(SWEDISH).isEmpty());
    }

    @Test
    void get_nullLocale_returnsEmpty() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertTrue(name.get(null).isEmpty());
    }

    @Test
    void hasLanguage_existingLanguage_returnsTrue() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertTrue(name.hasLanguage(FINNISH));
    }

    @Test
    void hasLanguage_nonExistingLanguage_returnsFalse() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertFalse(name.hasLanguage(SWEDISH));
    }

    @Test
    void hasLanguage_nullLocale_returnsFalse() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertFalse(name.hasLanguage(null));
    }

    @Test
    void isEmpty_emptyName_returnsTrue() {
        assertTrue(MultilingualName.empty().isEmpty());
    }

    @Test
    void isEmpty_nonEmptyName_returnsFalse() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertFalse(name.isEmpty());
    }

    @Test
    void locales_excludesUnspecifiedLanguage() {
        var name = MultilingualName.of(Map.of(FINNISH, "Helsinki", Locale.ROOT, "Manual"));
        var locales = name.locales();
        assertEquals(1, locales.size());
        assertTrue(locales.contains(FINNISH));
        assertFalse(locales.contains(Locale.ROOT));
    }

    @Test
    void locales_returnsAllSpecifiedLanguages() {
        var name = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        var locales = name.locales();
        assertEquals(2, locales.size());
        assertTrue(locales.contains(FINNISH));
        assertTrue(locales.contains(SWEDISH));
    }

    @Test
    void anyValue_nonEmptyName_returnsAValue() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
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
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertThrows(UnsupportedOperationException.class,
                () -> name.values().put(SWEDISH, "Helsingfors"));
    }

    @Test
    void locales_returnsImmutableSet() {
        var name = MultilingualName.of(FINNISH, "Helsinki");
        assertThrows(UnsupportedOperationException.class,
                () -> name.locales().add(SWEDISH));
    }

    @Test
    void sourceMapModifications_doNotAffectInstance() {
        var sourceMap = new HashMap<Locale, String>();
        sourceMap.put(FINNISH, "Helsinki");
        var name = MultilingualName.of(sourceMap);

        sourceMap.put(SWEDISH, "Helsingfors");
        sourceMap.put(FINNISH, "Modified");

        assertEquals(1, name.size());
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
        assertFalse(name.hasLanguage(SWEDISH));
    }

    // === Equality Tests ===

    @Test
    void equals_sameValues_returnsTrue() {
        var name1 = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        var name2 = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        assertEquals(name1, name2);
    }

    @Test
    void equals_differentValues_returnsFalse() {
        var name1 = MultilingualName.of(FINNISH, "Helsinki");
        var name2 = MultilingualName.of(FINNISH, "Turku");
        assertNotEquals(name1, name2);
    }

    @Test
    void equals_differentLanguages_returnsFalse() {
        var name1 = MultilingualName.of(FINNISH, "Helsinki");
        var name2 = MultilingualName.of(SWEDISH, "Helsinki");
        assertNotEquals(name1, name2);
    }

    @Test
    void hashCode_sameValues_sameHashCode() {
        var name1 = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        var name2 = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        assertEquals(name1.hashCode(), name2.hashCode());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeMultipleLanguages_correctJson() throws JsonProcessingException {
        var name = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        var json = objectMapper.writeValueAsString(name);
        assertTrue(json.contains("\"fi\""));
        assertTrue(json.contains("\"Helsinki\""));
        assertTrue(json.contains("\"sv\""));
        assertTrue(json.contains("\"Helsingfors\""));
    }

    @Test
    void jackson_deserializeValidJson_correctObject() throws JsonProcessingException {
        var json = """
                {"fi":"Helsinki","sv":"Helsingfors"}
                """;
        var name = objectMapper.readValue(json, MultilingualName.class);
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
        assertEquals("Helsingfors", name.get(SWEDISH).orElseThrow());
    }

    @Test
    void jackson_serializeEmpty_correctJson() throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(MultilingualName.empty());
        assertEquals("{}", json);
    }

    @Test
    void jackson_deserializeEmpty_correctObject() throws JsonProcessingException {
        var json = "{}";
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
                {"":"Manual Entry"}
                """;
        var name = objectMapper.readValue(json, MultilingualName.class);
        assertEquals("Manual Entry", name.getUnspecified().orElseThrow());
    }

    @Test
    void jackson_roundTrip_preservesData() throws JsonProcessingException {
        var original = MultilingualName.of(Map.of(
                FINNISH, "Helsinki",
                SWEDISH, "Helsingfors",
                Locale.ROOT, "Manual"
        ));
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, MultilingualName.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeInvalidLanguageCode_throwsException() {
        var json = """
                {"invalid-code":"value"}
                """;
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, MultilingualName.class));
    }

    @Test
    void jackson_deserializeValueTooLong_throwsException() {
        var longValue = "a".repeat(201);
        var json = """
                {"fi":"%s"}
                """.formatted(longValue);
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(json, MultilingualName.class));
    }

    // === fromLanguageTags Tests ===

    @Test
    void fromLanguageTags_validTags_createsInstance() {
        var name = MultilingualName.fromLanguageTags(Map.of("fi", "Helsinki", "sv", "Helsingfors"));
        assertEquals("Helsinki", name.get(FINNISH).orElseThrow());
        assertEquals("Helsingfors", name.get(SWEDISH).orElseThrow());
    }

    @Test
    void fromLanguageTags_emptyStringTag_createsUnspecified() {
        var name = MultilingualName.fromLanguageTags(Map.of("", "Manual"));
        assertEquals("Manual", name.getUnspecified().orElseThrow());
    }

    @Test
    void fromLanguageTags_emptyMap_returnsEmpty() {
        var name = MultilingualName.fromLanguageTags(Map.of());
        assertSame(MultilingualName.empty(), name);
    }

    // === toLanguageTags Tests ===

    @Test
    void toLanguageTags_returnsCorrectMap() {
        var name = MultilingualName.of(Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
        var tags = name.toLanguageTags();
        assertEquals(2, tags.size());
        assertEquals("Helsinki", tags.get("fi"));
        assertEquals("Helsingfors", tags.get("sv"));
    }

    @Test
    void toLanguageTags_unspecifiedLanguage_usesEmptyString() {
        var name = MultilingualName.withUnspecifiedLanguage("Manual");
        var tags = name.toLanguageTags();
        assertEquals("Manual", tags.get(""));
    }
}
