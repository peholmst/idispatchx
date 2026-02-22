package net.pkhapps.idispatchx.common.auth;

/**
 * Exception thrown when JWT token validation fails.
 */
public class TokenValidationException extends RuntimeException {

    /**
     * Error codes for token validation failures.
     */
    public enum ErrorCode {
        /**
         * Token is malformed or cannot be parsed.
         */
        INVALID_TOKEN,

        /**
         * Token signature verification failed.
         */
        INVALID_SIGNATURE,

        /**
         * Token has expired.
         */
        TOKEN_EXPIRED,

        /**
         * Token issuer does not match expected issuer.
         */
        INVALID_ISSUER,

        /**
         * Required claim is missing from the token.
         */
        MISSING_CLAIM,

        /**
         * Key for signature verification not found.
         */
        KEY_NOT_FOUND
    }

    private final ErrorCode errorCode;

    /**
     * Creates a new token validation exception.
     *
     * @param errorCode the error code
     * @param message   the error message
     */
    public TokenValidationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new token validation exception with a cause.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param cause     the underlying cause
     */
    public TokenValidationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
