package net.pkhapps.idispatchx.gis.server.auth;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import net.pkhapps.idispatchx.common.auth.JwksException;
import net.pkhapps.idispatchx.common.auth.LogoutTokenValidator;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Javalin handler for OIDC Back-Channel Logout notifications.
 * <p>
 * This handler receives logout tokens from the OIDC provider when a user
 * logs out. It validates the token and records the session revocation
 * in the session store.
 * <p>
 * Per the OIDC specification, this endpoint:
 * <ul>
 *   <li>Accepts POST requests with application/x-www-form-urlencoded body</li>
 *   <li>Expects a 'logout_token' form parameter</li>
 *   <li>Returns 200 OK on success</li>
 *   <li>Returns 400 Bad Request on validation failure</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * app.post("/logout/backchannel", new BackChannelLogoutHandler(logoutValidator, sessionStore));
 * </pre>
 *
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">
 *     OpenID Connect Back-Channel Logout 1.0</a>
 */
public final class BackChannelLogoutHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutHandler.class);

    private static final String LOGOUT_TOKEN_PARAM = "logout_token";

    private final LogoutTokenValidator logoutTokenValidator;
    private final SessionStore sessionStore;

    /**
     * Creates a new back-channel logout handler.
     *
     * @param logoutTokenValidator the validator for logout tokens
     * @param sessionStore         the session store for recording revocations
     */
    public BackChannelLogoutHandler(LogoutTokenValidator logoutTokenValidator,
                                    SessionStore sessionStore) {
        this.logoutTokenValidator = Objects.requireNonNull(logoutTokenValidator,
                "logoutTokenValidator must not be null");
        this.sessionStore = Objects.requireNonNull(sessionStore,
                "sessionStore must not be null");
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        var logoutToken = ctx.formParam(LOGOUT_TOKEN_PARAM);

        if (logoutToken == null || logoutToken.isBlank()) {
            log.debug("Back-channel logout request missing logout_token");
            throw new BadRequestResponse("Missing logout_token parameter");
        }

        try {
            var claims = logoutTokenValidator.validate(logoutToken);

            if (claims.hasSessionId()) {
                // Revoke specific session
                sessionStore.revokeSession(claims.sessionId());
                log.info("Revoked session: {} for subject: {}",
                        claims.sessionId(), claims.subject());
            } else if (claims.isSubjectLogout()) {
                // Subject-only logout - this implementation only supports session-based logout
                // For full subject logout, you would need to track all sessions per subject
                log.warn("Received subject-only logout for {} - session tracking required",
                        claims.subject());
            }

            ctx.status(HttpStatus.OK);
            ctx.result("");

        } catch (TokenValidationException e) {
            log.debug("Back-channel logout token validation failed: {} - {}",
                    e.getErrorCode(), e.getMessage());
            throw new BadRequestResponse("Invalid logout token: " + e.getMessage());

        } catch (JwksException e) {
            log.warn("JWKS error during logout token validation: {}", e.getMessage());
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.result("Service unavailable");
        }
    }
}
