package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;

import java.util.Optional;

/**
 * Port for snapshot operations.
 * <p>
 * Per ADR-0006, snapshots capture the complete operational state for faster
 * startup recovery. Snapshots use the same format (text/JSON or binary) as the WAL.
 */
public interface SnapshotPort {

    /**
     * Creates a snapshot of the operational state.
     * <p>
     * The snapshot is written atomically using a temporary file followed by rename.
     *
     * @param state          the operational state to snapshot
     * @param upToSequence   the sequence number of the last WAL entry included
     * @throws SnapshotWriteException if the snapshot cannot be written
     */
    void createSnapshot(OperationalState state, SequenceNumber upToSequence);

    /**
     * Loads the latest valid snapshot.
     *
     * @return the latest snapshot, or empty if no snapshot exists
     * @throws SnapshotReadException if the snapshot exists but cannot be read
     */
    Optional<Snapshot> loadLatestSnapshot();

    /**
     * Purges snapshots older than the given sequence number.
     * <p>
     * This is called after a new snapshot is successfully written.
     * Purging is performed asynchronously and must not block normal operations.
     *
     * @param keepAfter purge snapshots with sequence numbers before this value
     */
    void purgeOlderSnapshots(SequenceNumber keepAfter);
}
