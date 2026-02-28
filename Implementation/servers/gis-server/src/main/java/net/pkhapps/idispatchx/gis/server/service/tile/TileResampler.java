package net.pkhapps.idispatchx.gis.server.service.tile;

import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resamples tiles from available zoom levels to fill in missing zoom levels.
 * <p>
 * When a tile is requested at a zoom level that has no pre-rendered tiles,
 * this resampler extracts a sub-region from a lower zoom level tile and
 * upscales it using bilinear interpolation to produce a 256×256 tile.
 * <p>
 * The maximum resampling depth is 3 zoom levels (i.e., the source tile
 * must be no more than 3 levels below the requested zoom level).
 */
public final class TileResampler {

    private static final Logger log = LoggerFactory.getLogger(TileResampler.class);

    private static final String TILE_MATRIX_SET = "ETRS-TM35FIN";
    private static final int TILE_SIZE = 256;
    private static final int MAX_RESAMPLE_DEPTH = 3;

    private final Path tileDirectory;

    /**
     * Creates a new tile resampler.
     *
     * @param tileDirectory the base directory containing tile data
     */
    public TileResampler(Path tileDirectory) {
        this.tileDirectory = Objects.requireNonNull(tileDirectory, "tileDirectory must not be null");
    }

    /**
     * Resamples a tile at the requested zoom level from a lower zoom level source.
     * <p>
     * Uses bilinear interpolation to upscale the extracted sub-region to 256×256.
     *
     * @param layer      the tile layer
     * @param zoom       the requested zoom level
     * @param row        the requested row index
     * @param col        the requested column index
     * @return the resampled tile as PNG bytes, or null if no suitable source is available
     */
    public byte @Nullable [] resample(TileLayer layer, int zoom, int row, int col) {
        var sourceZoom = findSourceZoom(layer, zoom);
        if (sourceZoom < 0) {
            log.debug("No source zoom available for layer '{}' at zoom {}", layer.name(), zoom);
            return null;
        }

        var diff = zoom - sourceZoom;
        if (diff > MAX_RESAMPLE_DEPTH) {
            log.debug("Resample depth {} exceeds max {} for layer '{}' zoom {}",
                    diff, MAX_RESAMPLE_DEPTH, layer.name(), zoom);
            return null;
        }

        // Calculate source tile coordinates and sub-region
        var sourceRow = row >> diff;
        var sourceCol = col >> diff;
        var subRow = row & ((1 << diff) - 1);
        var subCol = col & ((1 << diff) - 1);
        var regionSize = TILE_SIZE >> diff;
        var xStart = subCol * regionSize;
        var yStart = subRow * regionSize;

        log.debug("Resampling layer '{}' zoom={} row={} col={} from source zoom={} row={} col={} region=[{},{},{},{}]",
                layer.name(), zoom, row, col, sourceZoom, sourceRow, sourceCol, xStart, yStart, regionSize, regionSize);

        // Load source tile
        var sourcePath = getTilePath(layer.name(), sourceZoom, sourceRow, sourceCol);
        BufferedImage sourceImage;
        try {
            if (!Files.exists(sourcePath)) {
                log.debug("Source tile not found: {}", sourcePath);
                return null;
            }
            sourceImage = ImageIO.read(sourcePath.toFile());
            if (sourceImage == null) {
                log.debug("Could not read source tile image: {}", sourcePath);
                return null;
            }
        } catch (IOException e) {
            log.warn("Failed to read source tile {}: {}", sourcePath, e.getMessage());
            return null;
        }

        // Extract sub-region and scale up to 256x256
        var subImage = sourceImage.getSubimage(xStart, yStart, regionSize, regionSize);
        var outputImage = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g2d = outputImage.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(subImage, 0, 0, TILE_SIZE, TILE_SIZE, null);
        } finally {
            g2d.dispose();
        }

        // Encode as PNG
        try {
            var baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("Failed to encode resampled tile as PNG: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Finds the nearest available source zoom level below the requested zoom.
     *
     * @param layer the tile layer
     * @param zoom  the requested zoom level
     * @return the nearest available zoom level, or -1 if none found
     */
    int findSourceZoom(TileLayer layer, int zoom) {
        return layer.nearestAvailableZoom(zoom - 1);
    }

    private Path getTilePath(String layerName, int zoom, int row, int col) {
        return tileDirectory
                .resolve(layerName)
                .resolve(TILE_MATRIX_SET)
                .resolve(String.valueOf(zoom))
                .resolve(String.valueOf(row))
                .resolve(col + ".png");
    }
}
