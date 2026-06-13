package net.pkhapps.idispatchx.cad.domain.model.incident;

import java.util.Objects;

/**
 * Unique identifier for an {@link IncidentLogEntry}.
 * <p>
 * Wraps a Nano ID string (21 URL-safe characters: [A-Za-z0-9_-]).
 *
 * @param value the Nano ID string
 */
public record IncidentLogEntryId(String value) {

    private static final int NANO_ID_LENGTH = 21;

    public IncidentLogEntryId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() != NANO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "IncidentLogEntryId must be exactly " + NANO_ID_LENGTH + " characters, got: " + value.length());
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isNanoIdChar(value.charAt(i))) {
                throw new IllegalArgumentException(
                        "IncidentLogEntryId contains invalid character at position " + i + ": " + value);
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
