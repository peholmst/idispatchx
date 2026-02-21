package net.pkhapps.idispatchx.gis.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.pkhapps.idispatchx.common.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Provides a HikariCP connection pool configured for PostgreSQL/PostGIS.
 * <p>
 * The connection pool validates connections using a PostGIS function to ensure
 * the PostGIS extension is available and working.
 */
public final class DataSourceProvider implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

    /**
     * Connection validation query using PostGIS function.
     */
    private static final String CONNECTION_TEST_QUERY = "SELECT PostGIS_Version()";

    /**
     * Default connection timeout in milliseconds.
     */
    private static final long CONNECTION_TIMEOUT_MS = 30_000;

    /**
     * Default idle timeout in milliseconds (10 minutes).
     */
    private static final long IDLE_TIMEOUT_MS = 600_000;

    /**
     * Default max lifetime in milliseconds (30 minutes).
     */
    private static final long MAX_LIFETIME_MS = 1_800_000;

    private final HikariDataSource dataSource;

    /**
     * Creates a new data source provider with the given database configuration.
     *
     * @param config the database configuration
     */
    public DataSourceProvider(DatabaseConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.url());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.poolSize());
        hikariConfig.setMinimumIdle(Math.min(2, config.poolSize()));
        hikariConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        hikariConfig.setIdleTimeout(IDLE_TIMEOUT_MS);
        hikariConfig.setMaxLifetime(MAX_LIFETIME_MS);
        hikariConfig.setConnectionTestQuery(CONNECTION_TEST_QUERY);
        hikariConfig.setPoolName("gis-server-pool");

        // PostgreSQL specific settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        log.info("Initializing HikariCP connection pool for {}", sanitizeJdbcUrl(config.url()));
        this.dataSource = new HikariDataSource(hikariConfig);
        log.info("HikariCP connection pool initialized with {} connections", config.poolSize());
    }

    /**
     * Returns the configured data source.
     *
     * @return the data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Closes the connection pool, releasing all connections.
     */
    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            log.info("Shutting down HikariCP connection pool");
            dataSource.close();
            log.info("HikariCP connection pool shut down");
        }
    }

    /**
     * Sanitizes a JDBC URL for logging by removing potential credentials.
     * JDBC URLs can contain passwords in query parameters (e.g., ?password=secret).
     *
     * @param url the JDBC URL to sanitize
     * @return a sanitized URL safe for logging
     */
    private static String sanitizeJdbcUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Remove query parameters which may contain credentials
        var queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            return url.substring(0, queryIndex) + "?[REDACTED]";
        }
        // Also handle credentials in userinfo portion (jdbc:postgresql://user:pass@host/db)
        var atIndex = url.indexOf('@');
        if (atIndex > 0) {
            var protocolEnd = url.indexOf("://");
            if (protocolEnd > 0 && protocolEnd < atIndex) {
                return url.substring(0, protocolEnd + 3) + "[REDACTED]@" + url.substring(atIndex + 1);
            }
        }
        return url;
    }
}
