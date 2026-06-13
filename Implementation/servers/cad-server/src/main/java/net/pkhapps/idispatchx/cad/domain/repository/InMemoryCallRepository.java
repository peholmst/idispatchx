package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe in-memory implementation of {@link CallRepository}.
 * <p>
 * Reads are served directly from a {@link ConcurrentHashMap}.
 * Writes must be performed while holding the appropriate lock via
 * {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 */
public class InMemoryCallRepository implements CallRepository {

    private final ConcurrentHashMap<CallId, Call> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Call> findById(CallId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Stream<Call> findAll() {
        return store.values().stream();
    }

    @Override
    public void add(Call call) {
        store.put(call.id(), call);
    }

    @Override
    public void remove(CallId id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(CallId id) {
        return store.containsKey(id);
    }

    @Override
    public Stream<Call> findActive() {
        return store.values().stream().filter(c -> c.state() == CallState.ACTIVE);
    }

    @Override
    public Stream<Call> findByIncidentId(IncidentId incidentId) {
        return store.values().stream()
                .filter(c -> incidentId.equals(c.incidentId()));
    }
}
