package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Event raised when a new {@link net.pkhapps.idispatchx.cad.domain.model.call.Call} is created.
 * <p>
 * The receiving dispatcher is recovered on WAL replay via {@link #causedByUser()}.
 */
public record CallCreatedEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        CallId callId,
        @Nullable CallerName callerName,
        @Nullable PhoneNumber callerPhoneNumber,
        @Nullable Location location,
        @Nullable Description description
) implements DomainEvent {

    public CallCreatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }

}
