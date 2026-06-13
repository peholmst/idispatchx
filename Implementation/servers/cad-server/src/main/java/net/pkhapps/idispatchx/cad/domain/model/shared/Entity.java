package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.util.Objects;

/**
 * Base class for all domain entities.
 * <p>
 * Entities are identified by their ID and are equal when their IDs are equal.
 * Entities are NOT internally synchronized; thread-safety is provided externally
 * via {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 *
 * @param <ID> the type of this entity's identifier
 */
public abstract class Entity<ID> {

    private final ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public ID id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
