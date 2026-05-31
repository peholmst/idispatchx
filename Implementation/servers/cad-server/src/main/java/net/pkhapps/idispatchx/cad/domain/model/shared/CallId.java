package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.util.Objects;

/**
 * Unique identifier for a {@link net.pkhapps.idispatchx.cad.domain.model.call.Call}.
 * <p>
 * Wraps a Nano ID string (21 URL-safe characters: [A-Za-z0-9_-]).
 *
 * @param value the Nano ID string
 */
public record CallId(String value) {

    private static final int NANO_ID_LENGTH = 21;

    public CallId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() != NANO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "CallId must be exactly " + NANO_ID_LENGTH + " characters, got: " + value.length());
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isNanoIdChar(value.charAt(i))) {
                throw new IllegalArgumentException(
                        "CallId contains invalid character at position " + i + ": " + value);
            }
        }
    }

    private static boolean isNanoIdChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    @Override
    public String toString() {
        return value;
    }
}
