package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event raised when call details are updated.
 * <p>
 * {@code incidentId} is set when this update links the call to an incident
 * (e.g. in {@code CreateIncidentFromCallCommandHandler}); null for ordinary field updates.
 */
public record CallUpdatedEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        CallId callId,
        @Nullable CallerName callerName,
        @Nullable PhoneNumber callerPhoneNumber,
        @Nullable Location location,
        @Nullable Description description,
        @Nullable CallOutcome outcome,
        @Nullable Description outcomeRationale,
        @Nullable IncidentId incidentId
) implements DomainEvent {

    public CallUpdatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }

}
