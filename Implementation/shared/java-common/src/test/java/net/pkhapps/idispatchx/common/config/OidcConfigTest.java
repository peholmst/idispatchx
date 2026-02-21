package net.pkhapps.idispatchx.common.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class OidcConfigTest {

    @Test
    void validConfig_createsSuccessfully() {
        var config = new OidcConfig(
                URI.create("https://auth.example.com"),
                URI.create("https://auth.example.com/.well-known/jwks.json")
        );

        assertEquals(URI.create("https://auth.example.com"), config.issuer());
        assertEquals(URI.create("https://auth.example.com/.well-known/jwks.json"), config.jwksUrl());
    }

    @Test
    void nullIssuer_throws() {
        assertThrows(NullPointerException.class,
                () -> new OidcConfig(null, URI.create("https://example.com/jwks")));
    }

    @Test
    void nullJwksUrl_throws() {
        assertThrows(NullPointerException.class,
                () -> new OidcConfig(URI.create("https://example.com"), null));
    }

    @Test
    void builder_loadsFromEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("OIDC_ISSUER", "https://auth.example.com");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = OidcConfig.builder("OIDC_ISSUER", "OIDC_JWKS").load(loader);

        assertEquals(URI.create("https://auth.example.com"), config.issuer());
        // JWKS URL should be derived from issuer
        assertEquals(URI.create("https://auth.example.com/.well-known/jwks.json"), config.jwksUrl());
    }

    @Test
    void builder_usesExplicitJwksUrl() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("OIDC_ISSUER", "https://auth.example.com");
        envVars.put("OIDC_JWKS", "https://auth.example.com/custom/jwks");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = OidcConfig.builder("OIDC_ISSUER", "OIDC_JWKS").load(loader);

        assertEquals(URI.create("https://auth.example.com/custom/jwks"), config.jwksUrl());
    }

    @Test
    void builder_handlesIssuerWithTrailingSlash() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("OIDC_ISSUER", "https://auth.example.com/");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = OidcConfig.builder("OIDC_ISSUER", "OIDC_JWKS").load(loader);

        assertEquals(URI.create("https://auth.example.com/.well-known/jwks.json"), config.jwksUrl());
    }

    @Test
    void builder_throwsOnInvalidIssuerUri() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("OIDC_ISSUER", "not a valid uri");

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class,
                () -> OidcConfig.builder("OIDC_ISSUER", "OIDC_JWKS").load(loader));
    }

    @Test
    void builder_throwsOnMissingIssuer() {
        Map<String, String> envVars = new HashMap<>();

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class,
                () -> OidcConfig.builder("OIDC_ISSUER", "OIDC_JWKS").load(loader));
    }
}
