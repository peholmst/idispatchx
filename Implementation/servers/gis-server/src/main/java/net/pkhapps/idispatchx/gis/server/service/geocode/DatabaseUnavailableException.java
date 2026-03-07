package net.pkhapps.idispatchx.gis.server.service.geocode;

/**
 * Exception thrown when a geocoding operation fails due to database unavailability.
 */
public class DatabaseUnavailableException extends RuntimeException {

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
