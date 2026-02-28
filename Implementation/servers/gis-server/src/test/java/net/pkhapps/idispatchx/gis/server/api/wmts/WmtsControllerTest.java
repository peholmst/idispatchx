package net.pkhapps.idispatchx.gis.server.api.wmts;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import net.pkhapps.idispatchx.gis.server.service.tile.TileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmtsControllerTest {

    @Mock
    private TileService tileService;

    @Mock
    private CapabilitiesGenerator capabilitiesGenerator;

    @Mock
    private Context ctx;

    private WmtsController controller;

    @BeforeEach
    void setUp() {
        controller = new WmtsController(tileService, capabilitiesGenerator);
    }

    // ==================== Capabilities ====================

    @Test
    void getCapabilities_returns200WithXml() throws Exception {
        when(capabilitiesGenerator.getCapabilitiesXml()).thenReturn("<Capabilities/>");

        invokeGetCapabilities();

        verify(ctx).contentType("application/xml;charset=UTF-8");
        verify(ctx).result("<Capabilities/>");
    }

    // ==================== GetTile — validation ====================

    @Test
    void getTile_missingPngSuffix_throws400() {
        setupTilePathParams("terrain", "10", "100", "200");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    @Test
    void getTile_invalidZoom_throws400() {
        setupTilePathParams("terrain", "abc", "100", "200.png");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    @Test
    void getTile_invalidRow_throws400() {
        setupTilePathParams("terrain", "10", "xyz", "200.png");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    @Test
    void getTile_invalidCol_throws400() {
        setupTilePathParams("terrain", "10", "100", "abc.png");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    @Test
    void getTile_zoomOutOfRange_throws400() {
        setupTilePathParams("terrain", "16", "0", "0.png");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    @Test
    void getTile_rowOutOfBounds_throws400() {
        // At zoom=0, matrix is 1x1; row=1 is out of bounds
        setupTilePathParams("terrain", "0", "1", "0.png");

        assertThrows(BadRequestResponse.class, this::invokeGetTile);
    }

    // ==================== GetTile — layer not found ====================

    @Test
    void getTile_unknownLayer_throws404() {
        setupTilePathParams("unknown", "10", "100", "200.png");
        when(tileService.getTile("unknown", 10, 100, 200))
                .thenThrow(new IllegalArgumentException("Unknown layer"));

        assertThrows(NotFoundResponse.class, this::invokeGetTile);
    }

    // ==================== GetTile — no tile available ====================

    @Test
    void getTile_tileNotFound_returns204() throws Exception {
        setupTilePathParams("terrain", "10", "100", "200.png");
        when(tileService.getTile("terrain", 10, 100, 200))
                .thenThrow(new TileService.TileNotFoundException("No tile"));

        invokeGetTile();

        verify(ctx).status(HttpStatus.NO_CONTENT);
    }

    // ==================== GetTile — pre-rendered ====================

    @Test
    void getTile_preRendered_returns200WithEtag() throws Exception {
        var data = new byte[]{1, 2, 3, 4};
        setupTilePathParams("terrain", "10", "100", "200.png");
        when(tileService.getTile("terrain", 10, 100, 200))
                .thenReturn(new TileService.TileResult.PreRendered(data));
        when(ctx.header("If-None-Match")).thenReturn(null);

        invokeGetTile();

        verify(ctx).contentType("image/png");
        verify(ctx).result(data);
        // Should set ETag and Cache-Control headers
        verify(ctx).header(eq("ETag"), anyString());
        verify(ctx).header("Cache-Control", "public, max-age=86400");
    }

    @Test
    void getTile_preRendered_etagMatchReturns304() throws Exception {
        var data = new byte[]{1, 2, 3, 4};
        setupTilePathParams("terrain", "10", "100", "200.png");
        when(tileService.getTile("terrain", 10, 100, 200))
                .thenReturn(new TileService.TileResult.PreRendered(data));

        // Compute what the ETag will be
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var hash = digest.digest(data);
        var expectedEtag = "\"" + java.util.HexFormat.of().formatHex(hash) + "\"";

        when(ctx.header("If-None-Match")).thenReturn(expectedEtag);

        invokeGetTile();

        verify(ctx).status(HttpStatus.NOT_MODIFIED);
        verify(ctx, never()).result(any(byte[].class));
    }

    @Test
    void getTile_preRendered_differentEtagDoesNotReturn304() throws Exception {
        var data = new byte[]{1, 2, 3, 4};
        setupTilePathParams("terrain", "10", "100", "200.png");
        when(tileService.getTile("terrain", 10, 100, 200))
                .thenReturn(new TileService.TileResult.PreRendered(data));
        when(ctx.header("If-None-Match")).thenReturn("\"different-etag\"");

        invokeGetTile();

        verify(ctx).result(data);
        verify(ctx, never()).status(HttpStatus.NOT_MODIFIED);
    }

    // ==================== GetTile — resampled ====================

    @Test
    void getTile_resampled_returns200WithShortCacheControl() throws Exception {
        var data = new byte[]{5, 6, 7, 8};
        setupTilePathParams("terrain", "11", "200", "400.png");
        when(tileService.getTile("terrain", 11, 200, 400))
                .thenReturn(new TileService.TileResult.Resampled(data));

        invokeGetTile();

        verify(ctx).contentType("image/png");
        verify(ctx).result(data);
        verify(ctx).header("Cache-Control", "public, max-age=3600");
        // Should NOT set ETag for resampled tiles
        verify(ctx, never()).header(eq("ETag"), anyString());
    }

    // ==================== helpers ====================

    private void setupTilePathParams(String layer, String zoom, String row, String colFile) {
        when(ctx.pathParam("layer")).thenReturn(layer);
        when(ctx.pathParam("zoom")).thenReturn(zoom);
        when(ctx.pathParam("row")).thenReturn(row);
        when(ctx.pathParam("colFile")).thenReturn(colFile);
    }

    private void invokeGetCapabilities() throws Exception {
        // Use reflection to call the private method, or restructure to expose for test
        // Instead, test via a stub approach — call indirectly by verifying behavior
        // We expose it by calling the method through a test helper
        callPrivateMethod("handleGetCapabilities", ctx);
    }

    private void invokeGetTile() throws Exception {
        callPrivateMethod("handleGetTile", ctx);
    }

    private void callPrivateMethod(String methodName, Context context) throws Exception {
        var method = WmtsController.class.getDeclaredMethod(methodName, Context.class);
        method.setAccessible(true);
        try {
            method.invoke(controller, context);
        } catch (java.lang.reflect.InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
