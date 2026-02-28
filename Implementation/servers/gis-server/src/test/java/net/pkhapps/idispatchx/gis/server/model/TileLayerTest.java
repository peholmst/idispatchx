package net.pkhapps.idispatchx.gis.server.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileLayerTest {

    @Test
    void validLayer_withZoomLevels() {
        var layer = new TileLayer("base", Set.of(0, 5, 10, 15));

        assertEquals("base", layer.name());
        assertEquals(Set.of(0, 5, 10, 15), layer.availableZoomLevels());
    }

    @Test
    void validLayer_emptyZoomLevels() {
        var layer = new TileLayer("empty", Set.of());

        assertEquals("empty", layer.name());
        assertTrue(layer.availableZoomLevels().isEmpty());
    }

    @Test
    void nullName_throws() {
        assertThrows(NullPointerException.class,
                () -> new TileLayer(null, Set.of(5)));
    }

    @Test
    void nullZoomLevels_throws() {
        assertThrows(NullPointerException.class,
                () -> new TileLayer("base", null));
    }

    @Test
    void blankName_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TileLayer("", Set.of(5)));

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void whitespaceOnlyName_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TileLayer("   ", Set.of(5)));

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void zoomLevelBelowRange_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TileLayer("base", Set.of(-1)));

        assertTrue(exception.getMessage().contains("zoom level"));
        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void zoomLevelAboveRange_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TileLayer("base", Set.of(16)));

        assertTrue(exception.getMessage().contains("zoom level"));
        assertTrue(exception.getMessage().contains("16"));
    }

    @Test
    void mixedValidAndInvalidZoomLevels_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TileLayer("base", Set.of(0, 5, 20)));

        assertTrue(exception.getMessage().contains("20"));
    }

    @Test
    void hasZoomLevel_existingLevel_returnsTrue() {
        var layer = new TileLayer("base", Set.of(0, 5, 10));

        assertTrue(layer.hasZoomLevel(0));
        assertTrue(layer.hasZoomLevel(5));
        assertTrue(layer.hasZoomLevel(10));
    }

    @Test
    void hasZoomLevel_nonExistingLevel_returnsFalse() {
        var layer = new TileLayer("base", Set.of(0, 5, 10));

        assertFalse(layer.hasZoomLevel(1));
        assertFalse(layer.hasZoomLevel(3));
        assertFalse(layer.hasZoomLevel(15));
    }

    @Test
    void hasZoomLevel_emptyLevels_returnsFalse() {
        var layer = new TileLayer("empty", Set.of());

        assertFalse(layer.hasZoomLevel(0));
        assertFalse(layer.hasZoomLevel(5));
    }

    @Test
    void nearestAvailableZoom_exactMatch() {
        var layer = new TileLayer("base", Set.of(0, 5, 10, 15));

        assertEquals(5, layer.nearestAvailableZoom(5));
        assertEquals(10, layer.nearestAvailableZoom(10));
    }

    @Test
    void nearestAvailableZoom_requestedHigherThanAvailable() {
        var layer = new TileLayer("base", Set.of(0, 5, 10));

        // Requesting zoom 12 should return 10 (nearest lower)
        assertEquals(10, layer.nearestAvailableZoom(12));

        // Requesting zoom 15 should return 10 (nearest lower)
        assertEquals(10, layer.nearestAvailableZoom(15));
    }

    @Test
    void nearestAvailableZoom_requestedBetweenAvailable() {
        var layer = new TileLayer("base", Set.of(0, 5, 10, 15));

        // Requesting zoom 7 should return 5 (nearest lower)
        assertEquals(5, layer.nearestAvailableZoom(7));

        // Requesting zoom 3 should return 0 (nearest lower)
        assertEquals(0, layer.nearestAvailableZoom(3));
    }

    @Test
    void nearestAvailableZoom_requestedLowerThanAllAvailable() {
        var layer = new TileLayer("base", Set.of(5, 10, 15));

        // Requesting zoom 3, no lower available
        assertEquals(-1, layer.nearestAvailableZoom(3));
    }

    @Test
    void nearestAvailableZoom_emptyLevels_returnsNegativeOne() {
        var layer = new TileLayer("empty", Set.of());

        assertEquals(-1, layer.nearestAvailableZoom(5));
    }

    @Test
    void nearestAvailableZoom_singleLevel() {
        var layer = new TileLayer("single", Set.of(10));

        assertEquals(10, layer.nearestAvailableZoom(10));
        assertEquals(10, layer.nearestAvailableZoom(15));
        assertEquals(-1, layer.nearestAvailableZoom(5));
    }

    @Test
    void availableZoomLevels_immutable() {
        var mutableSet = new HashSet<>(Set.of(5, 10));
        var layer = new TileLayer("base", mutableSet);

        // Modify original set
        mutableSet.add(15);

        // Layer should not be affected
        assertEquals(Set.of(5, 10), layer.availableZoomLevels());
    }

    @Test
    void availableZoomLevels_cannotModify() {
        var layer = new TileLayer("base", Set.of(5, 10));

        assertThrows(UnsupportedOperationException.class,
                () -> layer.availableZoomLevels().add(15));
    }

    @Test
    void equals_sameValues() {
        var layer1 = new TileLayer("base", Set.of(5, 10));
        var layer2 = new TileLayer("base", Set.of(5, 10));

        assertEquals(layer1, layer2);
        assertEquals(layer1.hashCode(), layer2.hashCode());
    }

    @Test
    void equals_differentName() {
        var layer1 = new TileLayer("base", Set.of(5, 10));
        var layer2 = new TileLayer("other", Set.of(5, 10));

        assertNotEquals(layer1, layer2);
    }

    @Test
    void equals_differentZoomLevels() {
        var layer1 = new TileLayer("base", Set.of(5, 10));
        var layer2 = new TileLayer("base", Set.of(5, 15));

        assertNotEquals(layer1, layer2);
    }
}
