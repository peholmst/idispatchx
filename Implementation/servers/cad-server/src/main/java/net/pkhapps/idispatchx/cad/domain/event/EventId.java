package net.pkhapps.idispatchx.cad.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * A unique identifier for a {@link DomainEvent}.
 */
public record EventId(String value) {

    public EventId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    /**
     * Generates a new random EventId using UUID.
     */
    public static EventId generate() {
        return new EventId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
