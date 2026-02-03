package net.pkhapps.idispatchx.cad.domain.command;

import java.util.Objects;
import java.util.UUID;

/**
 * A unique identifier for a {@link Command}, used for idempotency tracking.
 */
public record CommandId(String value) {

    public CommandId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
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
