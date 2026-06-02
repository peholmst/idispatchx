package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentPriority;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentType;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event raised when a new {@link net.pkhapps.idispatchx.cad.domain.model.incident.Incident} is created.
 * <p>
 * {@link #timestamp()} records when the incident was created.
 */
public record IncidentCreatedEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        IncidentId incidentId,
        @Nullable CallId sourceCallId,
        @Nullable IncidentType incidentType,
        @Nullable IncidentPriority incidentPriority,
        @Nullable Location location,
        @Nullable Description description
) implements DomainEvent {

    public IncidentCreatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(incidentId, "incidentId must not be null");
    }
}
