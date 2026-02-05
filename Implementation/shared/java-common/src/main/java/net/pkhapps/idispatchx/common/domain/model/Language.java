package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A value object representing an ISO 639 language code.
 * <p>
 * Supports ISO 639-1 (2-letter) and ISO 639-2/3 (3-letter) codes. An empty string
 * represents an unspecified language, used for manually entered names where the language
 * is not known.
 * <p>
 * The system must not assume or infer a language for unspecified entries.
 *
 * @param code the ISO 639 language code in lowercase, or empty string for unspecified
 */
public record Language(String code) {

    private static final Language UNSPECIFIED = new Language("");
    private static final Pattern ISO_639_PATTERN = Pattern.compile("^[a-z]{2,3}$");

    /**
     * Compact constructor that validates and normalizes the language code.
     *
     * @param code the language code
     * @throws NullPointerException     if code is null
     * @throws IllegalArgumentException if code is not a valid ISO 639 language code
     */
    public Language {
        Objects.requireNonNull(code, "code must not be null");
        if (!code.isEmpty()) {
            code = code.toLowerCase();
            if (!ISO_639_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException(
                        "invalid language code '" + code + "': must be 2-3 lowercase letters (ISO 639)");
            }
        }
    }

    /**
     * Creates a Language from the given language code string.
     *
     * @param code the language code (will be normalized to lowercase)
     * @return the Language instance
     * @throws NullPointerException     if code is null
     * @throws IllegalArgumentException if code is not a valid ISO 639 language code
     */
    @JsonCreator
    public static Language of(String code) {
        if (code.isEmpty()) {
            return UNSPECIFIED;
        }
        return new Language(code);
    }

    /**
     * Creates a Language from a {@link Locale}. The locale must contain only a language code
     * with no script, country, or variant.
     *
     * @param locale the locale to extract the language code from
     * @return the Language instance
     * @throws NullPointerException     if locale is null
     * @throws IllegalArgumentException if locale contains script, country, or variant
     */
    public static Language of(Locale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        var language = locale.getLanguage();
        if (language.isEmpty()) {
            if (!locale.getCountry().isEmpty() || !locale.getVariant().isEmpty()) {
                throw new IllegalArgumentException(
                        "invalid locale '" + locale.toLanguageTag() + "': unspecified language must use Locale.ROOT");
            }
            return UNSPECIFIED;
        }
        if (!locale.getScript().isEmpty() || !locale.getCountry().isEmpty() || !locale.getVariant().isEmpty()) {
            throw new IllegalArgumentException(
                    "invalid locale '" + locale.toLanguageTag() + "': only language code is allowed, no script/country/variant");
        }
        return new Language(language);
    }

    /**
     * Returns the Language instance representing an unspecified language.
     *
     * @return the unspecified language
     */
    public static Language unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns true if this language is unspecified (empty code).
     *
     * @return true if unspecified
     */
    public boolean isUnspecified() {
        return code.isEmpty();
    }

    /**
     * Converts this language to a {@link Locale}. Returns {@link Locale#ROOT} for unspecified.
     *
     * @return the corresponding locale
     */
    public Locale toLocale() {
        if (isUnspecified()) {
            return Locale.ROOT;
        }
        return Locale.of(code);
    }

    /**
     * Returns the language code string. Used by Jackson for serialization.
     *
     * @return the language code
     */
    @JsonValue
    @Override
    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
