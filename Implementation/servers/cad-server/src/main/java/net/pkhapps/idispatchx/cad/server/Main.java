package net.pkhapps.idispatchx.cad.server;

import net.pkhapps.idispatchx.cad.server.config.CadServerConfig;
import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main entry point for the CAD Server application.
 * <p>
 * The server loads configuration from environment variables and starts the HTTP server.
 * On startup it replays the WAL (and any snapshot) to reconstruct in-memory state.
 * Graceful shutdown is handled via a shutdown hook.
 * <p>
 * Configuration sources (in order of precedence):
 * <ol>
 *   <li>Environment variables (highest priority)</li>
 *   <li>System properties</li>
 *   <li>Properties file (from command line argument or classpath)</li>
 * </ol>
 * <p>
 * Usage: {@code java -jar cad-server.jar [config-file]}
 * <p>
 * If a configuration file path is provided as a command line argument and the file exists,
 * it will be used. Otherwise, {@code application.properties} from the classpath is used.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_CONFIG_RESOURCE = "application.properties";

    private Main() {
    }

    /**
     * Application entry point.
     *
     * @param args command line arguments; optionally the first argument is a path to a configuration file
     */
    public static void main(String[] args) {
        log.info("Starting iDispatchX CAD Server...");

        try {
            var configLoader = createConfigLoader(args);
            var config = CadServerConfig.load(configLoader);

            var server = new CadServer(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received");
                server.close();
            }, "shutdown-hook"));

            server.start();

            log.info("iDispatchX CAD Server is ready");

        } catch (ConfigurationException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("Failed to start CAD Server", e);
            System.exit(1);
        }
    }

    /**
     * Creates a configuration loader based on command line arguments.
     * <p>
     * If a configuration file path is provided and the file exists, loads from that file.
     * Otherwise, loads from the classpath resource {@code application.properties}.
     *
     * @param args command line arguments
     * @return the configuration loader
     */
    private static ConfigLoader createConfigLoader(String[] args) {
        if (args.length > 0) {
            var configPath = Path.of(args[0]);
            if (Files.exists(configPath)) {
                log.info("Using configuration file: {}", configPath);
                return ConfigLoader.fromFile(configPath);
            } else {
                log.warn("Configuration file not found: {}, falling back to classpath", configPath);
            }
        }
        log.info("Using classpath configuration: {}", DEFAULT_CONFIG_RESOURCE);
        return ConfigLoader.fromClasspath(DEFAULT_CONFIG_RESOURCE);
    }
}
