package net.pkhapps.idispatchx.cad.domain.command;

/**
 * Marker interface for all commands in the CAD Server domain.
 * <p>
 * Commands represent intent to change the state of the system. Each command
 * carries a unique {@link CommandId} for idempotency tracking.
 */
public interface Command {

    /**
     * Returns the unique identifier for this command, used for idempotency.
     */
    CommandId commandId();
}
