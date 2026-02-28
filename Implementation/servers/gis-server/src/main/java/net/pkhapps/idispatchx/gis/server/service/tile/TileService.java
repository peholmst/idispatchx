package net.pkhapps.idispatchx.gis.server.service.tile;

import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates tile retrieval, serving pre-rendered tiles when available
 * and falling back to resampled tiles when not.
 * <p>
 * Results are represented as a sealed {@link TileResult} interface that
 * allows the caller to distinguish between pre-rendered and resampled tiles
 * (e.g., for different cache-control headers).
 */
public final class TileService {

    private static final Logger log = LoggerFactory.getLogger(TileService.class);

    private static final String TILE_MATRIX_SET = "ETRS-TM35FIN";

    /**
     * Sealed interface representing the result of a tile retrieval operation.
     */
    public sealed interface TileResult {
        /**
         * A tile that was read directly from the pre-rendered tile storage.
         *
         * @param data the raw PNG byte data
         */
        record PreRendered(byte[] data) implements TileResult {
        }

        /**
         * A tile that was produced by resampling from a lower zoom level.
         *
         * @param data the raw PNG byte data
         */
        record Resampled(byte[] data) implements TileResult {
        }
    }

    private final Path tileDirectory;
    private final Map<String, TileLayer> layers;
    private final TileResampler resampler;
    private final TileCache cache;

    /**
     * Creates a new tile service.
     *
     * @param tileDirectory the base directory containing tile data
     * @param layers        the map of known layers (from LayerDiscovery)
     * @param resampler     the resampler for producing tiles at missing zoom levels
     * @param cache         the cache for resampled tiles
     */
    public TileService(Path tileDirectory, Map<String, TileLayer> layers,
                       TileResampler resampler, TileCache cache) {
        this.tileDirectory = Objects.requireNonNull(tileDirectory, "tileDirectory must not be null");
        this.layers = Map.copyOf(Objects.requireNonNull(layers, "layers must not be null"));
        this.resampler = Objects.requireNonNull(resampler, "resampler must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    /**
     * Retrieves a tile for the given layer and coordinates.
     * <p>
     * Lookup order:
     * <ol>
     *   <li>Pre-rendered tile file on disk</li>
     *   <li>Resampled tile from cache</li>
     *   <li>Newly resampled tile (then cached)</li>
     * </ol>
     *
     * @param layerName the name of the tile layer
     * @param zoom      the zoom level
     * @param row       the row index
     * @param col       the column index
     * @return the tile result
     * @throws IllegalArgumentException if the layer is not known
     * @throws TileNotFoundException    if no tile can be produced for the given coordinates
     */
    public TileResult getTile(String layerName, int zoom, int row, int col) {
        var layer = layers.get(layerName);
        if (layer == null) {
            throw new IllegalArgumentException("Unknown layer: " + layerName);
        }

        // Try pre-rendered tile first
        if (layer.hasZoomLevel(zoom)) {
            var tilePath = getTilePath(layerName, zoom, row, col);
            if (Files.exists(tilePath)) {
                try {
                    var data = Files.readAllBytes(tilePath);
                    log.debug("Served pre-rendered tile for layer '{}' zoom={} row={} col={}", layerName, zoom, row, col);
                    return new TileResult.PreRendered(data);
                } catch (IOException e) {
                    log.warn("Failed to read pre-rendered tile {}: {}", tilePath, e.getMessage());
                }
            }
        }

        // Try resampled tile from cache
        var cached = cache.get(layerName, zoom, row, col);
        if (cached != null) {
            log.debug("Served cached resampled tile for layer '{}' zoom={} row={} col={}", layerName, zoom, row, col);
            return new TileResult.Resampled(cached);
        }

        // Resample from lower zoom level
        var data = resampler.resample(layer, zoom, row, col);
        if (data != null) {
            cache.put(layerName, zoom, row, col, data);
            log.debug("Served freshly resampled tile for layer '{}' zoom={} row={} col={}", layerName, zoom, row, col);
            return new TileResult.Resampled(data);
        }

        throw new TileNotFoundException("No tile available for layer '" + layerName +
                "' at zoom=" + zoom + " row=" + row + " col=" + col);
    }

    /**
     * Returns the map of known tile layers.
     *
     * @return an unmodifiable view of the known layers
     */
    public Map<String, TileLayer> getLayers() {
        return layers;
    }

    private Path getTilePath(String layerName, int zoom, int row, int col) {
        return tileDirectory
                .resolve(layerName)
                .resolve(TILE_MATRIX_SET)
                .resolve(String.valueOf(zoom))
                .resolve(String.valueOf(row))
                .resolve(col + ".png");
    }

    /**
     * Exception thrown when no tile can be found or produced for the given coordinates.
     */
    public static final class TileNotFoundException extends RuntimeException {
        public TileNotFoundException(String message) {
            super(message);
        }
    }
}
