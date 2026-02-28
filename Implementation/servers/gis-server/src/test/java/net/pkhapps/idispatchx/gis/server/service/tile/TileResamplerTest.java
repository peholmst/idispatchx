package net.pkhapps.idispatchx.gis.server.service.tile;

import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileResamplerTest {

    @TempDir
    Path tileDir;

    @Test
    void outputIs256x256() throws IOException {
        createSolidTile("terrain", 10, 100, 200, Color.BLUE);

        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);
        var result = resampler.resample(layer, 11, 200, 400);

        assertNotNull(result);
        var img = readImage(result);
        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());
    }

    @Test
    void resampleDiff1ExtractsCorrectQuadrant() throws IOException {
        // Create a source tile at zoom 10 where each quadrant has a distinct color
        createQuadrantTile("terrain", 10, 0, 0,
                Color.RED,    // top-left quadrant  -> subRow=0, subCol=0
                Color.GREEN,  // top-right quadrant -> subRow=0, subCol=1
                Color.BLUE,   // bottom-left        -> subRow=1, subCol=0
                Color.YELLOW  // bottom-right       -> subRow=1, subCol=1
        );

        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);

        // zoom=11, row=0, col=0 -> subRow=0, subCol=0 -> top-left (RED) quadrant
        var result = resampler.resample(layer, 11, 0, 0);
        assertNotNull(result);
        var img = readImage(result);
        // The center pixel should be approximately red
        assertApproximateColor(Color.RED, new Color(img.getRGB(128, 128)));
    }

    @Test
    void resampleDiff1ExtractsTopRightQuadrant() throws IOException {
        createQuadrantTile("terrain", 10, 0, 0,
                Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW);

        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);

        // zoom=11, row=0, col=1 -> subRow=0, subCol=1 -> top-right (GREEN) quadrant
        var result = resampler.resample(layer, 11, 0, 1);
        assertNotNull(result);
        var img = readImage(result);
        assertApproximateColor(Color.GREEN, new Color(img.getRGB(128, 128)));
    }

    @Test
    void missingSourceTileReturnsNull() {
        // No tiles on disk
        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);

        var result = resampler.resample(layer, 11, 200, 400);
        assertNull(result);
    }

    @Test
    void noAvailableSourceZoomReturnsNull() {
        // Layer exists but has no lower zoom level to resample from
        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);

        // Requesting zoom 9 when only zoom 10 is available — no lower zoom
        var result = resampler.resample(layer, 9, 0, 0);
        assertNull(result);
    }

    @Test
    void depthExceedsMaxReturnsNull() {
        // Layer has zoom 5, requesting zoom 9 (diff=4, exceeds max of 3)
        var layer = new TileLayer("terrain", Set.of(5));
        var resampler = new TileResampler(tileDir);

        var result = resampler.resample(layer, 9, 0, 0);
        assertNull(result);
    }

    @Test
    void findSourceZoomReturnsNearestLower() {
        var layer = new TileLayer("terrain", Set.of(5, 10, 14));
        var resampler = new TileResampler(tileDir);

        // Requesting zoom 11 -> nearest lower is 10
        assertEquals(10, resampler.findSourceZoom(layer, 11));

        // Requesting zoom 13 -> nearest lower is 10
        assertEquals(10, resampler.findSourceZoom(layer, 13));

        // Requesting zoom 15 -> nearest lower is 14
        assertEquals(14, resampler.findSourceZoom(layer, 15));
    }

    @Test
    void findSourceZoomReturnsMinusOneWhenNoneAvailable() {
        var layer = new TileLayer("terrain", Set.of(10));
        var resampler = new TileResampler(tileDir);

        // Requesting zoom 10 -> no lower zoom available (must use zoom-1)
        assertEquals(-1, resampler.findSourceZoom(layer, 10));

        // Requesting zoom 5 -> no lower zoom available
        assertEquals(-1, resampler.findSourceZoom(layer, 5));
    }

    @Test
    void resampleWithDiff3ProducesTile() throws IOException {
        createSolidTile("terrain", 5, 3, 7, Color.CYAN);

        var layer = new TileLayer("terrain", Set.of(5));
        var resampler = new TileResampler(tileDir);

        // diff=3: source_row=3>>3=0... wait, let me compute correctly
        // zoom=8, row=24, col=56 -> sourceRow=24>>3=3, sourceCol=56>>3=7
        var result = resampler.resample(layer, 8, 24, 56);
        assertNotNull(result);
        var img = readImage(result);
        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());
    }

    // ==================== helpers ====================

    private void createSolidTile(String layer, int zoom, int row, int col, Color color) throws IOException {
        var img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        writeTile(layer, zoom, row, col, img);
    }

    /**
     * Creates a tile where each 128x128 quadrant has a distinct solid color.
     */
    private void createQuadrantTile(String layer, int zoom, int row, int col,
                                    Color topLeft, Color topRight,
                                    Color bottomLeft, Color bottomRight) throws IOException {
        var img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();

        g.setColor(topLeft);
        g.fillRect(0, 0, 128, 128);

        g.setColor(topRight);
        g.fillRect(128, 0, 128, 128);

        g.setColor(bottomLeft);
        g.fillRect(0, 128, 128, 128);

        g.setColor(bottomRight);
        g.fillRect(128, 128, 128, 128);

        g.dispose();
        writeTile(layer, zoom, row, col, img);
    }

    private void writeTile(String layer, int zoom, int row, int col, BufferedImage img) throws IOException {
        var path = tileDir
                .resolve(layer)
                .resolve("ETRS-TM35FIN")
                .resolve(String.valueOf(zoom))
                .resolve(String.valueOf(row))
                .resolve(col + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "PNG", path.toFile());
    }

    private BufferedImage readImage(byte[] data) throws IOException {
        return ImageIO.read(new java.io.ByteArrayInputStream(data));
    }

    private void assertApproximateColor(Color expected, Color actual) {
        int tolerance = 15;
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance,
                "Red channel mismatch: expected " + expected.getRed() + " got " + actual.getRed());
        assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance,
                "Green channel mismatch: expected " + expected.getGreen() + " got " + actual.getGreen());
        assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance,
                "Blue channel mismatch: expected " + expected.getBlue() + " got " + actual.getBlue());
    }
}
