package net.pkhapps.idispatchx.gis.server.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TileCoordinatesTest {

    @Test
    void validCoordinates_zoom0() {
        var coords = TileCoordinates.of(0, 0, 0);

        assertEquals(0, coords.zoom());
        assertEquals(0, coords.row());
        assertEquals(0, coords.col());
    }

    @Test
    void validCoordinates_zoom1() {
        // At zoom 1, matrix is 2x2, so valid range is 0-1
        var coords = TileCoordinates.of(1, 1, 1);

        assertEquals(1, coords.zoom());
        assertEquals(1, coords.row());
        assertEquals(1, coords.col());
    }

    @Test
    void validCoordinates_zoom15_maxValues() {
        // At zoom 15, matrix is 32768x32768
        var maxIndex = 32767;
        var coords = TileCoordinates.of(15, maxIndex, maxIndex);

        assertEquals(15, coords.zoom());
        assertEquals(maxIndex, coords.row());
        assertEquals(maxIndex, coords.col());
    }

    @Test
    void validCoordinates_zoom15_minValues() {
        var coords = TileCoordinates.of(15, 0, 0);

        assertEquals(15, coords.zoom());
        assertEquals(0, coords.row());
        assertEquals(0, coords.col());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 5, 10, 15})
    void validZoomLevels(int zoom) {
        assertDoesNotThrow(() -> TileCoordinates.of(zoom, 0, 0));
    }

    @Test
    void zoomTooLow_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(-1, 0, 0));

        assertTrue(exception.getMessage().contains("zoom"));
        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void zoomTooHigh_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(16, 0, 0));

        assertTrue(exception.getMessage().contains("zoom"));
        assertTrue(exception.getMessage().contains("16"));
    }

    @Test
    void negativeRow_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(5, -1, 0));

        assertTrue(exception.getMessage().contains("row"));
        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    void negativeCol_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(5, 0, -1));

        assertTrue(exception.getMessage().contains("col"));
        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    void rowExceedsMatrixBounds_zoom0_throws() {
        // At zoom 0, matrix is 1x1, so only valid row is 0
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(0, 1, 0));

        assertTrue(exception.getMessage().contains("row"));
        assertTrue(exception.getMessage().contains("less than 1"));
    }

    @Test
    void colExceedsMatrixBounds_zoom0_throws() {
        // At zoom 0, matrix is 1x1, so only valid col is 0
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(0, 0, 1));

        assertTrue(exception.getMessage().contains("col"));
        assertTrue(exception.getMessage().contains("less than 1"));
    }

    @Test
    void rowExceedsMatrixBounds_zoom5_throws() {
        // At zoom 5, matrix is 32x32, so valid range is 0-31
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(5, 32, 0));

        assertTrue(exception.getMessage().contains("row"));
        assertTrue(exception.getMessage().contains("less than 32"));
    }

    @Test
    void colExceedsMatrixBounds_zoom5_throws() {
        // At zoom 5, matrix is 32x32, so valid range is 0-31
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(5, 0, 32));

        assertTrue(exception.getMessage().contains("col"));
        assertTrue(exception.getMessage().contains("less than 32"));
    }

    @Test
    void rowExceedsMatrixBounds_zoom15_throws() {
        // At zoom 15, matrix is 32768x32768
        var exception = assertThrows(IllegalArgumentException.class,
                () -> TileCoordinates.of(15, 32768, 0));

        assertTrue(exception.getMessage().contains("row"));
        assertTrue(exception.getMessage().contains("less than 32768"));
    }

    @Test
    void matrixDimensionForZoom_zoom0() {
        assertEquals(1, TileCoordinates.matrixDimensionForZoom(0));
    }

    @Test
    void matrixDimensionForZoom_zoom1() {
        assertEquals(2, TileCoordinates.matrixDimensionForZoom(1));
    }

    @Test
    void matrixDimensionForZoom_zoom2() {
        assertEquals(4, TileCoordinates.matrixDimensionForZoom(2));
    }

    @Test
    void matrixDimensionForZoom_zoom10() {
        assertEquals(1024, TileCoordinates.matrixDimensionForZoom(10));
    }

    @Test
    void matrixDimensionForZoom_zoom15() {
        assertEquals(32768, TileCoordinates.matrixDimensionForZoom(15));
    }

    @Test
    void staticFactoryMethod_sameAsConstructor() {
        var fromFactory = TileCoordinates.of(5, 10, 15);
        var fromConstructor = new TileCoordinates(5, 10, 15);

        assertEquals(fromConstructor, fromFactory);
    }

    @Test
    void equals_sameValues() {
        var coords1 = TileCoordinates.of(5, 10, 15);
        var coords2 = TileCoordinates.of(5, 10, 15);

        assertEquals(coords1, coords2);
        assertEquals(coords1.hashCode(), coords2.hashCode());
    }

    @Test
    void equals_differentValues() {
        var coords1 = TileCoordinates.of(5, 10, 15);
        var coords2 = TileCoordinates.of(5, 10, 16);

        assertNotEquals(coords1, coords2);
    }
}
