package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A value object representing a multilingual name that can have multiple language versions.
 * <p>
 * In Finland, names are typically in Finnish, Swedish, or both. In Lapland, Sami languages
 * are also used. This class supports ISO 639 language codes (ISO 639-1 where available,
 * otherwise ISO 639-2 or 639-3).
 * <p>
 * When a name is manually entered without language specification, the unspecified language
 * (empty string) is used as the key. The system must not assume or infer a language for
 * such entries.
 * <p>
 * An empty MultilingualName (with no language versions) represents an unknown or unspecified
 * name. The absence of a name must be represented as an empty MultilingualName, not as null.
 *
 * @param values an immutable map of language codes to name values
 */
public record MultilingualName(Map<String, String> values) {

    /**
     * Maximum length of a name value in characters.
     */
    public static final int MAX_VALUE_LENGTH = 200;

    /**
     * Language code representing an unspecified language (for manually entered names).
     */
    public static final String UNSPECIFIED_LANGUAGE = "";

    private static final MultilingualName EMPTY = new MultilingualName(Map.of());

    private static final Pattern ISO_639_PATTERN = Pattern.compile("^[a-z]{2,3}$");

    /**
     * Compact constructor that validates and normalizes the input map.
     *
     * @param values the map of language codes to name values
     * @throws NullPointerException if values is null, or any key or value is null
     * @throws IllegalArgumentException if any language code is invalid or any value is blank/too long
     */
    public MultilingualName {
        Objects.requireNonNull(values, "values must not be null");
        var normalized = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            var languageCode = entry.getKey();
            var value = entry.getValue();
            Objects.requireNonNull(languageCode, "language code must not be null");
            Objects.requireNonNull(value, "value must not be null");
            validateLanguageCode(languageCode);
            validateValue(value);
            normalized.put(languageCode.toLowerCase(), value);
        }
        values = Map.copyOf(normalized);
    }

    private static void validateLanguageCode(String languageCode) {
        if (languageCode.isEmpty()) {
            return; // Unspecified language is allowed
        }
        if (!ISO_639_PATTERN.matcher(languageCode.toLowerCase()).matches()) {
            throw new IllegalArgumentException(
                    "invalid language code '" + languageCode + "': must be 2-3 lowercase letters (ISO 639)");
        }
    }

    private static void validateValue(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "value must not exceed " + MAX_VALUE_LENGTH + " characters");
        }
    }

    /**
     * Creates a MultilingualName from the given map of language codes to values.
     *
     * @param values the map of language codes to name values
     * @return the MultilingualName instance
     * @throws NullPointerException if values is null, or any key or value is null
     * @throws IllegalArgumentException if any language code is invalid or any value is blank/too long
     */
    public static MultilingualName of(Map<String, String> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        return new MultilingualName(values);
    }

    /**
     * Creates a MultilingualName with a single language version.
     *
     * @param languageCode the ISO 639 language code
     * @param value the name value
     * @return the MultilingualName instance
     * @throws NullPointerException if languageCode or value is null
     * @throws IllegalArgumentException if languageCode is invalid or value is blank/too long
     */
    public static MultilingualName of(String languageCode, String value) {
        return new MultilingualName(Map.of(languageCode, value));
    }

    /**
     * Creates a MultilingualName with an unspecified language (for manually entered names).
     *
     * @param value the name value
     * @return the MultilingualName instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is blank or too long
     */
    public static MultilingualName withUnspecifiedLanguage(String value) {
        return new MultilingualName(Map.of(UNSPECIFIED_LANGUAGE, value));
    }

    /**
     * Returns an empty MultilingualName representing an unknown or unspecified name.
     *
     * @return the empty MultilingualName instance
     */
    public static MultilingualName empty() {
        return EMPTY;
    }

    /**
     * Gets the name value for the specified language code.
     *
     * @param languageCode the ISO 639 language code (or empty string for unspecified)
     * @return the name value, or empty if not present
     */
    public Optional<String> get(@Nullable String languageCode) {
        if (languageCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(languageCode.toLowerCase()));
    }

    /**
     * Gets the name value for the unspecified language.
     *
     * @return the unspecified language value, or empty if not present
     */
    @JsonIgnore
    public Optional<String> getUnspecified() {
        return get(UNSPECIFIED_LANGUAGE);
    }

    /**
     * Checks if this multilingual name has a value for the specified language code.
     *
     * @param languageCode the ISO 639 language code (or empty string for unspecified)
     * @return true if a value exists for the language code
     */
    public boolean hasLanguage(@Nullable String languageCode) {
        if (languageCode == null) {
            return false;
        }
        return values.containsKey(languageCode.toLowerCase());
    }

    /**
     * Checks if this multilingual name is empty (has no language versions).
     *
     * @return true if empty
     */
    @JsonIgnore
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns the set of language codes in this multilingual name, excluding the unspecified language.
     *
     * @return set of ISO 639 language codes (excluding empty string)
     */
    public Set<String> languageCodes() {
        return values.keySet().stream()
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the number of language versions in this multilingual name.
     *
     * @return the number of language versions
     */
    public int size() {
        return values.size();
    }

    /**
     * Returns any available value from this multilingual name.
     * <p>
     * This is useful when you need to display a name but don't have a preferred language.
     * The specific value returned is implementation-dependent.
     *
     * @return any value, or empty if this multilingual name is empty
     */
    public Optional<String> anyValue() {
        return values.values().stream().findFirst();
    }
}
