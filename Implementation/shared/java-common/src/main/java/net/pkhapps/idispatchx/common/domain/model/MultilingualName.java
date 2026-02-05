package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
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
 * When a name is manually entered without language specification, {@link Locale#ROOT} is used
 * as the key. The system must not assume or infer a language for such entries.
 * <p>
 * An empty MultilingualName (with no language versions) represents an unknown or unspecified
 * name. The absence of a name must be represented as an empty MultilingualName, not as null.
 *
 * @param values an immutable map of locales to name values
 */
public record MultilingualName(Map<Locale, String> values) {

    /**
     * Maximum length of a name value in characters.
     */
    public static final int MAX_VALUE_LENGTH = 200;

    /**
     * Locale representing an unspecified language (for manually entered names).
     */
    public static final Locale UNSPECIFIED_LANGUAGE = Locale.ROOT;

    private static final MultilingualName EMPTY = new MultilingualName(Map.of());

    private static final Pattern ISO_639_PATTERN = Pattern.compile("^[a-z]{2,3}$");

    /**
     * Compact constructor that validates and normalizes the input map.
     *
     * @param values the map of locales to name values
     * @throws NullPointerException     if values is null, or any key or value is null
     * @throws IllegalArgumentException if any locale is invalid or any value is blank/too long
     */
    public MultilingualName {
        Objects.requireNonNull(values, "values must not be null");
        var normalized = new LinkedHashMap<Locale, String>();
        for (var entry : values.entrySet()) {
            var locale = entry.getKey();
            var value = entry.getValue();
            Objects.requireNonNull(locale, "locale must not be null");
            Objects.requireNonNull(value, "value must not be null");
            validateLocale(locale);
            validateValue(value);
            normalized.put(normalizeLocale(locale), value);
        }
        values = Map.copyOf(normalized);
    }

    private static void validateLocale(Locale locale) {
        var language = locale.getLanguage();
        if (language.isEmpty()) {
            if (!locale.getCountry().isEmpty() || !locale.getVariant().isEmpty()) {
                throw new IllegalArgumentException(
                        "invalid locale '" + locale.toLanguageTag() + "': unspecified language must use Locale.ROOT");
            }
            return; // Locale.ROOT is allowed for unspecified language
        }
        if (!ISO_639_PATTERN.matcher(language).matches()) {
            throw new IllegalArgumentException(
                    "invalid locale '" + locale.toLanguageTag() + "': language must be 2-3 lowercase letters (ISO 639)");
        }
        if (!locale.getScript().isEmpty() || !locale.getCountry().isEmpty() || !locale.getVariant().isEmpty()) {
            throw new IllegalArgumentException(
                    "invalid locale '" + locale.toLanguageTag() + "': only language code is allowed, no script/country/variant");
        }
    }

    private static Locale normalizeLocale(Locale locale) {
        var language = locale.getLanguage();
        if (language.isEmpty()) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(language.toLowerCase());
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
     * Creates a MultilingualName from a map of language tags to values.
     * This is the JSON deserialization entry point.
     *
     * @param values the map of language tags to name values
     * @return the MultilingualName instance
     * @throws NullPointerException     if values is null, or any key or value is null
     * @throws IllegalArgumentException if any language tag is invalid or any value is blank/too long
     */
    @JsonCreator
    public static MultilingualName fromLanguageTags(Map<String, String> values) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            return EMPTY;
        }
        var localeMap = new LinkedHashMap<Locale, String>();
        for (var entry : values.entrySet()) {
            var languageTag = entry.getKey();
            Objects.requireNonNull(languageTag, "language tag must not be null");
            var locale = languageTag.isEmpty() ? Locale.ROOT : Locale.forLanguageTag(languageTag);
            localeMap.put(locale, entry.getValue());
        }
        return new MultilingualName(localeMap);
    }

    /**
     * Creates a MultilingualName from the given map of locales to values.
     *
     * @param values the map of locales to name values
     * @return the MultilingualName instance
     * @throws NullPointerException     if values is null, or any key or value is null
     * @throws IllegalArgumentException if any locale is invalid or any value is blank/too long
     */
    public static MultilingualName of(Map<Locale, String> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        return new MultilingualName(values);
    }

    /**
     * Creates a MultilingualName with a single language version.
     *
     * @param locale the locale (must have only a language code, no script/country/variant)
     * @param value  the name value
     * @return the MultilingualName instance
     * @throws NullPointerException     if locale or value is null
     * @throws IllegalArgumentException if locale is invalid or value is blank/too long
     */
    public static MultilingualName of(Locale locale, String value) {
        return new MultilingualName(Map.of(locale, value));
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
     * Returns the values as a map of language tags to name values.
     * This is the JSON serialization method.
     *
     * @return map of language tags to values
     */
    @JsonValue
    public Map<String, String> toLanguageTags() {
        var result = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            var languageTag = entry.getKey().equals(Locale.ROOT) ? "" : entry.getKey().toLanguageTag();
            result.put(languageTag, entry.getValue());
        }
        return result;
    }

    /**
     * Gets the name value for the specified locale.
     *
     * @param locale the locale (or {@link Locale#ROOT} for unspecified)
     * @return the name value, or empty if not present
     */
    public Optional<String> get(@Nullable Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(normalizeLocale(locale)));
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
     * Checks if this multilingual name has a value for the specified locale.
     *
     * @param locale the locale (or {@link Locale#ROOT} for unspecified)
     * @return true if a value exists for the locale
     */
    public boolean hasLanguage(@Nullable Locale locale) {
        if (locale == null) {
            return false;
        }
        return values.containsKey(normalizeLocale(locale));
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
     * Returns the set of locales in this multilingual name, excluding the unspecified language.
     *
     * @return set of locales (excluding {@link Locale#ROOT})
     */
    @JsonIgnore
    public Set<Locale> locales() {
        return values.keySet().stream()
                .filter(locale -> !locale.equals(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the number of language versions in this multilingual name.
     *
     * @return the number of language versions
     */
    @JsonIgnore
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
    @JsonIgnore
    public Optional<String> anyValue() {
        return values.values().stream().findFirst();
    }
}
