package net.pkhapps.idispatchx.cad.adapter.primary.rest.shared;

import io.javalin.http.ForbiddenResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.http.HttpResponseException;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.api.CommonErrorCode;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.api.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

/**
 * Centralized exception handler for all CAD Server API endpoints.
 * <p>
 * Maps domain and framework exceptions to standardized {@link ErrorResponse} JSON responses.
 *
 * <ul>
 *   <li>{@link ValidationException} → 400 with the exception's own error code</li>
 *   <li>{@link IllegalArgumentException} → 400 {@code VALIDATION_ERROR}</li>
 *   <li>{@link NoSuchElementException} → 404 {@code RESOURCE_NOT_FOUND}</li>
 *   <li>{@link IllegalStateException} → 409 {@code INVARIANT_VIOLATION}</li>
 *   <li>{@link UnauthorizedResponse} → 401 {@code UNAUTHORIZED}</li>
 *   <li>{@link ForbiddenResponse} → 403 {@code FORBIDDEN}</li>
 *   <li>{@link Exception} (catch-all) → 500 {@code INTERNAL_ERROR}</li>
 * </ul>
 */
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {}

    /**
     * Registers all exception handlers on the given Javalin instance.
     *
     * @param router the Javalin routing API
     */
    public static void register(JavalinDefaultRoutingApi router) {
        router.exception(ValidationException.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(e.getDetails() != null
                    ? ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getDetails(), ctx.path())
                    : ErrorResponse.of(e.getErrorCode(), e.getMessage(), ctx.path()));
        });

        router.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(ErrorResponse.of(CadErrorCode.VALIDATION_ERROR,
                    e.getMessage() != null ? e.getMessage() : "Validation error", ctx.path()));
        });

        router.exception(NoSuchElementException.class, (e, ctx) -> {
            ctx.status(404);
            ctx.json(ErrorResponse.of(CadErrorCode.RESOURCE_NOT_FOUND,
                    e.getMessage() != null ? e.getMessage() : "Resource not found", ctx.path()));
        });

        router.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(409);
            ctx.json(ErrorResponse.of(CadErrorCode.INVARIANT_VIOLATION,
                    e.getMessage() != null ? e.getMessage() : "Invariant violation", ctx.path()));
        });

        router.exception(UnauthorizedResponse.class, (e, ctx) -> {
            ctx.status(401);
            ctx.json(ErrorResponse.of(CommonErrorCode.UNAUTHORIZED, e.getMessage(), ctx.path()));
        });

        router.exception(ForbiddenResponse.class, (e, ctx) -> {
            ctx.status(403);
            ctx.json(ErrorResponse.of(CommonErrorCode.FORBIDDEN, e.getMessage(), ctx.path()));
        });

        router.exception(HttpResponseException.class, (e, ctx) -> {
            ctx.status(e.getStatus());
            ctx.json(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, e.getMessage(), ctx.path()));
        });

        router.exception(Exception.class, (e, ctx) -> {
            log.error("Unexpected error on {}: {}", ctx.path(), e.getMessage(), e);
            ctx.status(500);
            ctx.json(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, "An unexpected error occurred", ctx.path()));
        });
    }
}
