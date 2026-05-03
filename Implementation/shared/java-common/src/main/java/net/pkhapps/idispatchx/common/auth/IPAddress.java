package net.pkhapps.idispatchx.common.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * A value object representing an IP address (IPv4 or IPv6).
 *
 * @param value the IP address string
 */
public record IPAddress(String value) {

    /**
     * Maximum allowed length (covers the longest valid IPv6 address with zone identifier).
     */
    static final int MAX_LENGTH = 45;

    /**
     * Creates an IPAddress with validation.
     *
     * @param value the IP address string
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank, too long, or contains illegal characters
     */
    public IPAddress {
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
                        "value contains character illegal in an IP address: '" + value.charAt(i) + "'");
            }
        }
    }

    private static boolean isLegalChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == '.' || c == ':'
                || c == '%'              // zone ID separator
                || (c >= 'g' && c <= 'z')  // zone ID interface name
                || (c >= 'G' && c <= 'Z')
                || c == '-' || c == '_'; // zone ID interface name
    }

    /**
     * Creates an IPAddress from the given string.
     *
     * @param value the IP address string
     * @return the IPAddress instance
     */
    @JsonCreator
    public static IPAddress of(String value) {
        return new IPAddress(value);
    }

    /**
     * Returns the IP address string. Used by Jackson for serialization.
     *
     * @return the IP address
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
