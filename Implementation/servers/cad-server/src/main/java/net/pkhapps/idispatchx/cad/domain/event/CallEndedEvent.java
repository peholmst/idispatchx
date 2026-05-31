package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event raised when a call transitions to the {@code ENDED} state.
 */
public record CallEndedEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        CallId callId,
        Instant callEnded,
        CallOutcome outcome,
        @Nullable Description outcomeRationale,
        @Nullable IncidentId incidentId
) implements DomainEvent {

    public CallEndedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(callEnded, "callEnded must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    @Override
    public UserId causedByUser() {
        return causedByUser;
    }
}
