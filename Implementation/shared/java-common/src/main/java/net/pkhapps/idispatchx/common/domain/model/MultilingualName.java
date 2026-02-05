package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A value object representing a multilingual name that can have multiple language versions.
 * <p>
 * In Finland, names are typically in Finnish, Swedish, or both. In Lapland, Sami languages
 * are also used. This class supports ISO 639 language codes (ISO 639-1 where available,
 * otherwise ISO 639-2 or 639-3).
 * <p>
 * When a name is manually entered without language specification, {@link Language#unspecified()}
 * is used as the key. The system must not assume or infer a language for such entries.
 * <p>
 * An empty MultilingualName (with no language versions) represents an unknown or unspecified
 * name. The absence of a name must be represented as an empty MultilingualName, not as null.
 *
 * @param values an immutable map of languages to name values
 */
public record MultilingualName(Map<Language, String> values) {

    /**
     * Maximum length of a name value in characters.
     */
    public static final int MAX_VALUE_LENGTH = 200;

    private static final MultilingualName EMPTY = new MultilingualName(Map.of());

    /**
     * Compact constructor that validates and normalizes the input map.
     *
     * @param values the map of languages to name values
     * @throws NullPointerException     if values is null, or any key or value is null
     * @throws IllegalArgumentException if any value is blank or too long
     */
    public MultilingualName {
        Objects.requireNonNull(values, "values must not be null");
        for (var entry : values.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "language must not be null");
            Objects.requireNonNull(entry.getValue(), "value must not be null");
            validateValue(entry.getValue());
        }
        values = Map.copyOf(values);
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
     * Creates a MultilingualName from the given map of languages to values.
     *
     * @param values the map of languages to name values
     * @return the MultilingualName instance
     * @throws NullPointerException     if values is null, or any key or value is null
     * @throws IllegalArgumentException if any value is blank or too long
     */
    @JsonCreator
    public static MultilingualName of(Map<Language, String> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        return new MultilingualName(values);
    }

    /**
     * Returns the immutable map of languages to name values.
     * This is the JSON serialization method.
     *
     * @return map of languages to values
     */
    @JsonValue
    @Override
    public Map<Language, String> values() {
        return values;
    }

    /**
     * Creates a MultilingualName with a single language version.
     *
     * @param language the language
     * @param value    the name value
     * @return the MultilingualName instance
     * @throws NullPointerException     if language or value is null
     * @throws IllegalArgumentException if value is blank or too long
     */
    public static MultilingualName of(Language language, String value) {
        return new MultilingualName(Map.of(language, value));
    }

    /**
     * Creates a MultilingualName with an unspecified language (for manually entered names).
     *
     * @param value the name value
     * @return the MultilingualName instance
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank or too long
     */
    public static MultilingualName withUnspecifiedLanguage(String value) {
        return new MultilingualName(Map.of(Language.unspecified(), value));
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
     * Gets the name value for the specified language.
     *
     * @param language the language (or {@link Language#unspecified()} for unspecified)
     * @return the name value, or empty if not present
     */
    public Optional<String> get(@Nullable Language language) {
        if (language == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(language));
    }

    /**
     * Gets the name value for the unspecified language.
     *
     * @return the unspecified language value, or empty if not present
     */
    public Optional<String> getUnspecified() {
        return get(Language.unspecified());
    }

    /**
     * Checks if this multilingual name has a value for the specified language.
     *
     * @param language the language (or {@link Language#unspecified()} for unspecified)
     * @return true if a value exists for the language
     */
    public boolean hasLanguage(@Nullable Language language) {
        if (language == null) {
            return false;
        }
        return values.containsKey(language);
    }

    /**
     * Checks if this multilingual name is empty (has no language versions).
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns the set of languages in this multilingual name, excluding the unspecified language.
     *
     * @return set of languages (excluding {@link Language#unspecified()})
     */
    public Set<Language> languages() {
        return values.keySet().stream()
                .filter(language -> !language.isUnspecified())
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
