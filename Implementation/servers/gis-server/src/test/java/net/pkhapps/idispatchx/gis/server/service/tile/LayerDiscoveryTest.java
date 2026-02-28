package net.pkhapps.idispatchx.gis.server.service.tile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LayerDiscoveryTest {

    @TempDir
    Path tileDir;

    @Test
    void emptyDirectoryReturnsNoLayers() {
        var discovery = new LayerDiscovery(tileDir);
        assertTrue(discovery.discoverLayers().isEmpty());
    }

    @Test
    void nonExistentDirectoryReturnsNoLayers() {
        var discovery = new LayerDiscovery(tileDir.resolve("nonexistent"));
        assertTrue(discovery.discoverLayers().isEmpty());
    }

    @Test
    void discoversLayerWithZoomLevels() throws IOException {
        createZoomDir("terrain", 5);
        createZoomDir("terrain", 10);
        createZoomDir("terrain", 14);

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertTrue(layers.containsKey("terrain"));
        var layer = layers.get("terrain");
        assertEquals("terrain", layer.name());
        assertEquals(3, layer.availableZoomLevels().size());
        assertTrue(layer.availableZoomLevels().containsAll(java.util.Set.of(5, 10, 14)));
    }

    @Test
    void discoversMultipleLayers() throws IOException {
        createZoomDir("terrain", 10);
        createZoomDir("roads", 12);
        createZoomDir("aerial", 8);

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertTrue(layers.containsKey("terrain"));
        assertTrue(layers.containsKey("roads"));
        assertTrue(layers.containsKey("aerial"));
    }

    @Test
    void skipsLayerWithoutEtrsTm35FinDirectory() throws IOException {
        // Create layer directory without ETRS-TM35FIN subdirectory
        Files.createDirectories(tileDir.resolve("terrain").resolve("OTHER_CRS").resolve("10"));

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertFalse(layers.containsKey("terrain"));
    }

    @Test
    void skipsNonNumericZoomDirectories() throws IOException {
        createZoomDir("terrain", 10);
        // Add a non-numeric directory
        Files.createDirectories(tileDir.resolve("terrain").resolve("ETRS-TM35FIN").resolve("not_a_number"));

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertTrue(layers.containsKey("terrain"));
        assertEquals(java.util.Set.of(10), layers.get("terrain").availableZoomLevels());
    }

    @Test
    void skipsZoomLevelsOutOfRange() throws IOException {
        createZoomDir("terrain", 0);
        createZoomDir("terrain", 15);
        // Out of range values - won't be valid dir names for int range, but test 16
        Files.createDirectories(tileDir.resolve("terrain").resolve("ETRS-TM35FIN").resolve("16"));

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertTrue(layers.containsKey("terrain"));
        assertTrue(layers.get("terrain").availableZoomLevels().containsAll(java.util.Set.of(0, 15)));
        assertFalse(layers.get("terrain").availableZoomLevels().contains(16));
    }

    @Test
    void skipsLayerWithNoValidZoomLevels() throws IOException {
        // Create layer dir with ETRS-TM35FIN but only invalid zoom dirs
        Files.createDirectories(tileDir.resolve("terrain").resolve("ETRS-TM35FIN").resolve("invalid"));

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertFalse(layers.containsKey("terrain"));
    }

    @Test
    void allValidZoomLevelsDiscovered() throws IOException {
        for (int z = 0; z <= 15; z++) {
            createZoomDir("terrain", z);
        }

        var discovery = new LayerDiscovery(tileDir);
        var layers = discovery.discoverLayers();

        assertTrue(layers.containsKey("terrain"));
        assertEquals(16, layers.get("terrain").availableZoomLevels().size());
    }

    private void createZoomDir(String layerName, int zoom) throws IOException {
        Files.createDirectories(
                tileDir.resolve(layerName)
                        .resolve("ETRS-TM35FIN")
                        .resolve(String.valueOf(zoom))
        );
    }
}
