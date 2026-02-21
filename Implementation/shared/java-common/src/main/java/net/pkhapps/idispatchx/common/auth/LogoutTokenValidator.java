package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Validates OIDC Back-Channel Logout tokens.
 * <p>
 * Logout tokens are JWTs sent by the OIDC provider when a user logs out.
 * They contain a 'sid' (session ID) and/or 'sub' (subject) claim to identify
 * which sessions should be terminated.
 * <p>
 * Per the OIDC Back-Channel Logout specification, a valid logout token must:
 * <ul>
 *   <li>Be signed by the OIDC provider's key</li>
 *   <li>Contain the expected issuer</li>
 *   <li>Contain the expected audience (client ID)</li>
 *   <li>Not be expired (within reasonable clock skew)</li>
 *   <li>Contain an 'events' claim with the logout event type</li>
 *   <li>Contain either 'sid' or 'sub' claim (or both)</li>
 *   <li>NOT contain a 'nonce' claim</li>
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">
 *     OpenID Connect Back-Channel Logout 1.0</a>
 */
public final class LogoutTokenValidator {

    /**
     * The OIDC back-channel logout event type.
     */
    public static final String LOGOUT_EVENT_TYPE =
            "http://schemas.openid.net/event/backchannel-logout";

    private final JwksKeyProvider keyProvider;
    private final String expectedIssuer;
    private final String expectedAudience;

    /**
     * Creates a new logout token validator.
     *
     * @param keyProvider      the key provider for fetching signing keys
     * @param expectedIssuer   the expected token issuer (must match access tokens)
     * @param expectedAudience the expected audience (client ID)
     */
    public LogoutTokenValidator(JwksKeyProvider keyProvider, String expectedIssuer,
                                String expectedAudience) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        this.expectedAudience = Objects.requireNonNull(expectedAudience, "expectedAudience must not be null");

        if (expectedIssuer.isBlank()) {
            throw new IllegalArgumentException("expectedIssuer must not be blank");
        }
        if (expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience must not be blank");
        }
    }

    /**
     * Validates a logout token and extracts session information.
     *
     * @param token the logout token string
     * @return the validated logout claims
     * @throws TokenValidationException if validation fails
     */
    public LogoutClaims validate(String token) {
        Objects.requireNonNull(token, "token must not be null");

        // Parse the token
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Failed to parse logout token: " + e.getMessage(), e);
        }

        // Get the key ID from the token header
        var keyId = signedJWT.getHeader().getKeyID();
        if (keyId == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Logout token does not contain a key ID (kid)");
        }

        // Fetch the signing key
        var jwk = keyProvider.getKey(keyId);
        if (jwk == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.KEY_NOT_FOUND,
                    "Signing key not found: " + keyId);
        }

        // Verify the signature
        verifySignature(signedJWT, jwk);

        // Extract and validate claims
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Failed to extract claims: " + e.getMessage(), e);
        }

        return validateClaims(claims);
    }

    private void verifySignature(SignedJWT signedJWT, JWK jwk) {
        try {
            JWSVerifier verifier = createVerifier(jwk);
            if (!signedJWT.verify(verifier)) {
                throw new TokenValidationException(
                        TokenValidationException.ErrorCode.INVALID_SIGNATURE,
                        "Logout token signature verification failed");
            }
        } catch (JOSEException e) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_SIGNATURE,
                    "Signature verification error: " + e.getMessage(), e);
        }
    }

    private JWSVerifier createVerifier(JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        } else if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey.toECPublicKey());
        } else {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Unsupported key type: " + jwk.getKeyType());
        }
    }

    private LogoutClaims validateClaims(JWTClaimsSet claims) {
        // Validate issuer
        var issuer = claims.getIssuer();
        if (issuer == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Logout token does not contain issuer (iss) claim");
        }
        if (!expectedIssuer.equals(issuer)) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_ISSUER,
                    "Logout token issuer '" + issuer + "' does not match expected '" + expectedIssuer + "'");
        }

        // Validate audience
        var audience = claims.getAudience();
        if (audience == null || audience.isEmpty()) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Logout token does not contain audience (aud) claim");
        }
        if (!audience.contains(expectedAudience)) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Logout token audience does not include expected client");
        }

        // Validate expiration (with 60 second clock skew tolerance)
        var expirationTime = claims.getExpirationTime();
        if (expirationTime != null) {
            var now = new Date();
            var skewAllowance = 60_000L; // 60 seconds
            if (expirationTime.getTime() + skewAllowance < now.getTime()) {
                throw new TokenValidationException(
                        TokenValidationException.ErrorCode.TOKEN_EXPIRED,
                        "Logout token has expired");
            }
        }

        // Validate events claim
        validateEventsClaimPresent(claims);

        // Validate nonce is NOT present (per spec)
        try {
            if (claims.getStringClaim("nonce") != null) {
                throw new TokenValidationException(
                        TokenValidationException.ErrorCode.INVALID_TOKEN,
                        "Logout token must not contain nonce claim");
            }
        } catch (ParseException e) {
            // Ignore parse errors for nonce
        }

        // Extract session ID and subject
        var sessionId = getStringClaim(claims, "sid");
        var subject = claims.getSubject();

        // At least one of sid or sub must be present
        if (sessionId == null && subject == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Logout token must contain either sid or sub claim");
        }

        return new LogoutClaims(sessionId, subject);
    }

    private void validateEventsClaimPresent(JWTClaimsSet claims) {
        try {
            var events = claims.getClaim("events");
            if (events == null) {
                throw new TokenValidationException(
                        TokenValidationException.ErrorCode.MISSING_CLAIM,
                        "Logout token does not contain events claim");
            }

            // Events should be a JSON object with the logout event type as a key
            if (events instanceof Map<?, ?> eventsMap) {
                if (!eventsMap.containsKey(LOGOUT_EVENT_TYPE)) {
                    throw new TokenValidationException(
                            TokenValidationException.ErrorCode.INVALID_TOKEN,
                            "Logout token events claim does not contain logout event type");
                }
            } else {
                throw new TokenValidationException(
                        TokenValidationException.ErrorCode.INVALID_TOKEN,
                        "Logout token events claim has invalid format");
            }
        } catch (TokenValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Failed to parse events claim: " + e.getMessage(), e);
        }
    }

    private @Nullable String getStringClaim(JWTClaimsSet claims, String claimName) {
        try {
            return claims.getStringClaim(claimName);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Claims extracted from a validated logout token.
     *
     * @param sessionId the session ID to revoke, or null if not provided
     * @param subject   the subject (user ID) whose sessions to revoke, or null if not provided
     */
    public record LogoutClaims(
            @Nullable String sessionId,
            @Nullable String subject
    ) {
        /**
         * Returns whether this logout applies to a specific session.
         *
         * @return true if a session ID is present
         */
        public boolean hasSessionId() {
            return sessionId != null;
        }

        /**
         * Returns whether this logout applies to all sessions for a subject.
         *
         * @return true if only subject is present (no session ID)
         */
        public boolean isSubjectLogout() {
            return sessionId == null && subject != null;
        }
    }
}
