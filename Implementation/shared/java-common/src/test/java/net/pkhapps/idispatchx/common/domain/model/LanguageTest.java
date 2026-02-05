package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void of_validIso639_1Code_createsInstance() {
        var language = Language.of("fi");
        assertEquals("fi", language.code());
    }

    @Test
    void of_validIso639_1Code_sv_createsInstance() {
        var language = Language.of("sv");
        assertEquals("sv", language.code());
    }

    @Test
    void of_validIso639_3Code_createsInstance() {
        var language = Language.of("sme");
        assertEquals("sme", language.code());
    }

    @Test
    void of_emptyString_returnsUnspecified() {
        var language = Language.of("");
        assertTrue(language.isUnspecified());
        assertSame(Language.unspecified(), language);
    }

    @Test
    void unspecified_returnsInstanceWithEmptyCode() {
        var language = Language.unspecified();
        assertEquals("", language.code());
        assertTrue(language.isUnspecified());
    }

    // === Validation Tests ===

    @Test
    void of_nullCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Language.of((String) null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd", "12", "f1", "ab-cd", "a1b", "1ab"})
    void of_invalidCode_throwsIllegalArgumentException(String code) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Language.of(code));
        assertTrue(exception.getMessage().contains("invalid language code"));
    }

    // === Normalization Tests ===

    @Test
    void of_uppercaseCode_normalizesToLowercase() {
        var language = Language.of("FI");
        assertEquals("fi", language.code());
    }

    @Test
    void of_mixedCaseCode_normalizesToLowercase() {
        var language = Language.of("Fi");
        assertEquals("fi", language.code());
    }

    // === Locale Factory Tests ===

    @Test
    void ofLocale_languageOnly_createsInstance() {
        var language = Language.of(Locale.of("fi"));
        assertEquals("fi", language.code());
    }

    @Test
    void ofLocale_localeRoot_returnsUnspecified() {
        var language = Language.of(Locale.ROOT);
        assertTrue(language.isUnspecified());
    }

    @Test
    void ofLocale_withCountry_throwsIllegalArgumentException() {
        var locale = Locale.of("fi", "FI");
        var exception = assertThrows(IllegalArgumentException.class, () -> Language.of(locale));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void ofLocale_withScript_throwsIllegalArgumentException() {
        var locale = new Locale.Builder().setLanguage("zh").setScript("Hans").build();
        var exception = assertThrows(IllegalArgumentException.class, () -> Language.of(locale));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void ofLocale_withVariant_throwsIllegalArgumentException() {
        var locale = new Locale.Builder().setLanguage("en").setVariant("posix").build();
        var exception = assertThrows(IllegalArgumentException.class, () -> Language.of(locale));
        assertTrue(exception.getMessage().contains("only language code is allowed"));
    }

    @Test
    void ofLocale_null_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Language.of((Locale) null));
    }

    // === Query Method Tests ===

    @Test
    void isUnspecified_unspecified_returnsTrue() {
        assertTrue(Language.unspecified().isUnspecified());
    }

    @Test
    void isUnspecified_specified_returnsFalse() {
        assertFalse(Language.of("fi").isUnspecified());
    }

    @Test
    void toLocale_specified_returnsLocaleWithLanguage() {
        var language = Language.of("fi");
        var locale = language.toLocale();
        assertEquals("fi", locale.getLanguage());
        assertTrue(locale.getCountry().isEmpty());
    }

    @Test
    void toLocale_unspecified_returnsLocaleRoot() {
        var language = Language.unspecified();
        assertEquals(Locale.ROOT, language.toLocale());
    }

    // === Equality Tests ===

    @Test
    void equals_sameCode_returnsTrue() {
        assertEquals(Language.of("fi"), Language.of("fi"));
    }

    @Test
    void equals_differentCode_returnsFalse() {
        assertNotEquals(Language.of("fi"), Language.of("sv"));
    }

    @Test
    void equals_normalizedSameCode_returnsTrue() {
        assertEquals(Language.of("FI"), Language.of("fi"));
    }

    @Test
    void hashCode_sameCode_sameHashCode() {
        assertEquals(Language.of("fi").hashCode(), Language.of("fi").hashCode());
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serialize_correctJson() throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(Language.of("fi"));
        assertEquals("\"fi\"", json);
    }

    @Test
    void jackson_serializeUnspecified_correctJson() throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(Language.unspecified());
        assertEquals("\"\"", json);
    }

    @Test
    void jackson_deserialize_correctObject() throws JsonProcessingException {
        var language = objectMapper.readValue("\"fi\"", Language.class);
        assertEquals(Language.of("fi"), language);
    }

    @Test
    void jackson_deserializeUnspecified_correctObject() throws JsonProcessingException {
        var language = objectMapper.readValue("\"\"", Language.class);
        assertTrue(language.isUnspecified());
    }

    @Test
    void jackson_roundTrip_preservesData() throws JsonProcessingException {
        var original = Language.of("sme");
        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, Language.class);
        assertEquals(original, restored);
    }

    @Test
    void jackson_deserializeInvalidCode_throwsException() {
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue("\"invalid\"", Language.class));
    }

    // === toString Tests ===

    @Test
    void toString_returnsCode() {
        assertEquals("fi", Language.of("fi").toString());
    }

    @Test
    void toString_unspecified_returnsEmptyString() {
        assertEquals("", Language.unspecified().toString());
    }
}
