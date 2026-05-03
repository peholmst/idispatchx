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
     * Creates a UserId with validation.
     *
     * @param value the user identifier
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank
     */
    public UserId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
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
