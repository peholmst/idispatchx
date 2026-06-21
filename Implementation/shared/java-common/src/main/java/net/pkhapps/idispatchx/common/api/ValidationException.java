package net.pkhapps.idispatchx.common.api;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Exception thrown when request validation fails.
 * <p>
 * This exception carries an error code and optional details map that are
 * used by the global exception handler to produce a standardized
 * {@link ErrorResponse} with HTTP 400 status.
 */
public final class ValidationException extends RuntimeException {

    private final String errorCode;
    private final @Nullable Map<String, Object> details;

    /**
     * Creates a validation exception with an error code and message.
     *
     * @param errorCode the error code (e.g., "INVALID_QUERY")
     * @param message   the human-readable error message
     */
    public ValidationException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Creates a validation exception with an error code, message, and details.
     *
     * @param errorCode the error code (e.g., "INVALID_QUERY")
     * @param message   the human-readable error message
     * @param details   optional additional details about the validation failure
     */
    public ValidationException(String errorCode, String message, @Nullable Map<String, Object> details) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.details = details == null ? null : Map.copyOf(details);
    }

    /**
     * Returns the error code for this validation failure.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the optional details map for this validation failure.
     *
     * @return the details map, or null if no details were provided
     */
    public @Nullable Map<String, Object> getDetails() {
        return details;
    }
}
