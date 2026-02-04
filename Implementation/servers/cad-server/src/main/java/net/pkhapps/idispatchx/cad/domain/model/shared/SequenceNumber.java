package net.pkhapps.idispatchx.cad.domain.model.shared;

/**
 * Domain primitive representing a WAL sequence number.
 * <p>
 * Sequence numbers are positive and monotonically increasing.
 * They are used to track position in the WAL for snapshot-based recovery.
 *
 * @param value the sequence number value, must be positive
 */
public record SequenceNumber(long value) {

    public SequenceNumber {
        if (value <= 0) {
            throw new IllegalArgumentException("sequence number must be positive: " + value);
        }
    }

    /**
     * Returns a sequence number representing the start of the WAL (before any entries).
     */
    public static SequenceNumber start() {
        return new SequenceNumber(1);
    }

    /**
     * Returns the next sequence number.
     */
    public SequenceNumber next() {
        return new SequenceNumber(value + 1);
    }

    /**
     * Returns true if this sequence number is before the given sequence number.
     */
    public boolean isBefore(SequenceNumber other) {
        return this.value < other.value;
    }

    /**
     * Returns true if this sequence number is after the given sequence number.
     */
    public boolean isAfter(SequenceNumber other) {
        return this.value > other.value;
    }
}
