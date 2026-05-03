package net.pkhapps.idispatchx.cad.port.secondary.commandlog;

/**
 * Thrown when a command cannot be written to the audit log.
 */
public class CommandLogException extends RuntimeException {

    public CommandLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
