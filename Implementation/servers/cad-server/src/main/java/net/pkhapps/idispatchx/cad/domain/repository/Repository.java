package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.shared.Entity;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Generic in-memory repository interface for domain entities.
 * <p>
 * Reads via {@link #findById} and {@link #findAll} are thread-safe (backed by
 * {@link java.util.concurrent.ConcurrentHashMap}). Writes must be performed while
 * holding the appropriate lock via
 * {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 *
 * @param <E>  the entity type
 * @param <ID> the entity's identifier type
 */
public interface Repository<E extends Entity<ID>, ID> {

    Optional<E> findById(ID id);

    Stream<E> findAll();

    void add(E entity);

    void remove(ID id);

    boolean existsById(ID id);
}
