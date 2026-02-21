package net.pkhapps.idispatchx.gis.server.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Runs Flyway database migrations on server startup.
 * <p>
 * Migrations are loaded from the gis-database module's migration scripts
 * on the classpath (db/migration directory).
 */
public final class FlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrator.class);

    /**
     * Default schema for GIS tables.
     */
    private static final String DEFAULT_SCHEMA = "gis";

    /**
     * Location of migration scripts on classpath.
     */
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private final Flyway flyway;

    /**
     * Creates a new Flyway migrator with the given data source.
     *
     * @param dataSource the data source to migrate
     */
    public FlywayMigrator(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");

        this.flyway = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(DEFAULT_SCHEMA)
                .createSchemas(true)
                .locations(MIGRATION_LOCATION)
                .load();
    }

    /**
     * Runs pending database migrations.
     * <p>
     * This method logs the current migration status and applies any pending migrations.
     * If migrations fail, an exception is thrown which should prevent server startup.
     *
     * @throws RuntimeException if migration fails
     */
    public void migrate() {
        log.info("Checking database migration status...");

        // Log current state
        var info = flyway.info();
        var current = info.current();
        if (current != null) {
            log.info("Current schema version: {} ({})", current.getVersion(), current.getDescription());
        } else {
            log.info("No migrations applied yet");
        }

        // Log pending migrations
        var pending = info.pending();
        if (pending.length > 0) {
            log.info("Found {} pending migration(s):", pending.length);
            for (MigrationInfo migration : pending) {
                log.info("  - {} ({})", migration.getVersion(), migration.getDescription());
            }
        } else {
            log.info("Database is up to date, no migrations pending");
            return;
        }

        // Apply migrations
        log.info("Applying database migrations...");
        MigrateResult result = flyway.migrate();

        if (result.success) {
            log.info("Successfully applied {} migration(s)", result.migrationsExecuted);
            if (result.targetSchemaVersion != null) {
                log.info("Database now at version: {}", result.targetSchemaVersion);
            }
        } else {
            log.error("Database migration failed");
            throw new RuntimeException("Database migration failed: " + result.warnings);
        }
    }

    /**
     * Returns the Flyway instance for advanced operations.
     *
     * @return the Flyway instance
     */
    public Flyway getFlyway() {
        return flyway;
    }
}
