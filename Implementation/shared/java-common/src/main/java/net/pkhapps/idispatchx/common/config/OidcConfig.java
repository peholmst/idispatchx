package net.pkhapps.idispatchx.common.config;

import java.net.URI;
import java.util.Objects;

/**
 * OIDC (OpenID Connect) provider configuration.
 * <p>
 * The JWKS URL defaults to the well-known endpoint derived from the issuer URL if not explicitly set.
 *
 * @param issuer  the OIDC provider issuer URL
 * @param jwksUrl the JWKS (JSON Web Key Set) endpoint URL
 */
public record OidcConfig(
        URI issuer,
        URI jwksUrl
) {

    /**
     * Creates an OIDC configuration with validation.
     *
     * @param issuer  the OIDC provider issuer URL
     * @param jwksUrl the JWKS endpoint URL
     */
    public OidcConfig {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(jwksUrl, "jwksUrl must not be null");
    }

    /**
     * Creates a builder for loading OIDC configuration from environment variables.
     *
     * @param issuerEnvVar  environment variable for issuer URL
     * @param jwksUrlEnvVar environment variable for JWKS URL (optional, defaults to well-known)
     * @return the builder
     */
    public static Builder builder(String issuerEnvVar, String jwksUrlEnvVar) {
        return new Builder(issuerEnvVar, jwksUrlEnvVar);
    }

    /**
     * Builder for creating OidcConfig from environment variables.
     */
    public static final class Builder {

        private final ConfigProperty<String> issuerProperty;
        private final String jwksUrlEnvVar;

        private Builder(String issuerEnvVar, String jwksUrlEnvVar) {
            this.issuerProperty = ConfigProperty.requiredString(issuerEnvVar);
            this.jwksUrlEnvVar = jwksUrlEnvVar;
        }

        /**
         * Loads the OIDC configuration using the provided config loader.
         *
         * @param loader the configuration loader
         * @return the OIDC configuration
         * @throws ConfigurationException if required properties are missing
         */
        public OidcConfig load(ConfigLoader loader) {
            var issuerStr = loader.get(issuerProperty);
            var issuer = parseUri(issuerStr, "issuer");

            // Try to get explicit JWKS URL, or derive from issuer
            var jwksUrlProperty = ConfigProperty.optionalString(jwksUrlEnvVar, "");
            var jwksUrlStr = loader.get(jwksUrlProperty);
            URI jwksUrl;
            if (jwksUrlStr.isBlank()) {
                jwksUrl = deriveJwksUrl(issuer);
            } else {
                jwksUrl = parseUri(jwksUrlStr, "jwksUrl");
            }

            return new OidcConfig(issuer, jwksUrl);
        }

        private URI parseUri(String value, String name) {
            try {
                return URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid URI for " + name + ": " + value, e);
            }
        }

        private URI deriveJwksUrl(URI issuer) {
            // Standard OIDC well-known endpoint
            var issuerStr = issuer.toString();
            if (!issuerStr.endsWith("/")) {
                issuerStr = issuerStr + "/";
            }
            return URI.create(issuerStr + ".well-known/jwks.json");
        }
    }
}
