package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentPriority;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentType;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Serializable snapshot DTO for {@link Incident}.
 * <p>
 * Used in place of the live aggregate in {@link OperationalState} so that snapshot
 * serialization does not require Jackson annotations on domain classes.
 */
public record IncidentSnapshot(
        IncidentId id,
        IncidentState state,
        Instant incidentCreated,
        @Nullable IncidentType incidentType,
        @Nullable IncidentPriority incidentPriority,
        @Nullable Location location,
        @Nullable Description description,
        List<IncidentLogEntry> logEntries
) {

    public IncidentSnapshot {
        logEntries = List.copyOf(logEntries);
    }

    /** Creates a snapshot DTO from a live {@link Incident} aggregate. */
    public static IncidentSnapshot from(Incident incident) {
        return new IncidentSnapshot(
                incident.id(),
                incident.state(),
                incident.incidentCreated(),
                incident.incidentType(),
                incident.incidentPriority(),
                incident.location(),
                incident.description(),
                incident.logEntries()
        );
    }

    /** Reconstructs a live {@link Incident} aggregate from this snapshot DTO. */
    public Incident toIncident() {
        return Incident.restore(id, state, incidentCreated, incidentType, incidentPriority,
                location, description, logEntries);
    }
}
