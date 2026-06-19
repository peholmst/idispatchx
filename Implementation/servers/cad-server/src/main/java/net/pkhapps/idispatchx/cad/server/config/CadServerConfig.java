package net.pkhapps.idispatchx.cad.server.config;

import net.pkhapps.idispatchx.cad.adapter.secondary.commandlog.CommandLogConfig;
import net.pkhapps.idispatchx.cad.adapter.secondary.snapshot.SnapshotConfig;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalConfig;
import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigProperty;
import net.pkhapps.idispatchx.common.config.OidcConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * CAD Server configuration aggregating all settings needed to run the server.
 * <p>
 * Configuration is loaded from environment variables:
 * <ul>
 *   <li>{@code CAD_SERVER_PORT} - HTTP server port (default: 8080)</li>
 *   <li>{@code CAD_CONTEXT_PATH} - URL context path prefix (default: empty)</li>
 *   <li>{@code CAD_CORS_ALLOWED_ORIGINS} - Comma-separated CORS origins (default: empty)</li>
 *   <li>{@code CAD_SNAPSHOT_INTERVAL_SECONDS} - Snapshot interval in seconds (default: 3600)</li>
 *   <li>{@code CAD_IDEMPOTENCY_RETENTION_HOURS} - Idempotency cache retention in hours (default: 24)</li>
 *   <li>WAL settings via {@link WalConfig}: {@code CAD_WAL_*}</li>
 *   <li>Snapshot settings via {@link SnapshotConfig}: {@code CAD_SNAPSHOT_*}</li>
 *   <li>Command log settings via {@link CommandLogConfig}: {@code CAD_COMMAND_LOG_*}</li>
 *   <li>OIDC settings via {@link OidcConfig}: {@code CAD_OIDC_*}</li>
 * </ul>
 *
 * @param port                       the HTTP server port
 * @param contextPath                the URL context path prefix (empty or starts with {@code /})
 * @param corsAllowedOrigins         comma-separated CORS origins, or empty to disable CORS
 * @param walConfig                  WAL configuration
 * @param snapshotConfig             snapshot configuration
 * @param commandLogConfig           command audit log configuration
 * @param oidcConfig                 OIDC provider configuration
 * @param snapshotIntervalSeconds    how often to create a WAL snapshot (seconds)
 * @param idempotencyRetentionPeriod how long to retain processed command IDs for idempotency
 */
public record CadServerConfig(
        int port,
        String contextPath,
        String corsAllowedOrigins,
        WalConfig walConfig,
        SnapshotConfig snapshotConfig,
        CommandLogConfig commandLogConfig,
        OidcConfig oidcConfig,
        long snapshotIntervalSeconds,
        Duration idempotencyRetentionPeriod
) {

    public static final int DEFAULT_PORT = 8080;
    public static final long DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 3600L;
    public static final int DEFAULT_IDEMPOTENCY_RETENTION_HOURS = 24;

    private static final String ENV_PORT = "CAD_SERVER_PORT";
    private static final String ENV_CONTEXT_PATH = "CAD_CONTEXT_PATH";
    private static final String ENV_CORS_ALLOWED_ORIGINS = "CAD_CORS_ALLOWED_ORIGINS";
    private static final String ENV_SNAPSHOT_INTERVAL_SECONDS = "CAD_SNAPSHOT_INTERVAL_SECONDS";
    private static final String ENV_IDEMPOTENCY_RETENTION_HOURS = "CAD_IDEMPOTENCY_RETENTION_HOURS";

    public CadServerConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
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
        Objects.requireNonNull(walConfig, "walConfig must not be null");
        Objects.requireNonNull(snapshotConfig, "snapshotConfig must not be null");
        Objects.requireNonNull(commandLogConfig, "commandLogConfig must not be null");
        Objects.requireNonNull(oidcConfig, "oidcConfig must not be null");
        if (snapshotIntervalSeconds < 1) {
            throw new IllegalArgumentException("snapshotIntervalSeconds must be at least 1");
        }
        Objects.requireNonNull(idempotencyRetentionPeriod, "idempotencyRetentionPeriod must not be null");
    }

    /**
     * Loads the CAD server configuration from environment variables.
     *
     * @param loader the configuration loader
     * @return the configuration
     * @throws net.pkhapps.idispatchx.common.config.ConfigurationException if required properties are missing
     */
    public static CadServerConfig load(ConfigLoader loader) {
        var port = loader.get(ConfigProperty.optionalInt(ENV_PORT, DEFAULT_PORT));
        var contextPath = loader.get(ConfigProperty.optionalString(ENV_CONTEXT_PATH, ""));
        var corsAllowedOrigins = loader.get(ConfigProperty.optionalString(ENV_CORS_ALLOWED_ORIGINS, ""));
        var snapshotIntervalSeconds = loader.get(
                ConfigProperty.optionalInt(ENV_SNAPSHOT_INTERVAL_SECONDS, (int) DEFAULT_SNAPSHOT_INTERVAL_SECONDS));
        var idempotencyRetentionHours = loader.get(
                ConfigProperty.optionalInt(ENV_IDEMPOTENCY_RETENTION_HOURS, DEFAULT_IDEMPOTENCY_RETENTION_HOURS));

        var walConfig = WalConfig.builder().load(loader);
        var snapshotConfig = SnapshotConfig.builder().load(loader);
        var commandLogConfig = CommandLogConfig.builder(
                "CAD_COMMAND_LOG_DIRECTORY",
                "CAD_COMMAND_LOG_FILE_NAME",
                "CAD_COMMAND_LOG_MAX_FILE_SIZE_MB"
        ).load(loader);
        var oidcConfig = OidcConfig.builder(
                "CAD_OIDC_ISSUER",
                "CAD_OIDC_JWKS_URL",
                "CAD_OIDC_CLIENT_ID"
        ).load(loader);

        return new CadServerConfig(
                port,
                contextPath,
                corsAllowedOrigins,
                walConfig,
                snapshotConfig,
                commandLogConfig,
                oidcConfig,
                snapshotIntervalSeconds,
                Duration.ofHours(idempotencyRetentionHours)
        );
    }
}
