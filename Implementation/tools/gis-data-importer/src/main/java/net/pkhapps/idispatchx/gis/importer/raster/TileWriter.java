package net.pkhapps.idispatchx.gis.importer.raster;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes extracted tiles to the filesystem in WMTS-compatible directory structure.
 * <p>
 * Path format: {@code baseDir/layer/ETRS-TM35FIN/{zoom}/{row}/{col}.png}
 * <p>
 * When a tile file already exists, the new tile is composited on top using
 * {@link AlphaComposite#SrcOver}, allowing multiple source images to contribute
 * to the same tile.
 */
public final class TileWriter {

    private final Path baseDir;
    private final String layer;

    /**
     * Creates a new tile writer.
     *
     * @param baseDir base directory for tile storage
     * @param layer   layer name (e.g., "terrain")
     */
    public TileWriter(Path baseDir, String layer) {
        this.baseDir = baseDir;
        this.layer = layer;
    }

    /**
     * Writes the given tile to the filesystem. If a tile already exists at the same
     * location, the new tile is composited on top of the existing one.
     *
     * @param tile the extracted tile to write
     * @throws IOException if writing fails
     */
    public void write(TileExtractor.ExtractedTile tile) throws IOException {
        var coord = tile.coordinate();
        var tilePath = baseDir
                .resolve(layer)
                .resolve(TileMatrixSet.IDENTIFIER)
                .resolve(String.valueOf(coord.zoom()))
                .resolve(String.valueOf(coord.row()))
                .resolve(coord.col() + ".png");

        Files.createDirectories(tilePath.getParent());

        BufferedImage output;
        if (Files.exists(tilePath)) {
            var existing = ImageIO.read(tilePath.toFile());
            output = new BufferedImage(TileMatrixSet.TILE_SIZE, TileMatrixSet.TILE_SIZE,
                    BufferedImage.TYPE_INT_ARGB);
            var g = output.createGraphics();
            try {
                g.drawImage(existing, 0, 0, null);
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(tile.image(), 0, 0, null);
            } finally {
                g.dispose();
            }
        } else {
            output = tile.image();
        }

        ImageIO.write(output, "png", tilePath.toFile());
    }

    /**
     * Returns the layer directory path ({@code baseDir/layer}).
     *
     * @return the layer directory
     */
    public Path layerDirectory() {
        return baseDir.resolve(layer);
    }
}
