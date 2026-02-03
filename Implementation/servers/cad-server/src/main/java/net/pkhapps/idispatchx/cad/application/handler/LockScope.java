package net.pkhapps.idispatchx.cad.application.handler;

import java.util.List;
import java.util.Objects;

/**
 * Defines the scope of locks to acquire for a command.
 * <p>
 * A scope contains one or more {@link LockKey}s that identify the aggregates
 * that need to be locked before executing a command.
 */
public record LockScope(List<LockKey> keys) {

    public LockScope {
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        keys = List.copyOf(keys); // Defensive copy, immutable
    }

    /**
     * Creates a lock scope for a single aggregate.
     */
    public static LockScope of(String type, String id) {
        return new LockScope(List.of(new LockKey(type, id)));
    }

    /**
     * Creates a lock scope for multiple aggregates.
     */
    public static LockScope of(LockKey... keys) {
        return new LockScope(List.of(keys));
    }
}
