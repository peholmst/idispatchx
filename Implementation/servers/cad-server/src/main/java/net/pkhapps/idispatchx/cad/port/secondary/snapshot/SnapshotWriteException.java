package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

/**
 * Exception thrown when a snapshot cannot be written.
 */
public class SnapshotWriteException extends RuntimeException {

    public SnapshotWriteException(String message) {
        super(message);
    }

    public SnapshotWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
