package net.pkhapps.idispatchx.gis.importer.db;

import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Manages the PostgreSQL database connection for the GIS Data Importer.
 * Runs Flyway migrations on connect to ensure the schema is current,
 * and provides a jOOQ {@link DSLContext} for database operations.
 */
public final class DatabaseConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnection.class);

    private final DataSource dataSource;
    private final DSLContext dsl;

    public DatabaseConnection(String dbUrl, String dbUser, String dbPassword) {
        var ds = new PGSimpleDataSource();
        ds.setUrl(dbUrl);
        ds.setUser(dbUser);
        ds.setPassword(dbPassword);
        this.dataSource = ds;

        LOG.info("Running Flyway migrations on {}", dbUrl);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        LOG.info("Database connection established");
    }

    /**
     * Returns the jOOQ DSLContext for database operations.
     */
    public DSLContext dsl() {
        return dsl;
    }

    @Override
    public void close() {
        // PGSimpleDataSource does not pool connections; nothing to close.
    }
}
