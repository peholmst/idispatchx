package net.pkhapps.idispatchx.gis.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;

/**
 * Base class for integration tests that require a PostgreSQL/PostGIS database.
 * <p>
 * A single container is started once when the class is first loaded and shared
 * across all test classes that extend this base class. Flyway migrations are
 * applied once during setup.
 * <p>
 * Subclasses should use {@link #dsl} to execute database queries, and
 * {@link #dataSource} to access the underlying connection pool.
 */
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgis/postgis:16-3.4")
                    .waitingFor(Wait.forListeningPort());

    protected static final DataSource dataSource;
    protected static final DSLContext dsl;

    static {
        POSTGRES.start();

        var config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    /**
     * Truncates all GIS data tables in a safe order (respecting FK constraints).
     * Call this in {@code @BeforeEach} or {@code @AfterEach} to reset state between tests.
     */
    protected static void truncateAllTables() {
        dsl.execute("TRUNCATE gis.named_place, gis.address_point, gis.road_segment, gis.municipality CASCADE");
    }
}
