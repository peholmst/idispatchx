package net.pkhapps.idispatchx.cad.port.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port for Write-Ahead Log operations.
 * <p>
 * Per Performance NFR: No state can be updated before the event has been
 * written to WAL and synced.
 */
public interface WalPort {

    /**
     * Writes an event to the WAL and blocks until synced to disk.
     *
     * @param event the event to write
     * @throws WalWriteException if write or sync fails
     */
    void write(DomainEvent event);

    /**
     * Writes multiple events atomically to the WAL and blocks until synced.
     * All events are written as a single batch or none.
     *
     * @param events the events to write
     * @throws WalWriteException if write or sync fails
     */
    void writeBatch(List<? extends DomainEvent> events);

    /**
     * Replays all events from the WAL in order.
     * Called on startup to reconstruct in-memory state.
     *
     * @param consumer receives each event in order
     */
    void replay(Consumer<DomainEvent> consumer);

    /**
     * Truncates WAL entries up to the given event ID (inclusive).
     * Called after archival to prevent unbounded WAL growth.
     *
     * @param upToEventId truncate all events up to and including this ID
     */
    void truncate(EventId upToEventId);
}
