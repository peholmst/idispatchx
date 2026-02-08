package net.pkhapps.idispatchx.gis.importer.raster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldFileParserTest {

    @TempDir
    Path tempDir;

    private Path createWorldFile(String... lines) throws IOException {
        var file = tempDir.resolve("test.pgw");
        Files.writeString(file, String.join("\n", lines) + "\n");
        return file;
    }

    // === Happy path ===

    @Test
    void parse_sampleData_returnsCorrectCornerCoordinates() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.0", "-0.5", "224000.25", "6677999.75");
        var data = WorldFileParser.parse(file);

        assertEquals(0.5, data.pixelWidth());
        assertEquals(-0.5, data.pixelHeight());
        assertEquals(224000.0, data.ulCornerX(), 1e-9);
        assertEquals(6678000.0, data.ulCornerY(), 1e-9);
    }

    @Test
    void parse_whitespaceInLines_trimsAndParses() throws IOException {
        var file = createWorldFile("  0.5 ", " 0.0 ", " 0.0 ", " -0.5 ", " 224000.25 ", " 6677999.75 ");
        var data = WorldFileParser.parse(file);

        assertEquals(0.5, data.pixelWidth());
        assertEquals(224000.0, data.ulCornerX(), 1e-9);
    }

    @Test
    void parse_extraLines_ignoresExtraLines() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.0", "-0.5", "224000.25", "6677999.75", "extra", "lines");
        var data = WorldFileParser.parse(file);

        assertEquals(0.5, data.pixelWidth());
    }

    // === Validation failures ===

    @Test
    void parse_tooFewLines_throwsException() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.0", "-0.5", "224000.25");
        assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
    }

    @Test
    void parse_nonZeroRotationY_throwsException() throws IOException {
        var file = createWorldFile("0.5", "0.1", "0.0", "-0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Rotation Y"));
    }

    @Test
    void parse_nonZeroRotationX_throwsException() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.1", "-0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Rotation X"));
    }

    @Test
    void parse_negativePixelWidth_throwsException() throws IOException {
        var file = createWorldFile("-0.5", "0.0", "0.0", "-0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Pixel width"));
    }

    @Test
    void parse_zeroPixelWidth_throwsException() throws IOException {
        var file = createWorldFile("0.0", "0.0", "0.0", "-0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Pixel width"));
    }

    @Test
    void parse_positivePixelHeight_throwsException() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.0", "0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Pixel height"));
    }

    @Test
    void parse_nonSquarePixels_throwsException() throws IOException {
        var file = createWorldFile("0.5", "0.0", "0.0", "-1.0", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("square"));
    }

    @Test
    void parse_eastingOutOfBounds_throwsException() throws IOException {
        // Easting way too small
        var file = createWorldFile("0.5", "0.0", "0.0", "-0.5", "0.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("easting"));
    }

    @Test
    void parse_northingOutOfBounds_throwsException() throws IOException {
        // Northing way too small
        var file = createWorldFile("0.5", "0.0", "0.0", "-0.5", "224000.25", "100000.25");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("northing"));
    }

    @Test
    void parse_nonNumericValue_throwsException() throws IOException {
        var file = createWorldFile("abc", "0.0", "0.0", "-0.5", "224000.25", "6677999.75");
        var ex = assertThrows(IllegalArgumentException.class, () -> WorldFileParser.parse(file));
        assertTrue(ex.getMessage().contains("Cannot parse"));
    }
}
