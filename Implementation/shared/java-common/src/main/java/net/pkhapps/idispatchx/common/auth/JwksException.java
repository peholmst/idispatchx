package net.pkhapps.idispatchx.common.auth;

/**
 * Exception thrown when JWKS operations fail.
 */
public class JwksException extends RuntimeException {

    /**
     * Creates a new JWKS exception with the given message.
     *
     * @param message the error message
     */
    public JwksException(String message) {
        super(message);
    }

    /**
     * Creates a new JWKS exception with the given message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public JwksException(String message, Throwable cause) {
        super(message, cause);
    }
}
