package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

/**
 * The encoding format for WAL segment files and snapshot files.
 * <p>
 * Per ADR-0006, both formats must support the same logical operations and replay semantics.
 * Snapshots must use the same format as the WAL.
 */
public enum WalFormat {
    /** Newline-delimited JSON. For development and debugging. */
    TEXT,
    /** Jackson SMILE compact binary. For production use. */
    BINARY
}
