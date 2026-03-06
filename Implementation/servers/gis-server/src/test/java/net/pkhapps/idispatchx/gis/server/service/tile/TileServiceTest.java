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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileServiceTest {

    @TempDir
    Path tileDir;

    @Test
    void unknownLayerThrowsIllegalArgumentException() {
        var layers = Map.<String, TileLayer>of();
        var service = new TileService(tileDir, layers, new TileResampler(tileDir), new TileCache());

        assertThrows(IllegalArgumentException.class,
                () -> service.getTile("nonexistent", 10, 0, 0));
    }

    @Test
    void preRenderedTileIsReturnedWhenAvailable() throws IOException {
        createTile("terrain", 10, 100, 200);

        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var service = new TileService(tileDir, layers, new TileResampler(tileDir), new TileCache());

        var result = service.getTile("terrain", 10, 100, 200);
        assertInstanceOf(TileService.TileResult.PreRendered.class, result);
        assertNotNull(((TileService.TileResult.PreRendered) result).data());
        assertTrue(((TileService.TileResult.PreRendered) result).data().length > 0);
    }

    @Test
    void tileNotFoundThrowsTileNotFoundException() {
        // Layer exists but no tile file on disk and no resampling possible
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var service = new TileService(tileDir, layers, new TileResampler(tileDir), new TileCache());

        assertThrows(TileService.TileNotFoundException.class,
                () -> service.getTile("terrain", 10, 100, 200));
    }

    @Test
    void resampledTileIsReturnedWhenPreRenderedMissing() throws IOException {
        // Create source tile at zoom 10
        createTile("terrain", 10, 50, 100);

        var layer = new TileLayer("terrain", Set.of(10));
        var service = new TileService(tileDir, Map.of("terrain", layer),
                new TileResampler(tileDir), new TileCache());

        // Request zoom 11 (no pre-rendered tile, resampled from zoom 10)
        var result = service.getTile("terrain", 11, 100, 200);
        assertInstanceOf(TileService.TileResult.Resampled.class, result);
    }

    @Test
    void cacheMissThenHitForResampledTile() throws IOException {
        createTile("terrain", 10, 50, 100);

        var layer = new TileLayer("terrain", Set.of(10));
        var cache = new TileCache();
        var service = new TileService(tileDir, Map.of("terrain", layer),
                new TileResampler(tileDir), cache);

        // First call — cache miss, resamples and stores
        service.getTile("terrain", 11, 100, 200);
        assertEquals(0, cache.getHits());
        assertEquals(1, cache.getMisses());

        // Second call — cache hit
        service.getTile("terrain", 11, 100, 200);
        assertEquals(1, cache.getHits());
        assertEquals(1, cache.getMisses());
    }

    @Test
    void getLayersReturnsKnownLayers() {
        var layer = new TileLayer("terrain", Set.of(10));
        var layers = Map.of("terrain", layer);
        var service = new TileService(tileDir, layers, new TileResampler(tileDir), new TileCache());

        assertEquals(Map.of("terrain", layer), service.getLayers());
    }

    // ==================== helpers ====================

    private void createTile(String layer, int zoom, int row, int col) throws IOException {
        var img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 256, 256);
        g.dispose();

        var path = tileDir
                .resolve(layer)
                .resolve("ETRS-TM35FIN")
                .resolve(String.valueOf(zoom))
                .resolve(String.valueOf(row))
                .resolve(col + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "PNG", path.toFile());
    }
}
