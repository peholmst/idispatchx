package net.pkhapps.idispatchx.cad.port.secondary.commandlog;

/**
 * Secondary port for recording commands in an append-only audit log.
 * <p>
 * All commands received by the CAD Server must be logged, including retries and failed ones.
 * Implementations must be thread-safe.
 */
public interface CommandLogPort {

    /**
     * Records a command in the audit log.
     *
     * @param entry the command log entry to record
     */
    void log(CommandLogEntry entry);
}
