package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.jwk.JWK;
import org.jspecify.annotations.Nullable;

/**
 * Provider for JSON Web Keys (JWK) used for JWT signature verification.
 * <p>
 * Implementations fetch and cache keys from a JWKS endpoint or other source.
 */
public interface JwksKeyProvider {

    /**
     * Gets a JWK by its key ID.
     *
     * @param keyId the key ID (kid)
     * @return the JWK, or null if not found
     * @throws JwksException if fetching the key fails
     */
    @Nullable JWK getKey(String keyId);
}
