package net.pkhapps.idispatchx.gis.importer.raster;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TileExtractorTest {

    /**
     * Creates a solid-color ARGB image of the given size.
     */
    private BufferedImage createSolidImage(int width, int height, Color color) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    // === Basic extraction ===

    @Test
    void extract_singleTileSizedImage_producesOneTile() throws IOException {
        // 256x256 image at zoom 14 (pixelSize=0.5) covers exactly one tile (128m x 128m)
        // Place it at a tile boundary
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = createSolidImage(256, 256, Color.RED);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        var count = TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        assertEquals(1, count);
        assertEquals(1, tiles.size());
        assertEquals(14, tiles.getFirst().coordinate().zoom());
        assertEquals(13364, tiles.getFirst().coordinate().row());
        assertEquals(6035, tiles.getFirst().coordinate().col());
    }

    @Test
    void extract_singleTileSizedImage_tileIs256x256ARGB() throws IOException {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = createSolidImage(256, 256, Color.BLUE);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        var tile = tiles.getFirst().image();
        assertEquals(256, tile.getWidth());
        assertEquals(256, tile.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, tile.getType());
    }

    @Test
    void extract_twoByTwoTiles_producesFourTiles() throws IOException {
        // 512x512 image at zoom 14 = 2x2 tiles
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = createSolidImage(512, 512, Color.GREEN);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        var count = TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        assertEquals(4, count);
        assertEquals(4, tiles.size());
    }

    @Test
    void extract_twoByTwoTiles_correctCoordinates() throws IOException {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = createSolidImage(512, 512, Color.GREEN);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        // Should produce tiles at (13364,6035), (13364,6036), (13365,6035), (13365,6036)
        var coords = tiles.stream()
                .map(t -> t.coordinate().row() + "," + t.coordinate().col())
                .sorted()
                .toList();
        assertEquals(4, coords.size());
        assertTrue(coords.contains("13364,6035"));
        assertTrue(coords.contains("13364,6036"));
        assertTrue(coords.contains("13365,6035"));
        assertTrue(coords.contains("13365,6036"));
    }

    // === Transparency skipping ===

    @Test
    void extract_fullyTransparentImage_producesZeroTiles() throws IOException {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        // Create a fully transparent image
        var source = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        var count = TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        assertEquals(0, count);
        assertEquals(0, tiles.size());
    }

    @Test
    void extract_partiallyOpaqueImage_producesOnlyNonEmptyTiles() throws IOException {
        // 512x512 image, but only the top-left 256x256 quadrant has content
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        var g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 256, 256); // Only top-left quadrant
        g.dispose();

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        var count = TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        assertEquals(1, count);
        assertEquals(13364, tiles.getFirst().coordinate().row());
        assertEquals(6035, tiles.getFirst().coordinate().col());
    }

    // === Return value ===

    @Test
    void extract_returnValueMatchesTileCount() throws IOException {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        var source = createSolidImage(512, 512, Color.RED);

        var tiles = new ArrayList<TileExtractor.ExtractedTile>();
        var count = TileExtractor.extract(source, 14, bounds.west(), bounds.north(), 0.5, -0.5, tiles::add);

        assertEquals(tiles.size(), count);
    }
}
