package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event raised when an {@link IncidentLogEntry} is appended to an incident.
 * <p>
 * The {@code logEntry} is a complete snapshot of the entry for WAL replay.
 */
public record IncidentLogEntryAddedEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        UserId causedByUser,
        IncidentId incidentId,
        IncidentLogEntry logEntry
) implements DomainEvent {

    public IncidentLogEntryAddedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(logEntry, "logEntry must not be null");
    }

    @Override
    public UserId causedByUser() {
        return causedByUser;
    }
}
