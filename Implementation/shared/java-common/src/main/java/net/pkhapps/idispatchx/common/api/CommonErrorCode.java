package net.pkhapps.idispatchx.common.api;

/**
 * Error codes shared across all server applications.
 * <p>
 * These codes are used in {@link ErrorResponse} to identify error categories
 * that are common to all servers (authentication, authorization, database, etc.).
 * Server-specific error codes should be defined in the respective server module.
 */
public final class CommonErrorCode {

    private CommonErrorCode() {
        // Utility class
    }

    /**
     * Missing or invalid JWT token.
     */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    /**
     * Valid JWT but insufficient role for the requested resource.
     */
    public static final String FORBIDDEN = "FORBIDDEN";

    /**
     * Database connection or query failure.
     */
    public static final String DATABASE_ERROR = "DATABASE_ERROR";

    /**
     * Unexpected server error.
     */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
