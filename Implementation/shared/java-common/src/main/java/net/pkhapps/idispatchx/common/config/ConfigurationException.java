package net.pkhapps.idispatchx.common.config;

/**
 * Exception thrown when configuration loading or validation fails.
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Creates a new configuration exception with the given message.
     *
     * @param message the error message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new configuration exception with the given message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
