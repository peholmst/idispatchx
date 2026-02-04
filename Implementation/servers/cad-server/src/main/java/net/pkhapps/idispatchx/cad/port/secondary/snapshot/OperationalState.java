package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.unit.UnitStatus;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents the complete operational state of the CAD Server.
 * <p>
 * This record contains only operational data (not reference data).
 * It is used for snapshot creation and restoration.
 *
 * @param incidents    all active incidents
 * @param calls        all active calls
 * @param unitStatuses all unit statuses
 */
public record OperationalState(
        Collection<Incident> incidents,
        Collection<Call> calls,
        Collection<UnitStatus> unitStatuses
) {

    public OperationalState {
        incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents must not be null"));
        calls = List.copyOf(Objects.requireNonNull(calls, "calls must not be null"));
        unitStatuses = List.copyOf(Objects.requireNonNull(unitStatuses, "unitStatuses must not be null"));
    }

    /**
     * Returns an empty operational state.
     */
    public static OperationalState empty() {
        return new OperationalState(List.of(), List.of(), List.of());
    }
}
