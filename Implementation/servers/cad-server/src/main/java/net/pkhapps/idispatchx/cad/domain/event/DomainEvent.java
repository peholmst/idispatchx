package net.pkhapps.idispatchx.cad.domain.event;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;

import java.time.Instant;

/**
 * Marker interface for all domain events in the CAD Server.
 * <p>
 * Events represent facts that have occurred and are written to the WAL.
 * They are used for state reconstruction during replay on startup.
 */
public interface DomainEvent {

    /**
     * Returns the unique identifier for this event.
     */
    EventId eventId();

    /**
     * Returns the timestamp when this event occurred (UTC).
     */
    Instant timestamp();

    /**
     * Returns the ID of the command that caused this event, used for idempotency tracking.
     * May be null for system-generated events.
     */
    CommandId causedBy();
}
