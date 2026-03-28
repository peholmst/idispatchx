package net.pkhapps.idispatchx.gis.server.api.health;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Health check endpoint for infrastructure monitoring.
 * <p>
 * This is an internal endpoint not exposed through the public reverse proxy
 * (per Security NFR). It does not require authentication.
 * <p>
 * Checks:
 * <ul>
 *   <li>Database connectivity (execute simple query)</li>
 *   <li>Tile directory accessibility (check exists and readable)</li>
 *   <li>Discovered layers list</li>
 * </ul>
 * <p>
 * Endpoint: {@code GET /health}
 */
public final class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private final DataSource dataSource;
    private final Path tileDirectory;
    private final Map<String, TileLayer> layers;

    /**
     * Creates a new health controller.
     *
     * @param dataSource    the data source for checking database connectivity
     * @param tileDirectory the base path for tile storage
     * @param layers        the discovered tile layers
     */
    public HealthController(DataSource dataSource, Path tileDirectory, Map<String, TileLayer> layers) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tileDirectory = Objects.requireNonNull(tileDirectory, "tileDirectory must not be null");
        this.layers = Map.copyOf(Objects.requireNonNull(layers, "layers must not be null"));
    }

    /**
     * Registers the health check route on the given Javalin instance.
     * No authentication filters are applied.
     *
     * @param app         the Javalin application
     * @param contextPath the URL context path prefix (empty or starts with {@code /})
     */
    public void registerRoutes(Javalin app, String contextPath) {
        app.get(contextPath + "/health", this::handleHealthCheck);
    }

    private void handleHealthCheck(Context ctx) {
        var components = new LinkedHashMap<String, Object>();
        boolean allHealthy = true;

        // Check database
        var dbStatus = checkDatabase();
        components.put("database", dbStatus);
        if (!STATUS_UP.equals(dbStatus.get("status"))) {
            allHealthy = false;
        }

        // Check tile directory
        var tileStatus = checkTileDirectory();
        components.put("tileDirectory", tileStatus);
        if (!STATUS_UP.equals(tileStatus.get("status"))) {
            allHealthy = false;
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("status", allHealthy ? STATUS_UP : STATUS_DOWN);
        response.put("components", components);

        ctx.status(allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE);
        ctx.json(response);
    }

    Map<String, Object> checkDatabase() {
        var result = new LinkedHashMap<String, Object>();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT 1")) {
            if (rs.next()) {
                result.put("status", STATUS_UP);
            } else {
                result.put("status", STATUS_DOWN);
                result.put("error", "Query returned no results");
            }
        } catch (SQLException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            result.put("status", STATUS_DOWN);
            result.put("error", e.getMessage());
        }
        return result;
    }

    Map<String, Object> checkTileDirectory() {
        var result = new LinkedHashMap<String, Object>();
        if (Files.isDirectory(tileDirectory) && Files.isReadable(tileDirectory)) {
            result.put("status", STATUS_UP);
            result.put("layers", List.copyOf(layers.keySet()));
        } else {
            result.put("status", STATUS_DOWN);
            if (!Files.exists(tileDirectory)) {
                result.put("error", "Tile directory does not exist");
            } else if (!Files.isDirectory(tileDirectory)) {
                result.put("error", "Tile path is not a directory");
            } else {
                result.put("error", "Tile directory is not readable");
            }
        }
        return result;
    }
}
