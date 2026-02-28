package net.pkhapps.idispatchx.gis.server.service.tile;

import net.pkhapps.idispatchx.gis.server.model.TileCoordinates;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Scans the tile directory structure to discover available layers and their zoom levels.
 * <p>
 * Expected directory structure:
 * <pre>
 * {tileDirectory}/
 *   {layerName}/
 *     ETRS-TM35FIN/
 *       {zoom}/          ← numeric directory name (0-15)
 *         {row}/
 *           {col}.png
 * </pre>
 * <p>
 * Directories that do not match the expected naming convention are silently skipped.
 */
public final class LayerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(LayerDiscovery.class);

    private static final String TILE_MATRIX_SET = "ETRS-TM35FIN";

    private final Path tileDirectory;

    /**
     * Creates a new layer discovery for the specified tile directory.
     *
     * @param tileDirectory the base directory containing tile data
     */
    public LayerDiscovery(Path tileDirectory) {
        this.tileDirectory = Objects.requireNonNull(tileDirectory, "tileDirectory must not be null");
    }

    /**
     * Discovers all available tile layers by scanning the tile directory.
     * <p>
     * Returns a map from layer name to {@link TileLayer}. Layers without
     * any valid zoom levels are excluded from the result.
     *
     * @return a map of layer name to TileLayer (immutable)
     */
    public Map<String, TileLayer> discoverLayers() {
        log.info("Discovering tile layers in {}", tileDirectory);

        var layers = new ArrayList<TileLayer>();

        if (!Files.isDirectory(tileDirectory)) {
            log.warn("Tile directory does not exist or is not a directory: {}", tileDirectory);
            return Map.of();
        }

        try (var stream = Files.list(tileDirectory)) {
            stream.filter(Files::isDirectory)
                    .forEach(layerDir -> {
                        var layerName = layerDir.getFileName().toString();
                        var layer = discoverLayer(layerName, layerDir);
                        if (layer != null) {
                            layers.add(layer);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list tile directory {}: {}", tileDirectory, e.getMessage());
        }

        var result = new java.util.LinkedHashMap<String, TileLayer>();
        for (var layer : layers) {
            result.put(layer.name(), layer);
        }

        log.info("Discovered {} tile layer(s): {}", result.size(), result.keySet());
        return Map.copyOf(result);
    }

    private TileLayer discoverLayer(String layerName, Path layerDir) {
        var matrixSetDir = layerDir.resolve(TILE_MATRIX_SET);
        if (!Files.isDirectory(matrixSetDir)) {
            log.debug("Skipping layer directory {} - missing {} subdirectory", layerDir, TILE_MATRIX_SET);
            return null;
        }

        var zoomLevels = new HashSet<Integer>();

        try (var stream = Files.list(matrixSetDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(zoomDir -> {
                        var dirName = zoomDir.getFileName().toString();
                        try {
                            int zoom = Integer.parseInt(dirName);
                            if (zoom >= TileCoordinates.MIN_ZOOM && zoom <= TileCoordinates.MAX_ZOOM) {
                                zoomLevels.add(zoom);
                            } else {
                                log.debug("Skipping zoom directory {} - out of valid range ({}-{})",
                                        zoomDir, TileCoordinates.MIN_ZOOM, TileCoordinates.MAX_ZOOM);
                            }
                        } catch (NumberFormatException e) {
                            log.debug("Skipping non-numeric zoom directory: {}", zoomDir);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list zoom directories in {}: {}", matrixSetDir, e.getMessage());
            return null;
        }

        if (zoomLevels.isEmpty()) {
            log.debug("Skipping layer {} - no valid zoom levels found", layerName);
            return null;
        }

        log.debug("Discovered layer '{}' with zoom levels: {}", layerName, zoomLevels);
        return new TileLayer(layerName, zoomLevels);
    }
}
