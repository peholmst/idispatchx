package net.pkhapps.idispatchx.gis.server.auth;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.auth.JwksException;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidationException;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Javalin handler that validates JWT tokens from the Authorization header.
 * <p>
 * This handler extracts Bearer tokens from the Authorization header,
 * validates them using the provided {@link TokenValidator}, and stores
 * the validated claims in the request context via {@link AuthContext}.
 * <p>
 * If validation fails, the handler responds with HTTP 401 Unauthorized.
 * <p>
 * Usage:
 * <pre>
 * app.before("/api/*", new JwtAuthHandler(tokenValidator, sessionStore));
 * </pre>
 */
public final class JwtAuthHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthHandler.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final TokenValidator tokenValidator;
    private final @Nullable SessionStore sessionStore;

    /**
     * Creates a new JWT authentication handler without session revocation checking.
     *
     * @param tokenValidator the token validator for validating JWTs
     */
    public JwtAuthHandler(TokenValidator tokenValidator) {
        this(tokenValidator, null);
    }

    /**
     * Creates a new JWT authentication handler with session revocation checking.
     *
     * @param tokenValidator the token validator for validating JWTs
     * @param sessionStore   the session store for checking revoked sessions (may be null)
     */
    public JwtAuthHandler(TokenValidator tokenValidator, @Nullable SessionStore sessionStore) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
        this.sessionStore = sessionStore;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        var authHeader = ctx.header(AUTHORIZATION_HEADER);

        if (authHeader == null || authHeader.isBlank()) {
            log.debug("Missing Authorization header for {}", ctx.path());
            throw new UnauthorizedResponse("Missing Authorization header");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Invalid Authorization header format for {}", ctx.path());
            throw new UnauthorizedResponse("Invalid Authorization header format");
        }

        var token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            log.debug("Empty token in Authorization header for {}", ctx.path());
            throw new UnauthorizedResponse("Missing token");
        }

        try {
            var claims = tokenValidator.validate(token);

            // Check if session has been revoked via back-channel logout
            if (sessionStore != null && claims.sessionId() != null) {
                if (sessionStore.isRevoked(claims.sessionId())) {
                    log.debug("Session {} has been revoked for subject: {}",
                            claims.sessionId(), claims.subject());
                    throw new UnauthorizedResponse("Session has been revoked");
                }
            }

            AuthContext.setClaims(ctx, claims);
            log.debug("Authenticated request for subject: {}", claims.subject());
        } catch (TokenValidationException e) {
            log.debug("Token validation failed for {}: {} - {}",
                    ctx.path(), e.getErrorCode(), e.getMessage());
            handleValidationError(e);
        } catch (JwksException e) {
            log.warn("JWKS error during token validation: {}", e.getMessage());
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.result("Authentication service unavailable");
        }
    }

    private void handleValidationError(TokenValidationException e) {
        var message = switch (e.getErrorCode()) {
            case TOKEN_EXPIRED -> "Token expired";
            case INVALID_SIGNATURE -> "Invalid token signature";
            case INVALID_ISSUER -> "Invalid token issuer";
            case KEY_NOT_FOUND -> "Token signing key not found";
            case MISSING_CLAIM, INVALID_TOKEN -> "Invalid token";
        };
        throw new UnauthorizedResponse(message);
    }
}
