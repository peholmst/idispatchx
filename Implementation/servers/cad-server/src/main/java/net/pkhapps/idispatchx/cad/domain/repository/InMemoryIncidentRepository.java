package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentState;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe in-memory implementation of {@link IncidentRepository}.
 * <p>
 * Reads are served directly from a {@link ConcurrentHashMap}.
 * Writes must be performed while holding the appropriate lock via
 * {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 */
public class InMemoryIncidentRepository implements IncidentRepository {

    private final ConcurrentHashMap<IncidentId, Incident> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Incident> findById(IncidentId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Stream<Incident> findAll() {
        return store.values().stream();
    }

    @Override
    public void add(Incident incident) {
        store.put(incident.id(), incident);
    }

    @Override
    public void remove(IncidentId id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(IncidentId id) {
        return store.containsKey(id);
    }

    @Override
    public Stream<Incident> findActive() {
        return store.values().stream().filter(i -> i.state() != IncidentState.ENDED);
    }
}
