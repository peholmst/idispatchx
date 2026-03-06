package net.pkhapps.idispatchx.common.config;

import java.net.URI;
import java.util.Objects;

/**
 * OIDC (OpenID Connect) provider configuration.
 * <p>
 * The JWKS URL defaults to the well-known endpoint derived from the issuer URL if not explicitly set.
 *
 * @param issuer   the OIDC provider issuer URL
 * @param jwksUrl  the JWKS (JSON Web Key Set) endpoint URL
 * @param clientId the OIDC client ID (used for back-channel logout token audience validation)
 */
public record OidcConfig(
        URI issuer,
        URI jwksUrl,
        String clientId
) {

    /**
     * Creates an OIDC configuration with validation.
     *
     * @param issuer   the OIDC provider issuer URL
     * @param jwksUrl  the JWKS endpoint URL
     * @param clientId the OIDC client ID
     */
    public OidcConfig {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(jwksUrl, "jwksUrl must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
    }

    /**
     * Creates a builder for loading OIDC configuration from environment variables.
     *
     * @param issuerEnvVar    environment variable for issuer URL
     * @param jwksUrlEnvVar   environment variable for JWKS URL (optional, defaults to well-known)
     * @param clientIdEnvVar  environment variable for the OIDC client ID (required)
     * @return the builder
     */
    public static Builder builder(String issuerEnvVar, String jwksUrlEnvVar, String clientIdEnvVar) {
        return new Builder(issuerEnvVar, jwksUrlEnvVar, clientIdEnvVar);
    }

    /**
     * Builder for creating OidcConfig from environment variables.
     */
    public static final class Builder {

        private final ConfigProperty<String> issuerProperty;
        private final String jwksUrlEnvVar;
        private final ConfigProperty<String> clientIdProperty;

        private Builder(String issuerEnvVar, String jwksUrlEnvVar, String clientIdEnvVar) {
            this.issuerProperty = ConfigProperty.requiredString(issuerEnvVar);
            this.jwksUrlEnvVar = jwksUrlEnvVar;
            this.clientIdProperty = ConfigProperty.requiredString(clientIdEnvVar);
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

            var clientId = loader.get(clientIdProperty);

            return new OidcConfig(issuer, jwksUrl, clientId);
        }

        private URI parseUri(String value, String name) {
            try {
                return URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid URI for " + name + ": " + value, e);
            }
        }

        private URI deriveJwksUrl(URI issuer) {
            // Derive JWKS URL from OIDC discovery endpoint
            // Standard OIDC providers publish their JWKS location in the discovery document
            // at {issuer}/.well-known/openid-configuration
            // Common JWKS paths vary by provider:
            // - Standard: {issuer}/.well-known/jwks.json
            // - Keycloak: {issuer}/protocol/openid-connect/certs
            // - Auth0: {issuer}/.well-known/jwks.json
            //
            // For simplicity, we use the standard path here. If a provider uses a different
            // path, the GIS_OIDC_JWKS_URL environment variable should be set explicitly.
            // A future enhancement could fetch the discovery document and extract jwks_uri.
            var issuerStr = issuer.toString();
            if (!issuerStr.endsWith("/")) {
                issuerStr = issuerStr + "/";
            }
            return URI.create(issuerStr + ".well-known/jwks.json");
        }
    }
}
