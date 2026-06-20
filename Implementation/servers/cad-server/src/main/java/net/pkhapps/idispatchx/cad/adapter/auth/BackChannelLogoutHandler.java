package net.pkhapps.idispatchx.cad.adapter.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.http.BadRequestResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.common.auth.JwksKeyProvider;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Objects;

/**
 * Handles OIDC back-channel logout notifications from the identity provider.
 * <p>
 * Endpoint: {@code POST /auth/back-channel-logout}
 * <p>
 * This endpoint is unauthenticated and accepts {@code application/x-www-form-urlencoded}
 * requests (per the OIDC back-channel logout specification). It must not be exposed through
 * the public reverse proxy; the OIDC provider must be able to reach it directly on the CAD
 * Server's internal network address.
 * <p>
 * On receipt of a valid logout token:
 * <ol>
 *   <li>Revokes the OIDC session in {@link SessionStore} to block future REST requests with
 *       JWT tokens that carry the same {@code sid} claim.</li>
 *   <li>Closes all active WebSocket connections belonging to the session via
 *       {@link SessionRegistry}, satisfying the immediate-revocation Security NFR.</li>
 *   <li>Logs a {@code forced session termination} audit event per the Security NFR.</li>
 * </ol>
 */
public final class BackChannelLogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutHandler.class);

    private final JwksKeyProvider keyProvider;
    private final String expectedIssuer;
    private final SessionStore sessionStore;
    private final SessionRegistry sessionRegistry;

    public BackChannelLogoutHandler(
            JwksKeyProvider keyProvider,
            String expectedIssuer,
            SessionStore sessionStore,
            SessionRegistry sessionRegistry) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
    }

    /**
     * Registers the back-channel logout route on the Javalin instance.
     *
     * @param router      the Javalin routing API
     * @param contextPath configurable URL prefix (empty or starts with {@code /})
     */
    public void registerRoutes(JavalinDefaultRoutingApi router, String contextPath) {
        router.post(contextPath + "/auth/back-channel-logout", ctx -> {
            var logoutToken = ctx.formParam("logout_token");
            if (logoutToken == null || logoutToken.isBlank()) {
                throw new BadRequestResponse("Missing logout_token parameter");
            }

            String sessionId;
            try {
                sessionId = parseAndValidateLogoutToken(logoutToken);
            } catch (BadRequestResponse e) {
                throw e;
            } catch (Exception e) {
                log.warn("Back-channel logout: rejected invalid logout_token: {}", e.getMessage());
                throw new BadRequestResponse("Invalid logout_token");
            }

            sessionStore.revokeSession(sessionId);
            sessionRegistry.revokeOidcSession(sessionId);
            log.info("Security audit: forced session termination for OIDC session {}", sessionId);

            ctx.status(200);
        });
    }

    private String parseAndValidateLogoutToken(String logoutToken) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(logoutToken);
        } catch (ParseException e) {
            throw new BadRequestResponse("logout_token is not a valid JWT");
        }

        var keyId = jwt.getHeader().getKeyID();
        if (keyId == null) {
            throw new BadRequestResponse("logout_token missing kid header");
        }

        var jwk = keyProvider.getKey(keyId);
        if (jwk == null) {
            throw new BadRequestResponse("Signing key not found for kid: " + keyId);
        }

        try {
            if (!jwt.verify(createVerifier(jwk))) {
                throw new BadRequestResponse("logout_token signature verification failed");
            }
        } catch (JOSEException e) {
            throw new BadRequestResponse("Signature verification error: " + e.getMessage());
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new BadRequestResponse("Failed to parse logout_token claims");
        }

        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new BadRequestResponse("logout_token issuer does not match expected issuer");
        }

        String sessionId;
        try {
            sessionId = claims.getStringClaim("sid");
        } catch (ParseException e) {
            throw new BadRequestResponse("Failed to parse sid claim from logout_token");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadRequestResponse("logout_token missing required sid claim");
        }

        return sessionId;
    }

    private static JWSVerifier createVerifier(JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        } else if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey.toECPublicKey());
        } else {
            throw new BadRequestResponse("Unsupported signing key type: " + jwk.getKeyType());
        }
    }
}
