package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;

import java.util.Objects;

/**
 * Represents a snapshot of the operational state at a specific WAL sequence number.
 * <p>
 * Per ADR-0006, snapshots capture the complete operational state and include
 * the sequence number of the last WAL entry they contain.
 *
 * @param state          the operational state at the time of the snapshot
 * @param sequenceNumber the sequence number of the last WAL entry included in this snapshot
 */
public record Snapshot(
        OperationalState state,
        SequenceNumber sequenceNumber
) {

    public Snapshot {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(sequenceNumber, "sequenceNumber must not be null");
    }
}
