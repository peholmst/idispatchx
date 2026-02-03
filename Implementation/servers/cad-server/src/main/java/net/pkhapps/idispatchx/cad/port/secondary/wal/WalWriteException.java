package net.pkhapps.idispatchx.cad.port.secondary.wal;

/**
 * Exception thrown when writing to the WAL fails.
 */
public class WalWriteException extends RuntimeException {

    public WalWriteException(String message) {
        super(message);
    }

    public WalWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
