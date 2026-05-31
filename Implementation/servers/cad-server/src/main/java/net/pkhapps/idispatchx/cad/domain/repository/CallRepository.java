package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;

import java.util.stream.Stream;

/**
 * Repository for {@link Call} entities.
 */
public interface CallRepository extends Repository<Call, CallId> {

    /**
     * Returns all calls in state {@code ACTIVE}.
     */
    Stream<Call> findActive();

    /**
     * Returns all calls linked to the given incident.
     */
    Stream<Call> findByIncidentId(IncidentId incidentId);
}
