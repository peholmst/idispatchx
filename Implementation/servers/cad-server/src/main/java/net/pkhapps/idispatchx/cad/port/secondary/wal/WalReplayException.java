package net.pkhapps.idispatchx.cad.port.secondary.wal;

/**
 * Exception thrown when a corrupt WAL entry is encountered during replay in
 * {@link net.pkhapps.idispatchx.cad.adapter.secondary.wal.CorruptionMode#STRICT} mode.
 */
public class WalReplayException extends RuntimeException {

    public WalReplayException(String message) {
        super(message);
    }

    public WalReplayException(String message, Throwable cause) {
        super(message, cause);
    }
}
