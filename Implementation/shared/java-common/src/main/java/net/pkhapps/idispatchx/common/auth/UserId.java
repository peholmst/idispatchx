package net.pkhapps.idispatchx.common.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * A value object representing the identity of a user, corresponding to the {@code sub} OIDC claim.
 *
 * @param value the user identifier string
 */
public record UserId(String value) {

    /**
     * Special constant used when the system has performed an operation on its own, without a human user.
     */
    public static final UserId SYSTEM = new UserId("SYSTEM");

    /**
     * Maximum allowed length, matching the maximum length of an email address per RFC 5321.
     */
    static final int MAX_LENGTH = 254;

    /**
     * Creates a UserId with validation.
     *
     * @param value the user identifier
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank, too long, or contains illegal characters
     */
    public UserId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "value exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isLegalChar(value.charAt(i))) {
                throw new IllegalArgumentException(
                        "value contains character illegal in a user identifier: '" + value.charAt(i) + "'");
            }
        }
    }

    private static boolean isLegalChar(char c) {
        // Union of characters legal in UUIDs, NanoIDs, or email addresses
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '-' || c == '_' || c == '.'
                || c == '@' || c == '+'
                || c == '!' || c == '#' || c == '$' || c == '%'
                || c == '&' || c == '\'' || c == '*' || c == '/'
                || c == '=' || c == '?' || c == '^'
                || c == '{' || c == '|' || c == '}' || c == '~';
    }

    /**
     * Creates a UserId from the given string.
     *
     * @param value the user identifier
     * @return the UserId instance
     */
    @JsonCreator
    public static UserId of(String value) {
        return new UserId(value);
    }

    /**
     * Returns the user identifier string. Used by Jackson for serialization.
     *
     * @return the user identifier
     */
    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
