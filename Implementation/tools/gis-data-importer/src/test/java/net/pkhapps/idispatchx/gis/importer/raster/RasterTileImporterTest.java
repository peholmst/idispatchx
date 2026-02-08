package net.pkhapps.idispatchx.gis.importer.raster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RasterTileImporterTest {

    @TempDir
    Path inputDir;

    @TempDir
    Path outputDir;

    /**
     * Creates a synthetic PNG and world file in the input directory.
     * The image is placed at the given tile boundary at zoom 14.
     */
    private Path createTestSource(String baseName, int width, int height, Color color,
                                  double ulCenterX, double ulCenterY) throws IOException {
        var pngPath = inputDir.resolve(baseName + ".png");
        var pgwPath = inputDir.resolve(baseName + ".pgw");

        // Create ARGB image
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ImageIO.write(image, "png", pngPath.toFile());

        // Create world file (0.5m pixel, center of first pixel)
        Files.writeString(pgwPath,
                "0.5\n0.0\n0.0\n-0.5\n" + ulCenterX + "\n" + ulCenterY + "\n");

        return pngPath;
    }

    // === importFile ===

    @Test
    void importFile_validSource_writesTiles() throws IOException {
        // Place at a known tile boundary: corner at (224000, 6678000), center at (224000.25, 6677999.75)
        var pngFile = createTestSource("test", 256, 256, Color.RED, 224000.25, 6677999.75);
        var importer = new RasterTileImporter(outputDir, "terrain");

        var count = importer.importFile(pngFile);

        assertTrue(count > 0);
        assertTrue(Files.exists(outputDir.resolve("terrain/ETRS-TM35FIN/14")));
    }

    @Test
    void importFile_missingWorldFile_returnsZero() throws IOException {
        // Create PNG without world file
        var pngPath = inputDir.resolve("noworld.png");
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", pngPath.toFile());

        var importer = new RasterTileImporter(outputDir, "terrain");
        assertEquals(0, importer.importFile(pngPath));
    }

    @Test
    void importFile_invalidWorldFile_returnsZero() throws IOException {
        var pngPath = inputDir.resolve("bad.png");
        var pgwPath = inputDir.resolve("bad.pgw");

        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", pngPath.toFile());
        Files.writeString(pgwPath, "invalid\n0.0\n0.0\n-0.5\n224000.25\n6677999.75\n");

        var importer = new RasterTileImporter(outputDir, "terrain");
        assertEquals(0, importer.importFile(pngPath));
    }

    // === importFiles ===

    @Test
    void importFiles_multipleFiles_returnsTotalCount() throws IOException {
        var file1 = createTestSource("test1", 256, 256, Color.RED, 224000.25, 6677999.75);
        var file2 = createTestSource("test2", 256, 256, Color.BLUE, 224128.25, 6677999.75);

        var importer = new RasterTileImporter(outputDir, "terrain");
        var total = importer.importFiles(List.of(file1, file2));

        assertTrue(total >= 2);
    }

    // === truncateLayer ===

    @Test
    void truncateLayer_existingLayer_deletesDirectory() throws IOException {
        var pngFile = createTestSource("test", 256, 256, Color.RED, 224000.25, 6677999.75);
        var importer = new RasterTileImporter(outputDir, "terrain");
        importer.importFile(pngFile);

        assertTrue(Files.exists(outputDir.resolve("terrain")));
        importer.truncateLayer();
        assertFalse(Files.exists(outputDir.resolve("terrain")));
    }

    @Test
    void truncateLayer_nonExistentLayer_doesNotThrow() throws IOException {
        var importer = new RasterTileImporter(outputDir, "terrain");
        assertDoesNotThrow(importer::truncateLayer);
    }

    // === deriveWorldFilePath ===

    @Test
    void deriveWorldFilePath_pngExtension_replaceWithPgw() {
        var result = RasterTileImporter.deriveWorldFilePath(Path.of("/data/L3311F.png"));
        assertEquals(Path.of("/data/L3311F.pgw"), result);
    }

    @Test
    void deriveWorldFilePath_upperCaseExtension_replaceWithPgw() {
        var result = RasterTileImporter.deriveWorldFilePath(Path.of("/data/L3311F.PNG"));
        assertEquals(Path.of("/data/L3311F.pgw"), result);
    }

    // === Indexed color conversion ===

    @Test
    void importFile_indexedColorPng_convertsAndImports() throws IOException {
        var pngPath = inputDir.resolve("indexed.png");
        var pgwPath = inputDir.resolve("indexed.pgw");

        // Create an indexed color image
        var indexed = new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_INDEXED);
        var g = indexed.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ImageIO.write(indexed, "png", pngPath.toFile());

        Files.writeString(pgwPath, "0.5\n0.0\n0.0\n-0.5\n224000.25\n6677999.75\n");

        var importer = new RasterTileImporter(outputDir, "terrain");
        var count = importer.importFile(pngPath);

        assertTrue(count > 0);
    }
}
