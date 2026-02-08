package net.pkhapps.idispatchx.gis.importer.raster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TileWriterTest {

    @TempDir
    Path tempDir;

    private BufferedImage createSolidTile(Color color) {
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        return image;
    }

    // === Path construction ===

    @Test
    void write_createsCorrectDirectoryStructure() throws IOException {
        var writer = new TileWriter(tempDir, "terrain");
        var tile = new TileExtractor.ExtractedTile(
                new TileMatrixSet.TileCoordinate(14, 13364, 6035),
                createSolidTile(Color.RED));

        writer.write(tile);

        var expected = tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6035.png");
        assertTrue(Files.exists(expected));
    }

    @Test
    void write_createsValidPngFile() throws IOException {
        var writer = new TileWriter(tempDir, "terrain");
        var tile = new TileExtractor.ExtractedTile(
                new TileMatrixSet.TileCoordinate(14, 13364, 6035),
                createSolidTile(Color.RED));

        writer.write(tile);

        var tilePath = tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6035.png");
        var readBack = ImageIO.read(tilePath.toFile());
        assertNotNull(readBack);
        assertEquals(256, readBack.getWidth());
        assertEquals(256, readBack.getHeight());
    }

    @Test
    void write_pngRoundTrip_preservesColor() throws IOException {
        var writer = new TileWriter(tempDir, "terrain");
        var tile = new TileExtractor.ExtractedTile(
                new TileMatrixSet.TileCoordinate(14, 13364, 6035),
                createSolidTile(Color.RED));

        writer.write(tile);

        var tilePath = tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6035.png");
        var readBack = ImageIO.read(tilePath.toFile());
        // Check center pixel is red (fully opaque)
        var pixel = readBack.getRGB(128, 128);
        assertEquals(0xFFFF0000, pixel);
    }

    // === Compositing ===

    @Test
    void write_existingTile_compositesOnTop() throws IOException {
        var writer = new TileWriter(tempDir, "terrain");
        var coord = new TileMatrixSet.TileCoordinate(14, 13364, 6035);

        // First tile: red in left half
        var first = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g1 = first.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 128, 256);
        g1.dispose();

        // Second tile: blue in right half
        var second = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g2 = second.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(128, 0, 128, 256);
        g2.dispose();

        writer.write(new TileExtractor.ExtractedTile(coord, first));
        writer.write(new TileExtractor.ExtractedTile(coord, second));

        var tilePath = tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6035.png");
        var readBack = ImageIO.read(tilePath.toFile());

        // Left half should be red
        var leftPixel = readBack.getRGB(64, 128);
        assertEquals(0xFFFF0000, leftPixel);

        // Right half should be blue
        var rightPixel = readBack.getRGB(192, 128);
        assertEquals(0xFF0000FF, rightPixel);
    }

    // === Layer directory ===

    @Test
    void layerDirectory_returnsCorrectPath() {
        var writer = new TileWriter(tempDir, "terrain");
        assertEquals(tempDir.resolve("terrain"), writer.layerDirectory());
    }

    // === Multiple tiles ===

    @Test
    void write_multipleTiles_createsMultipleFiles() throws IOException {
        var writer = new TileWriter(tempDir, "terrain");

        writer.write(new TileExtractor.ExtractedTile(
                new TileMatrixSet.TileCoordinate(14, 13364, 6035),
                createSolidTile(Color.RED)));
        writer.write(new TileExtractor.ExtractedTile(
                new TileMatrixSet.TileCoordinate(14, 13364, 6036),
                createSolidTile(Color.BLUE)));

        assertTrue(Files.exists(tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6035.png")));
        assertTrue(Files.exists(tempDir.resolve("terrain/ETRS-TM35FIN/14/13364/6036.png")));
    }
}
