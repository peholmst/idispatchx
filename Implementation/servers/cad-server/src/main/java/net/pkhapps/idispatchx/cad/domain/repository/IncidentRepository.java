package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;

import java.util.stream.Stream;

/**
 * Repository for {@link Incident} entities.
 */
public interface IncidentRepository extends Repository<Incident, IncidentId> {

    /**
     * Returns all incidents not in state {@code ENDED}.
     */
    Stream<Incident> findActive();
}
