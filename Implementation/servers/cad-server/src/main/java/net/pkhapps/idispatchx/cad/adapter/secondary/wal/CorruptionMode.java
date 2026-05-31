package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

/**
 * Controls how the WAL adapter handles corrupt entries during replay.
 * <p>
 * Per ADR-0006: "The appropriate mode depends on deployment requirements and operational procedures."
 */
public enum CorruptionMode {
    /**
     * Halt immediately on the first corrupt WAL entry by throwing {@link net.pkhapps.idispatchx.cad.port.secondary.wal.WalReplayException}.
     */
    STRICT,
    /**
     * Log a warning, skip the corrupt entry, and continue replay.
     */
    LENIENT
}
