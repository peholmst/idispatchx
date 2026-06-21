package net.pkhapps.idispatchx.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Standardized error response DTO used by all server API endpoints.
 * <p>
 * Produces JSON in the following format:
 * <pre>
 * {
 *   "error": {
 *     "code": "INVALID_QUERY",
 *     "message": "Search query must be at least 3 characters",
 *     "details": { "minLength": 3, "actualLength": 2 }
 *   },
 *   "timestamp": "2026-02-21T10:30:00Z",
 *   "path": "/api/v1/geocode/search"
 * }
 * </pre>
 *
 * @param error     the error details
 * @param timestamp the time the error occurred
 * @param path      the request path that produced the error
 */
public record ErrorResponse(
        ErrorBody error,
        Instant timestamp,
        String path
) {

    public ErrorResponse {
        Objects.requireNonNull(error, "error must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * The error detail body containing the error code, message, and optional details.
     *
     * @param code    the error code identifying the error type
     * @param message a human-readable error message
     * @param details optional additional details about the error (e.g., validation info)
     */
    public record ErrorBody(
            String code,
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Nullable Map<String, Object> details
    ) {

        public ErrorBody {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(message, "message must not be null");
            details = details == null ? null : Map.copyOf(details);
        }
    }

    /**
     * Creates an error response without details.
     *
     * @param code    the error code
     * @param message the error message
     * @param path    the request path
     * @return the error response
     */
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(new ErrorBody(code, message, null), Instant.now(), path);
    }

    /**
     * Creates an error response with details.
     *
     * @param code    the error code
     * @param message the error message
     * @param details additional details about the error
     * @param path    the request path
     * @return the error response
     */
    public static ErrorResponse of(String code, String message, Map<String, Object> details, String path) {
        return new ErrorResponse(new ErrorBody(code, message, details), Instant.now(), path);
    }
}
