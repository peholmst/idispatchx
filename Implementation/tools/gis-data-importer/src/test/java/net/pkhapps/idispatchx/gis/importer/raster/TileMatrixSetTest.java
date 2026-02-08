package net.pkhapps.idispatchx.gis.importer.raster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileMatrixSetTest {

    // === pixelSize tests ===

    @Test
    void pixelSize_zoom0_returns8192() {
        assertEquals(8192.0, TileMatrixSet.pixelSize(0));
    }

    @Test
    void pixelSize_zoom14_returns0point5() {
        assertEquals(0.5, TileMatrixSet.pixelSize(14));
    }

    @Test
    void pixelSize_zoom15_returns0point25() {
        assertEquals(0.25, TileMatrixSet.pixelSize(15));
    }

    @Test
    void pixelSize_negativeZoom_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TileMatrixSet.pixelSize(-1));
    }

    @Test
    void pixelSize_zoom16_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TileMatrixSet.pixelSize(16));
    }

    // === tileSpan tests ===

    @Test
    void tileSpan_zoom14_returns128() {
        assertEquals(128.0, TileMatrixSet.tileSpan(14));
    }

    @Test
    void tileSpan_zoom0_returns2097152() {
        assertEquals(2_097_152.0, TileMatrixSet.tileSpan(0));
    }

    // === column tests ===

    @Test
    void column_sampleDataEasting_returns6035() {
        assertEquals(6035, TileMatrixSet.column(224000.0, 14));
    }

    // === row tests ===

    @Test
    void row_sampleDataNorthing_returns13364() {
        assertEquals(13364, TileMatrixSet.row(6678000.0, 14));
    }

    // === tileBounds tests ===

    @Test
    void tileBounds_zoom14_row13364_col6035_correctBounds() {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        assertEquals(TileMatrixSet.ORIGIN_X + 6035 * 128.0, bounds.west(), 1e-9);
        assertEquals(TileMatrixSet.ORIGIN_Y - 13364 * 128.0, bounds.north(), 1e-9);
        assertEquals(bounds.west() + 128.0, bounds.east(), 1e-9);
        assertEquals(bounds.north() - 128.0, bounds.south(), 1e-9);
    }

    @Test
    void tileBounds_inverseOfColumnAndRow() {
        var bounds = TileMatrixSet.tileBounds(14, 13364, 6035);
        // The tile's west edge should map back to col 6035
        assertEquals(6035, TileMatrixSet.column(bounds.west(), 14));
        // The tile's north edge should map back to row 13364
        assertEquals(13364, TileMatrixSet.row(bounds.north(), 14));
    }

    // === zoomLevel tests ===

    @Test
    void zoomLevel_0point5_returns14() {
        assertEquals(14, TileMatrixSet.zoomLevel(0.5));
    }

    @Test
    void zoomLevel_1point0_returns13() {
        assertEquals(13, TileMatrixSet.zoomLevel(1.0));
    }

    @Test
    void zoomLevel_8192_returns0() {
        assertEquals(0, TileMatrixSet.zoomLevel(8192.0));
    }

    @Test
    void zoomLevel_0point25_returns15() {
        assertEquals(15, TileMatrixSet.zoomLevel(0.25));
    }

    @Test
    void zoomLevel_invalidPixelWidth_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TileMatrixSet.zoomLevel(0.3));
    }

    @Test
    void zoomLevel_tooSmallPixelWidth_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TileMatrixSet.zoomLevel(0.1));
    }

    @Test
    void zoomLevel_tooLargePixelWidth_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TileMatrixSet.zoomLevel(20000.0));
    }

    // === constants tests ===

    @Test
    void constants_haveExpectedValues() {
        assertEquals(256, TileMatrixSet.TILE_SIZE);
        assertEquals(-548576.0, TileMatrixSet.ORIGIN_X);
        assertEquals(8388608.0, TileMatrixSet.ORIGIN_Y);
        assertEquals(0, TileMatrixSet.MIN_ZOOM);
        assertEquals(15, TileMatrixSet.MAX_ZOOM);
        assertEquals("ETRS-TM35FIN", TileMatrixSet.IDENTIFIER);
    }
}
