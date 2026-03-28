package net.pkhapps.idispatchx.gis.server.config;

import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigProperty;
import net.pkhapps.idispatchx.common.config.DatabaseConfig;
import net.pkhapps.idispatchx.common.config.OidcConfig;

import java.nio.file.Path;
import java.util.Objects;

/**
 * GIS Server configuration aggregating all settings needed to run the server.
 * <p>
 * Configuration is loaded from environment variables with the following names:
 * <ul>
 *   <li>{@code GIS_SERVER_PORT} - HTTP server port (default: 8080)</li>
 *   <li>{@code GIS_TILE_DIR} - Base path for tile storage (required)</li>
 *   <li>{@code GIS_CONTEXT_PATH} - URL context path prefix for reverse proxy (default: empty)</li>
 *   <li>{@code GIS_CORS_ALLOWED_ORIGINS} - Comma-separated list of allowed CORS origins (default: empty)</li>
 *   <li>{@code GIS_DB_URL} - JDBC connection URL (required)</li>
 *   <li>{@code GIS_DB_USER} - Database username (required)</li>
 *   <li>{@code GIS_DB_PASSWORD} - Database password</li>
 *   <li>{@code GIS_DB_PASSWORD_FILE} - Path to file containing password</li>
 *   <li>{@code GIS_DB_POOL_SIZE} - Connection pool size (default: 10)</li>
 *   <li>{@code GIS_OIDC_ISSUER} - OIDC provider issuer URL (required)</li>
 *   <li>{@code GIS_OIDC_JWKS_URL} - JWKS endpoint (defaults to well-known)</li>
 *   <li>{@code GIS_OIDC_CLIENT_ID} - OIDC client ID (required)</li>
 * </ul>
 *
 * @param port               the HTTP server port
 * @param tileDirectory      the base path for tile storage
 * @param contextPath        the URL context path prefix (empty or starts with {@code /}, no trailing slash)
 * @param corsAllowedOrigins comma-separated list of allowed CORS origins, or empty to disable CORS
 * @param databaseConfig     the database connection configuration
 * @param oidcConfig         the OIDC provider configuration
 */
public record GisServerConfig(
        int port,
        Path tileDirectory,
        String contextPath,
        String corsAllowedOrigins,
        DatabaseConfig databaseConfig,
        OidcConfig oidcConfig
) {

    /**
     * Default HTTP server port.
     */
    public static final int DEFAULT_PORT = 8080;

    // Environment variable names
    private static final String ENV_PORT = "GIS_SERVER_PORT";
    private static final String ENV_TILE_DIR = "GIS_TILE_DIR";
    public static final String ENV_CONTEXT_PATH = "GIS_CONTEXT_PATH";
    private static final String ENV_CORS_ALLOWED_ORIGINS = "GIS_CORS_ALLOWED_ORIGINS";
    private static final String ENV_DB_URL = "GIS_DB_URL";
    private static final String ENV_DB_USER = "GIS_DB_USER";
    private static final String ENV_DB_PASSWORD = "GIS_DB_PASSWORD";
    private static final String ENV_DB_PASSWORD_FILE = "GIS_DB_PASSWORD_FILE";
    private static final String ENV_DB_POOL_SIZE = "GIS_DB_POOL_SIZE";
    private static final String ENV_OIDC_ISSUER = "GIS_OIDC_ISSUER";
    private static final String ENV_OIDC_JWKS_URL = "GIS_OIDC_JWKS_URL";
    private static final String ENV_OIDC_CLIENT_ID = "GIS_OIDC_CLIENT_ID";

    /**
     * Creates a GIS server configuration with validation.
     *
     * @param port               the HTTP server port
     * @param tileDirectory      the base path for tile storage
     * @param contextPath        the URL context path prefix
     * @param corsAllowedOrigins comma-separated CORS origins
     * @param databaseConfig     the database connection configuration
     * @param oidcConfig         the OIDC provider configuration
     */
    public GisServerConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
        Objects.requireNonNull(tileDirectory, "tileDirectory must not be null");
        Objects.requireNonNull(contextPath, "contextPath must not be null");
        if (!contextPath.isEmpty()) {
            if (!contextPath.startsWith("/")) {
                throw new IllegalArgumentException(
                        "contextPath must be empty or start with '/', got: " + contextPath);
            }
            if (contextPath.endsWith("/")) {
                throw new IllegalArgumentException(
                        "contextPath must not end with '/', got: " + contextPath);
            }
        }
        Objects.requireNonNull(corsAllowedOrigins, "corsAllowedOrigins must not be null");
        Objects.requireNonNull(databaseConfig, "databaseConfig must not be null");
        Objects.requireNonNull(oidcConfig, "oidcConfig must not be null");
    }

    /**
     * Loads the GIS server configuration from environment variables.
     *
     * @return the configuration
     * @throws net.pkhapps.idispatchx.common.config.ConfigurationException if required properties are missing
     */
    public static GisServerConfig load() {
        return load(ConfigLoader.create());
    }

    /**
     * Loads the GIS server configuration using the provided config loader.
     *
     * @param loader the configuration loader
     * @return the configuration
     * @throws net.pkhapps.idispatchx.common.config.ConfigurationException if required properties are missing
     */
    public static GisServerConfig load(ConfigLoader loader) {
        var portProperty = ConfigProperty.optionalInt(ENV_PORT, DEFAULT_PORT);
        var tileDirProperty = ConfigProperty.requiredPath(ENV_TILE_DIR);
        var contextPathProperty = ConfigProperty.optionalString(ENV_CONTEXT_PATH, "");
        var corsOriginsProperty = ConfigProperty.optionalString(ENV_CORS_ALLOWED_ORIGINS, "");

        var port = loader.get(portProperty);
        var tileDir = loader.get(tileDirProperty);
        var contextPath = loader.get(contextPathProperty);
        var corsAllowedOrigins = loader.get(corsOriginsProperty);

        var dbConfig = DatabaseConfig.builder(
                ENV_DB_URL,
                ENV_DB_USER,
                ENV_DB_PASSWORD,
                ENV_DB_PASSWORD_FILE,
                ENV_DB_POOL_SIZE
        ).load(loader);

        var oidcConfig = OidcConfig.builder(
                ENV_OIDC_ISSUER,
                ENV_OIDC_JWKS_URL,
                ENV_OIDC_CLIENT_ID
        ).load(loader);

        return new GisServerConfig(port, tileDir, contextPath, corsAllowedOrigins, dbConfig, oidcConfig);
    }
}
