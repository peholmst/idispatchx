package net.pkhapps.idispatchx.gis.server.config;

import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class GisServerConfigTest {

    @Test
    void load_withAllRequiredEnvVars() {
        var envVars = createRequiredEnvVars();

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = GisServerConfig.load(loader);

        assertEquals(GisServerConfig.DEFAULT_PORT, config.port());
        assertEquals(Path.of("/var/tiles"), config.tileDirectory());
        assertEquals("jdbc:postgresql://localhost/gis", config.databaseConfig().url());
        assertEquals("gisuser", config.databaseConfig().username());
        assertEquals("gispass", config.databaseConfig().password());
        assertEquals(URI.create("https://auth.example.com"), config.oidcConfig().issuer());
        assertEquals("gis-server", config.oidcConfig().clientId());
    }

    @Test
    void load_withCustomPort() {
        var envVars = createRequiredEnvVars();
        envVars.put("GIS_SERVER_PORT", "9090");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = GisServerConfig.load(loader);

        assertEquals(9090, config.port());
    }

    @Test
    void load_throwsOnMissingTileDir() {
        var envVars = createRequiredEnvVars();
        envVars.remove("GIS_TILE_DIR");

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class, () -> GisServerConfig.load(loader));
    }

    @Test
    void load_throwsOnMissingDbUrl() {
        var envVars = createRequiredEnvVars();
        envVars.remove("GIS_DB_URL");

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class, () -> GisServerConfig.load(loader));
    }

    @Test
    void load_throwsOnMissingOidcIssuer() {
        var envVars = createRequiredEnvVars();
        envVars.remove("GIS_OIDC_ISSUER");

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class, () -> GisServerConfig.load(loader));
    }

    @Test
    void load_throwsOnMissingOidcClientId() {
        var envVars = createRequiredEnvVars();
        envVars.remove("GIS_OIDC_CLIENT_ID");

        var loader = new ConfigLoader(new Properties(), envVars::get);

        assertThrows(ConfigurationException.class, () -> GisServerConfig.load(loader));
    }

    @Test
    void invalidPort_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GisServerConfig(
                        0, // invalid port
                        Path.of("/var/tiles"),
                        new net.pkhapps.idispatchx.common.config.DatabaseConfig(
                                "jdbc:postgresql://localhost/gis", "user", "pass", 10),
                        new net.pkhapps.idispatchx.common.config.OidcConfig(
                                URI.create("https://auth.example.com"),
                                URI.create("https://auth.example.com/.well-known/jwks.json"),
                                "gis-server")
                ));
    }

    @Test
    void portTooHigh_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GisServerConfig(
                        65536, // port too high
                        Path.of("/var/tiles"),
                        new net.pkhapps.idispatchx.common.config.DatabaseConfig(
                                "jdbc:postgresql://localhost/gis", "user", "pass", 10),
                        new net.pkhapps.idispatchx.common.config.OidcConfig(
                                URI.create("https://auth.example.com"),
                                URI.create("https://auth.example.com/.well-known/jwks.json"),
                                "gis-server")
                ));
    }

    @Test
    void nullTileDirectory_throws() {
        assertThrows(NullPointerException.class,
                () -> new GisServerConfig(
                        8080,
                        null,
                        new net.pkhapps.idispatchx.common.config.DatabaseConfig(
                                "jdbc:postgresql://localhost/gis", "user", "pass", 10),
                        new net.pkhapps.idispatchx.common.config.OidcConfig(
                                URI.create("https://auth.example.com"),
                                URI.create("https://auth.example.com/.well-known/jwks.json"),
                                "gis-server")
                ));
    }

    private Map<String, String> createRequiredEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("GIS_TILE_DIR", "/var/tiles");
        envVars.put("GIS_DB_URL", "jdbc:postgresql://localhost/gis");
        envVars.put("GIS_DB_USER", "gisuser");
        envVars.put("GIS_DB_PASSWORD", "gispass");
        envVars.put("GIS_OIDC_ISSUER", "https://auth.example.com");
        envVars.put("GIS_OIDC_CLIENT_ID", "gis-server");
        return envVars;
    }
}
