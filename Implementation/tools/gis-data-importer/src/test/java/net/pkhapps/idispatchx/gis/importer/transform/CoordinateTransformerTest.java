package net.pkhapps.idispatchx.gis.importer.transform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateTransformerTest {

    private final CoordinateTransformer transformer = new CoordinateTransformer();

    @Test
    void transformPoint_knownCoordinate_producesCorrectResult() {
        // Helsinki area: EPSG:3067 (385000, 6672000) should be roughly lat ~60.17, lon ~24.94
        var result = transformer.transformPoint(385000, 6672000);
        assertEquals(60.17, result[0], 0.1);
        assertEquals(24.94, result[1], 0.1);
    }

    @Test
    void transformPoint_sampleDataCoordinate_withinFinlandBounds() {
        // From sample data: Kuggö area in Parainen/Turku archipelago
        var result = transformer.transformPoint(231221.828, 6677931.943);
        assertTrue(result[0] >= 58.84 && result[0] <= 70.09, "Latitude should be within Finland bounds");
        assertTrue(result[1] >= 19.08 && result[1] <= 31.59, "Longitude should be within Finland bounds");
    }

    @Test
    void transformPoint_sampleDataCoordinate_approximateLocation() {
        // From sample data: Kuggö address point
        var result = transformer.transformPoint(231221.828, 6677931.943);
        // Should be in the Turku archipelago area: roughly lat ~60.1, lon ~22.2
        assertEquals(60.1, result[0], 0.2, "Latitude should be near 60.1");
        assertEquals(22.2, result[1], 0.2, "Longitude should be near 22.2");
    }

    @Test
    void transformLineString_twoPoints_transformsBoth() {
        var coords = new double[][]{
                {232056.555, 6678000.000},
                {232100.000, 6678050.000}
        };
        var result = transformer.transformLineString(coords);
        assertEquals(2, result.length);
        for (var point : result) {
            assertEquals(2, point.length);
            assertTrue(point[0] >= 58.84 && point[0] <= 70.09);
            assertTrue(point[1] >= 19.08 && point[1] <= 31.59);
        }
    }

    @Test
    void transformPolygon_closedRing_transformsAll() {
        var coords = new double[][]{
                {234265.396, 6669060.097},
                {231796.382, 6666000.000},
                {232000.000, 6666000.000},
                {234265.396, 6669060.097}
        };
        var result = transformer.transformPolygon(coords);
        assertEquals(4, result.length);
        for (var point : result) {
            assertTrue(point[0] >= 58.84 && point[0] <= 70.09);
            assertTrue(point[1] >= 19.08 && point[1] <= 31.59);
        }
    }

    @Test
    void transformPoint_outsideFinland_throws() {
        // Coordinates way outside Finland (origin area)
        assertThrows(IllegalArgumentException.class,
                () -> transformer.transformPoint(0, 0));
    }
}
