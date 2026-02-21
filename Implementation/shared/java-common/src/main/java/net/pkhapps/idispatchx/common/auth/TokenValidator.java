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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates JWT access tokens against a JWKS.
 * <p>
 * The validator verifies:
 * <ul>
 *   <li>Token signature using the JWKS</li>
 *   <li>Token expiration (exp claim)</li>
 *   <li>Token issuer matches expected issuer</li>
 *   <li>Required claims are present</li>
 * </ul>
 * <p>
 * This class is thread-safe.
 */
public final class TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);

    /**
     * Default claim name for roles.
     */
    public static final String DEFAULT_ROLES_CLAIM = "roles";

    private final JwksKeyProvider keyProvider;
    private final String expectedIssuer;
    private final String rolesClaim;

    /**
     * Creates a new token validator.
     *
     * @param keyProvider    the key provider for fetching signing keys
     * @param expectedIssuer the expected token issuer (iss claim)
     */
    public TokenValidator(JwksKeyProvider keyProvider, String expectedIssuer) {
        this(keyProvider, expectedIssuer, DEFAULT_ROLES_CLAIM);
    }

    /**
     * Creates a new token validator with a custom roles claim name.
     *
     * @param keyProvider    the key provider for fetching signing keys
     * @param expectedIssuer the expected token issuer (iss claim)
     * @param rolesClaim     the claim name containing user roles
     */
    public TokenValidator(JwksKeyProvider keyProvider, String expectedIssuer, String rolesClaim) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        this.rolesClaim = Objects.requireNonNull(rolesClaim, "rolesClaim must not be null");

        if (expectedIssuer.isBlank()) {
            throw new IllegalArgumentException("expectedIssuer must not be blank");
        }
    }

    /**
     * Validates a JWT token and extracts its claims.
     *
     * @param token the JWT token string
     * @return the validated token claims
     * @throws TokenValidationException if validation fails
     */
    public TokenClaims validate(String token) {
        Objects.requireNonNull(token, "token must not be null");

        // Parse the token
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Failed to parse JWT: " + e.getMessage(), e);
        }

        // Get the key ID from the token header
        var keyId = signedJWT.getHeader().getKeyID();
        if (keyId == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_TOKEN,
                    "Token does not contain a key ID (kid)");
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
                        "Token signature verification failed");
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

    private TokenClaims validateClaims(JWTClaimsSet claims) {
        // Validate issuer
        var issuer = claims.getIssuer();
        if (issuer == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Token does not contain issuer (iss) claim");
        }
        if (!expectedIssuer.equals(issuer)) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.INVALID_ISSUER,
                    "Token issuer '" + issuer + "' does not match expected '" + expectedIssuer + "'");
        }

        // Validate expiration
        var expirationTime = claims.getExpirationTime();
        if (expirationTime == null) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Token does not contain expiration (exp) claim");
        }
        if (expirationTime.before(new Date())) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.TOKEN_EXPIRED,
                    "Token has expired");
        }

        // Validate subject
        var subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.MISSING_CLAIM,
                    "Token does not contain subject (sub) claim");
        }

        // Extract roles
        var roles = extractRoles(claims);

        // Extract optional claims
        var sessionId = getStringClaim(claims, "sid");
        var issuedAt = claims.getIssueTime() != null
                ? claims.getIssueTime().toInstant()
                : null;

        return new TokenClaims(
                subject,
                issuer,
                sessionId,
                roles,
                expirationTime.toInstant(),
                issuedAt
        );
    }

    private Set<Role> extractRoles(JWTClaimsSet claims) {
        Set<Role> roles = new HashSet<>();

        try {
            var rolesClaim = claims.getClaim(this.rolesClaim);
            if (rolesClaim instanceof List<?> roleList) {
                for (Object item : roleList) {
                    if (item instanceof String roleStr) {
                        Role.fromClaimValue(roleStr).ifPresent(roles::add);
                    }
                }
            } else if (rolesClaim instanceof String roleStr) {
                // Some providers might send a single role as a string
                Role.fromClaimValue(roleStr).ifPresent(roles::add);
            }
        } catch (Exception e) {
            log.debug("Failed to extract roles from claim '{}': {}", this.rolesClaim, e.getMessage());
        }

        return roles;
    }

    private @Nullable String getStringClaim(JWTClaimsSet claims, String claimName) {
        try {
            return claims.getStringClaim(claimName);
        } catch (ParseException e) {
            return null;
        }
    }
}
