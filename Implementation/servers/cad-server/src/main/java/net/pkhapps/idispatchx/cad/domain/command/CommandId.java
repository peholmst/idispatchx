package net.pkhapps.idispatchx.cad.domain.command;

import java.util.Objects;
import java.util.UUID;

/**
 * A unique identifier for a {@link Command}, used for idempotency tracking.
 */
public record CommandId(String value) {

    /**
     * Maximum allowed length, matching the length of a UUID string representation.
     * NanoIDs are shorter (21 characters), so this covers both formats.
     */
    static final int MAX_LENGTH = 36;

    public CommandId {
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
                        "value contains character illegal in a NanoID or UUID: '" + value.charAt(i) + "'");
            }
        }
    }

    private static boolean isLegalChar(char c) {
        // Union of characters valid in NanoIDs ([A-Za-z0-9_-]) and UUIDs ([0-9a-fA-F-])
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '-' || c == '_';
    }

    /**
     * Generates a new random CommandId using UUID.
     */
    public static CommandId generate() {
        return new CommandId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
