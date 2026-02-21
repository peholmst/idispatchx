package net.pkhapps.idispatchx.gis.server;

import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigurationException;
import net.pkhapps.idispatchx.gis.server.config.GisServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the GIS Server application.
 * <p>
 * The server loads configuration from environment variables and starts
 * the HTTP server. Graceful shutdown is handled via a shutdown hook.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
        // Prevent instantiation
    }

    /**
     * Application entry point.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        log.info("Starting iDispatchX GIS Server...");

        try {
            // Load configuration from classpath (application.properties) with env var overrides
            var configLoader = ConfigLoader.fromClasspath("application.properties");
            var config = GisServerConfig.load(configLoader);

            // Create and start server
            var server = new GisServer(config);

            // Register shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received");
                server.close();
            }, "shutdown-hook"));

            // Start the server
            server.start();

            log.info("iDispatchX GIS Server is ready");

        } catch (ConfigurationException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("Failed to start GIS Server", e);
            System.exit(1);
        }
    }
}
