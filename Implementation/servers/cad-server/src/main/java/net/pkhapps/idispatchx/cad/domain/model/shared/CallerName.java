package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.util.Objects;

/**
 * The name of a caller, with a maximum length of 100 characters.
 *
 * @param value the caller name string
 */
public record CallerName(String value) {

    private static final int MAX_LENGTH = 100;

    public CallerName {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "CallerName must not exceed " + MAX_LENGTH + " characters, got: " + value.length());
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
