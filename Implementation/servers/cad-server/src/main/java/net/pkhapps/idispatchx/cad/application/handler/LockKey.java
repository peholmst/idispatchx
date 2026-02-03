package net.pkhapps.idispatchx.cad.application.handler;

import java.util.Objects;

/**
 * A key identifying a lockable resource, consisting of a type and an ID.
 * <p>
 * Lock keys are comparable to ensure consistent ordering when acquiring multiple locks,
 * which prevents deadlocks.
 */
public record LockKey(String type, String id) implements Comparable<LockKey> {

    public LockKey {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }

    @Override
    public int compareTo(LockKey other) {
        int typeCompare = type.compareTo(other.type);
        return typeCompare != 0 ? typeCompare : id.compareTo(other.id);
    }
}
