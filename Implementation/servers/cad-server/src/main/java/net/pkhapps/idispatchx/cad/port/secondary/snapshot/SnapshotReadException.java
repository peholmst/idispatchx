package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

/**
 * Exception thrown when a snapshot exists but cannot be read.
 */
public class SnapshotReadException extends RuntimeException {

    public SnapshotReadException(String message) {
        super(message);
    }

    public SnapshotReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
