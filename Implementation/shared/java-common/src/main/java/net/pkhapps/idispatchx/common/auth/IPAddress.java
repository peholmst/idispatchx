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
     * Creates an IPAddress with validation.
     *
     * @param value the IP address string
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank
     */
    public IPAddress {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
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
