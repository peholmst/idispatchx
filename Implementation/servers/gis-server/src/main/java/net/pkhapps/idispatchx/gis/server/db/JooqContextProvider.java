package net.pkhapps.idispatchx.gis.server.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Provides a jOOQ DSLContext configured for PostgreSQL.
 * <p>
 * The DSLContext is configured to use the PostgreSQL dialect and connected
 * to the provided data source.
 */
public final class JooqContextProvider {

    private static final Logger log = LoggerFactory.getLogger(JooqContextProvider.class);

    private final DSLContext dslContext;

    /**
     * Creates a new jOOQ context provider with the given data source.
     *
     * @param dataSource the data source to use
     */
    public JooqContextProvider(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");

        log.info("Initializing jOOQ DSLContext with PostgreSQL dialect");
        this.dslContext = DSL.using(dataSource, SQLDialect.POSTGRES);
        log.info("jOOQ DSLContext initialized");
    }

    /**
     * Returns the configured DSL context.
     *
     * @return the DSL context
     */
    public DSLContext getDslContext() {
        return dslContext;
    }
}
