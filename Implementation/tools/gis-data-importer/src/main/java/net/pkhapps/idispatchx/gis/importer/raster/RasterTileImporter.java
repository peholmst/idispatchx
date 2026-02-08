package net.pkhapps.idispatchx.gis.importer.raster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the raster tile import pipeline: parses world files, determines zoom levels,
 * extracts tiles from source images, and writes them to the tile directory.
 */
public final class RasterTileImporter {

    private static final Logger LOG = LoggerFactory.getLogger(RasterTileImporter.class);

    private final TileWriter tileWriter;

    /**
     * Creates a new raster tile importer.
     *
     * @param tileDir base tile directory
     * @param layer   layer name
     */
    public RasterTileImporter(Path tileDir, String layer) {
        this.tileWriter = new TileWriter(tileDir, layer);
    }

    /**
     * Imports a single PNG file and its associated world file.
     *
     * @param pngFile path to the PNG file
     * @return number of tiles written
     */
    public int importFile(Path pngFile) {
        var worldFile = deriveWorldFilePath(pngFile);
        if (!Files.exists(worldFile)) {
            LOG.warn("Skipping {}: no world file found at {}", pngFile.getFileName(), worldFile);
            return 0;
        }

        WorldFileParser.WorldFileData worldData;
        try {
            worldData = WorldFileParser.parse(worldFile);
        } catch (IOException e) {
            LOG.warn("Skipping {}: cannot read world file: {}", pngFile.getFileName(), e.getMessage());
            return 0;
        } catch (IllegalArgumentException e) {
            LOG.warn("Skipping {}: invalid world file: {}", pngFile.getFileName(), e.getMessage());
            return 0;
        }

        int zoom;
        try {
            zoom = TileMatrixSet.zoomLevel(worldData.pixelWidth());
        } catch (IllegalArgumentException e) {
            LOG.warn("Skipping {}: {}", pngFile.getFileName(), e.getMessage());
            return 0;
        }

        BufferedImage source;
        try {
            source = ImageIO.read(pngFile.toFile());
        } catch (IOException e) {
            LOG.warn("Skipping {}: cannot read image: {}", pngFile.getFileName(), e.getMessage());
            return 0;
        }
        if (source == null) {
            LOG.warn("Skipping {}: unsupported image format", pngFile.getFileName());
            return 0;
        }

        // Convert indexed color to ARGB
        if (source.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
            var converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = converted.createGraphics();
            g.drawImage(source, 0, 0, null);
            g.dispose();
            source = converted;
        }

        var ulX = worldData.ulCornerX();
        var ulY = worldData.ulCornerY();
        var lrX = ulX + source.getWidth() * worldData.pixelWidth();
        var lrY = ulY + source.getHeight() * worldData.pixelHeight();

        LOG.info("Importing {}: zoom={}, pixelSize={}m, bounds=({}, {}) to ({}, {}), tiles={}x{}",
                pngFile.getFileName(), zoom, worldData.pixelWidth(),
                ulX, ulY, lrX, lrY,
                TileMatrixSet.column(lrX, zoom) - TileMatrixSet.column(ulX, zoom) + 1,
                TileMatrixSet.row(lrY, zoom) - TileMatrixSet.row(ulY, zoom) + 1);

        var start = System.currentTimeMillis();
        int tilesWritten;
        try {
            tilesWritten = TileExtractor.extract(source, zoom, ulX, ulY,
                    worldData.pixelWidth(), worldData.pixelHeight(), tileWriter::write);
        } catch (IOException e) {
            LOG.error("Error writing tiles for {}: {}", pngFile.getFileName(), e.getMessage());
            return 0;
        }

        var duration = System.currentTimeMillis() - start;
        LOG.info("Wrote {} tiles for {} in {}ms", tilesWritten, pngFile.getFileName(), duration);
        return tilesWritten;
    }

    /**
     * Imports multiple PNG files.
     *
     * @param pngFiles list of PNG file paths
     * @return total number of tiles written
     */
    public int importFiles(List<Path> pngFiles) {
        var total = 0;
        for (var pngFile : pngFiles) {
            total += importFile(pngFile);
        }
        return total;
    }

    /**
     * Deletes the layer directory tree.
     *
     * @throws IOException if deletion fails
     */
    public void truncateLayer() throws IOException {
        var layerDir = tileWriter.layerDirectory();
        if (!Files.exists(layerDir)) {
            return;
        }
        try (var walk = Files.walk(layerDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        }
    }

    /**
     * Derives the world file path from a PNG file path (same base name, .pgw extension).
     */
    static Path deriveWorldFilePath(Path pngFile) {
        var fileName = pngFile.getFileName().toString();
        var dotIndex = fileName.lastIndexOf('.');
        var baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return pngFile.resolveSibling(baseName + ".pgw");
    }
}
