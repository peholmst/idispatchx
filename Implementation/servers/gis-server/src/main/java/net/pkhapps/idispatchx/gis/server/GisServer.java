package net.pkhapps.idispatchx.gis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import net.pkhapps.idispatchx.common.auth.JwksClient;
import net.pkhapps.idispatchx.common.auth.LogoutTokenValidator;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import net.pkhapps.idispatchx.gis.server.api.wmts.CapabilitiesGenerator;
import net.pkhapps.idispatchx.gis.server.api.wmts.WmtsController;
import net.pkhapps.idispatchx.gis.server.auth.BackChannelLogoutHandler;
import net.pkhapps.idispatchx.gis.server.auth.JwtAuthHandler;
import net.pkhapps.idispatchx.gis.server.auth.RoleAuthHandler;
import net.pkhapps.idispatchx.gis.server.config.GisServerConfig;
import net.pkhapps.idispatchx.gis.server.db.DataSourceProvider;
import net.pkhapps.idispatchx.gis.server.db.FlywayMigrator;
import net.pkhapps.idispatchx.gis.server.db.JooqContextProvider;
import net.pkhapps.idispatchx.gis.server.service.tile.LayerDiscovery;
import net.pkhapps.idispatchx.gis.server.service.tile.TileCache;
import net.pkhapps.idispatchx.gis.server.service.tile.TileResampler;
import net.pkhapps.idispatchx.gis.server.service.tile.TileService;
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
    private final SessionStore sessionStore;
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

        // Initialize auth services
        var jwksClient = new JwksClient(config.oidcConfig().jwksUrl());
        var tokenValidator = new TokenValidator(jwksClient, config.oidcConfig().issuer().toString());
        this.sessionStore = new SessionStore();
        var logoutValidator = new LogoutTokenValidator(
                jwksClient,
                config.oidcConfig().issuer().toString(),
                config.oidcConfig().clientId());
        var jwtAuth = new JwtAuthHandler(tokenValidator, sessionStore);
        var roleAuth = RoleAuthHandler.requireAnyRole(Role.DISPATCHER, Role.OBSERVER);
        var logoutHandler = new BackChannelLogoutHandler(logoutValidator, sessionStore);

        // Initialize tile services
        var layers = new LayerDiscovery(config.tileDirectory()).discoverLayers();
        var tileService = new TileService(
                config.tileDirectory(), layers,
                new TileResampler(config.tileDirectory()),
                new TileCache());
        var capGen = new CapabilitiesGenerator(layers);

        // Register routes
        new WmtsController(tileService, capGen).registerRoutes(javalin, jwtAuth, roleAuth);
        javalin.post("/api/v1/auth/logout", logoutHandler);

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
            sessionStore.close();
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
