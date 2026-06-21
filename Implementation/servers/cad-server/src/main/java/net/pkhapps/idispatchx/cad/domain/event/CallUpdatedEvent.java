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
 * <p>
 * The {@code clearXxx} flags signal an explicit field clear. When {@code true}, the corresponding
 * field is set to {@code null} during replay even if the field value is {@code null} in the event
 * (which is otherwise used to mean "field not changed by this event"). {@code null} clear flags
 * (absent in serialized form) are treated as {@code false} for backward compatibility with
 * older WAL entries.
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
        @Nullable IncidentId incidentId,
        @Nullable Boolean clearCallerName,
        @Nullable Boolean clearCallerPhoneNumber,
        @Nullable Boolean clearLocation,
        @Nullable Boolean clearDescription
) implements DomainEvent {

    public CallUpdatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }

}
