package net.pkhapps.idispatchx.gis.server.api.error;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.api.CommonErrorCode;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.gis.server.service.geocode.DatabaseUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized exception handler for all GIS Server API endpoints.
 * <p>
 * Converts exceptions to standardized {@link ErrorResponse} JSON responses.
 * All error responses follow the format defined in the technical design.
 * <p>
 * Exception to status code mapping:
 * <ul>
 *   <li>{@link ValidationException} → 400 Bad Request</li>
 *   <li>{@link BadRequestResponse} → 400 Bad Request</li>
 *   <li>{@link UnauthorizedResponse} → 401 Unauthorized</li>
 *   <li>{@link ForbiddenResponse} → 403 Forbidden</li>
 *   <li>{@link NotFoundResponse} → 404 Not Found</li>
 *   <li>{@link DatabaseUnavailableException} → 503 Service Unavailable</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 */
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {
        // Utility class
    }

    /**
     * Registers all exception handlers on the given Javalin instance.
     *
     * @param app the Javalin application
     */
    public static void register(Javalin app) {
        app.exception(ValidationException.class, GlobalExceptionHandler::handleValidationException);
        app.exception(BadRequestResponse.class, GlobalExceptionHandler::handleBadRequest);
        app.exception(UnauthorizedResponse.class, GlobalExceptionHandler::handleUnauthorized);
        app.exception(ForbiddenResponse.class, GlobalExceptionHandler::handleForbidden);
        app.exception(NotFoundResponse.class, GlobalExceptionHandler::handleNotFound);
        app.exception(DatabaseUnavailableException.class, GlobalExceptionHandler::handleDatabaseUnavailable);
        app.exception(HttpResponseException.class, GlobalExceptionHandler::handleHttpResponseException);
        app.exception(Exception.class, GlobalExceptionHandler::handleUnexpected);
    }

    private static void handleValidationException(ValidationException e, Context ctx) {
        log.debug("Validation error on {}: {} - {}", ctx.path(), e.getErrorCode(), e.getMessage());
        ctx.status(400);
        if (e.getDetails() != null) {
            ctx.json(ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getDetails(), ctx.path()));
        } else {
            ctx.json(ErrorResponse.of(e.getErrorCode(), e.getMessage(), ctx.path()));
        }
    }

    private static void handleBadRequest(BadRequestResponse e, Context ctx) {
        log.debug("Bad request on {}: {}", ctx.path(), e.getMessage());
        ctx.status(400);
        ctx.json(ErrorResponse.of(GisErrorCode.INVALID_PARAMETER, e.getMessage(), ctx.path()));
    }

    private static void handleUnauthorized(UnauthorizedResponse e, Context ctx) {
        log.debug("Unauthorized request on {}: {}", ctx.path(), e.getMessage());
        ctx.status(401);
        ctx.json(ErrorResponse.of(CommonErrorCode.UNAUTHORIZED, e.getMessage(), ctx.path()));
    }

    private static void handleForbidden(ForbiddenResponse e, Context ctx) {
        log.debug("Forbidden request on {}: {}", ctx.path(), e.getMessage());
        ctx.status(403);
        ctx.json(ErrorResponse.of(CommonErrorCode.FORBIDDEN, e.getMessage(), ctx.path()));
    }

    private static void handleNotFound(NotFoundResponse e, Context ctx) {
        log.debug("Not found on {}: {}", ctx.path(), e.getMessage());
        ctx.status(404);
        ctx.json(ErrorResponse.of(GisErrorCode.LAYER_NOT_FOUND, e.getMessage(), ctx.path()));
    }

    private static void handleDatabaseUnavailable(DatabaseUnavailableException e, Context ctx) {
        log.warn("Database unavailable on {}: {}", ctx.path(), e.getMessage());
        ctx.status(503);
        ctx.json(ErrorResponse.of(CommonErrorCode.DATABASE_ERROR, "Database service unavailable", ctx.path()));
    }

    private static void handleHttpResponseException(HttpResponseException e, Context ctx) {
        log.debug("HTTP error {} on {}: {}", e.getStatus(), ctx.path(), e.getMessage());
        ctx.status(e.getStatus());
        ctx.json(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, e.getMessage(), ctx.path()));
    }

    private static void handleUnexpected(Exception e, Context ctx) {
        log.error("Unexpected error on {}: {}", ctx.path(), e.getMessage(), e);
        ctx.status(500);
        ctx.json(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, "An unexpected error occurred", ctx.path()));
    }
}
