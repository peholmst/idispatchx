package net.pkhapps.idispatchx.gis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import net.pkhapps.idispatchx.gis.server.config.GisServerConfig;
import net.pkhapps.idispatchx.gis.server.db.DataSourceProvider;
import net.pkhapps.idispatchx.gis.server.db.FlywayMigrator;
import net.pkhapps.idispatchx.gis.server.db.JooqContextProvider;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GIS Server application that provides map tiles and geocoding services.
 * <p>
 * This class manages the server lifecycle including initialization of:
 * <ul>
 *   <li>Database connection pool (HikariCP)</li>
 *   <li>Database migrations (Flyway)</li>
 *   <li>jOOQ context for database queries</li>
 *   <li>Javalin HTTP server</li>
 * </ul>
 */
public final class GisServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GisServer.class);

    private final GisServerConfig config;
    private final DataSourceProvider dataSourceProvider;
    private final JooqContextProvider jooqContextProvider;
    private final Javalin javalin;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a new GIS server with the given configuration.
     *
     * @param config the server configuration
     */
    public GisServer(GisServerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");

        log.info("Initializing GIS Server...");

        // Initialize database
        this.dataSourceProvider = new DataSourceProvider(config.databaseConfig());
        this.jooqContextProvider = new JooqContextProvider(dataSourceProvider.getDataSource());

        // Run migrations
        var migrator = new FlywayMigrator(dataSourceProvider.getDataSource());
        migrator.migrate();

        // Initialize Javalin
        this.javalin = createJavalin();

        log.info("GIS Server initialized");
    }

    private Javalin createJavalin() {
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return Javalin.create(javalinConfig -> {
            javalinConfig.jsonMapper(new JavalinJackson(objectMapper, true));
            javalinConfig.showJavalinBanner = false;
        });
    }

    /**
     * Starts the HTTP server.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting GIS Server on port {}", config.port());
            javalin.start(config.port());
            log.info("GIS Server started on port {}", config.port());
        } else {
            log.warn("GIS Server is already running");
        }
    }

    /**
     * Stops the HTTP server and releases resources.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping GIS Server...");
            javalin.stop();
            dataSourceProvider.close();
            log.info("GIS Server stopped");
        }
    }

    /**
     * Closes the server, releasing all resources.
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Returns the server configuration.
     *
     * @return the configuration
     */
    public GisServerConfig getConfig() {
        return config;
    }

    /**
     * Returns the jOOQ DSL context for database queries.
     *
     * @return the DSL context
     */
    public DSLContext getDslContext() {
        return jooqContextProvider.getDslContext();
    }

    /**
     * Returns the Javalin instance for route configuration.
     *
     * @return the Javalin instance
     */
    public Javalin getJavalin() {
        return javalin;
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running.get();
    }
}
