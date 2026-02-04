package net.pkhapps.idispatchx.cad.port.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;

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
     * @return the sequence number assigned to this event
     * @throws WalWriteException if write or sync fails
     */
    SequenceNumber write(DomainEvent event);

    /**
     * Writes multiple events atomically to the WAL and blocks until synced.
     * All events are written as a single batch or none.
     *
     * @param events the events to write
     * @return the sequence number of the last event in the batch
     * @throws WalWriteException if write or sync fails
     */
    SequenceNumber writeBatch(List<? extends DomainEvent> events);

    /**
     * Replays events from the WAL starting after the given sequence number.
     * Used for startup recovery when a snapshot is present.
     *
     * @param from     replay events after this sequence number
     * @param consumer receives each event in order
     */
    void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer);

    /**
     * Replays all events from the WAL in order.
     * Called on startup to reconstruct in-memory state when no snapshot exists.
     *
     * @param consumer receives each event in order
     */
    void replay(Consumer<DomainEvent> consumer);

    /**
     * Truncates WAL entries up to the given sequence number (inclusive).
     * Called after snapshot creation to prevent unbounded WAL growth.
     *
     * @param upTo truncate all entries up to and including this sequence number
     */
    void truncate(SequenceNumber upTo);

    /**
     * Returns the current (highest) sequence number in the WAL.
     * Returns a sequence number representing "start" if the WAL is empty.
     *
     * @return the current sequence number
     */
    SequenceNumber currentSequence();
}
