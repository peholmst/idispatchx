package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event raised when a call is attached to an existing incident.
 */
public record CallAttachedToIncidentEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        CallId callId,
        IncidentId incidentId
) implements DomainEvent {

    public CallAttachedToIncidentEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(incidentId, "incidentId must not be null");
    }

}
