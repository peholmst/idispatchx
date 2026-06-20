package net.pkhapps.idispatchx.gis.server.api.wmts;

import io.javalin.http.BadRequestResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.gis.server.model.TileCoordinates;
import net.pkhapps.idispatchx.gis.server.service.tile.TileService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Javalin controller for the WMTS (Web Map Tile Service) API.
 * <p>
 * Provides two endpoints:
 * <ul>
 *   <li>GET /wmts/1.0.0/WMTSCapabilities.xml – returns the WMTS capabilities document</li>
 *   <li>GET /wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{colFile} – returns a map tile</li>
 * </ul>
 * <p>
 * All WMTS routes require authentication (applied via before-filters).
 */
public final class WmtsController {

    private static final String CONTENT_TYPE_XML = "application/xml;charset=UTF-8";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CACHE_CONTROL_PRE_RENDERED = "public, max-age=86400";
    private static final String CACHE_CONTROL_RESAMPLED = "public, max-age=3600";

    private final TileService tileService;
    private final CapabilitiesGenerator capabilitiesGenerator;

    /**
     * Creates a new WMTS controller.
     *
     * @param tileService          the tile service for retrieving tiles
     * @param capabilitiesGenerator the capabilities document generator
     */
    public WmtsController(TileService tileService, CapabilitiesGenerator capabilitiesGenerator) {
        this.tileService = Objects.requireNonNull(tileService, "tileService must not be null");
        this.capabilitiesGenerator = Objects.requireNonNull(capabilitiesGenerator, "capabilitiesGenerator must not be null");
    }

    /**
     * Registers all WMTS routes on the given Javalin instance.
     *
     * @param router          the Javalin routing API
     * @param jwtAuthHandler  the JWT authentication handler (applied as before-filter)
     * @param roleAuthHandler the role authorization handler (applied as before-filter)
     * @param contextPath     the URL context path prefix (empty or starts with {@code /})
     */
    public void registerRoutes(JavalinDefaultRoutingApi router, Handler jwtAuthHandler, Handler roleAuthHandler, String contextPath) {
        router.before(contextPath + "/wmts/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        router.before(contextPath + "/wmts/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) roleAuthHandler.handle(ctx); });
        router.get(contextPath + "/wmts/1.0.0/WMTSCapabilities.xml", this::handleGetCapabilities);
        router.get(contextPath + "/wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{colFile}", this::handleGetTile);
    }

    @OpenApi(
        path = "/wmts/1.0.0/WMTSCapabilities.xml",
        methods = {HttpMethod.GET},
        operationId = "getWmtsCapabilities",
        tags = {"WMTS"},
        summary = "Get WMTS capabilities document",
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(type = "application/xml")}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleGetCapabilities(Context ctx) {
        ctx.contentType(CONTENT_TYPE_XML);
        ctx.result(capabilitiesGenerator.getCapabilitiesXml());
    }

    @OpenApi(
        path = "/wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{colFile}",
        methods = {HttpMethod.GET},
        operationId = "getWmtsTile",
        tags = {"WMTS"},
        summary = "Get a WMTS map tile",
        pathParams = {
            @OpenApiParam(name = "layer", description = "Tile layer name", required = true),
            @OpenApiParam(name = "zoom", description = "Zoom level", type = Integer.class, required = true),
            @OpenApiParam(name = "row", description = "Tile row", type = Integer.class, required = true),
            @OpenApiParam(name = "colFile", description = "Tile column with .png extension", required = true)
        },
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(type = "image/png")}),
            @OpenApiResponse(status = "204"),
            @OpenApiResponse(status = "304"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleGetTile(Context ctx) {
        var layerName = ctx.pathParam("layer");
        var zoomStr = ctx.pathParam("zoom");
        var rowStr = ctx.pathParam("row");
        var colFile = ctx.pathParam("colFile");

        // Strip .png suffix from colFile
        String colStr;
        if (colFile.endsWith(".png")) {
            colStr = colFile.substring(0, colFile.length() - 4);
        } else {
            throw new BadRequestResponse("Tile file must have .png extension");
        }

        TileCoordinates coordinates;
        try {
            var zoom = Integer.parseInt(zoomStr);
            var row = Integer.parseInt(rowStr);
            var col = Integer.parseInt(colStr);
            coordinates = TileCoordinates.of(zoom, row, col);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid tile coordinates: zoom, row, and col must be integers");
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid tile coordinates: " + e.getMessage());
        }

        // Retrieve tile
        TileService.TileResult result;
        try {
            result = tileService.getTile(layerName, coordinates);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse("Unknown layer: " + layerName);
        } catch (TileService.TileNotFoundException e) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }

        // Handle different result types
        switch (result) {
            case TileService.TileResult.PreRendered preRendered -> {
                var data = preRendered.data();
                var etag = computeEtag(data);

                // Check If-None-Match
                var ifNoneMatch = ctx.header("If-None-Match");
                if (etag.equals(ifNoneMatch)) {
                    ctx.status(HttpStatus.NOT_MODIFIED);
                    return;
                }

                ctx.header("ETag", etag);
                ctx.header("Cache-Control", CACHE_CONTROL_PRE_RENDERED);
                ctx.contentType(CONTENT_TYPE_PNG);
                ctx.result(data);
            }
            case TileService.TileResult.Resampled resampled -> {
                ctx.header("Cache-Control", CACHE_CONTROL_RESAMPLED);
                ctx.contentType(CONTENT_TYPE_PNG);
                ctx.result(resampled.data());
            }
        }
    }

    private String computeEtag(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(data);
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available by the JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
