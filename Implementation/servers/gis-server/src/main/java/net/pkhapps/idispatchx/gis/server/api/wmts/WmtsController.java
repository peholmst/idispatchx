package net.pkhapps.idispatchx.gis.server.api.wmts;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
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
     * @param app             the Javalin application
     * @param jwtAuthHandler  the JWT authentication handler (applied as before-filter)
     * @param roleAuthHandler the role authorization handler (applied as before-filter)
     */
    public void registerRoutes(Javalin app, Handler jwtAuthHandler, Handler roleAuthHandler) {
        app.before("/wmts/*", jwtAuthHandler);
        app.before("/wmts/*", roleAuthHandler);
        app.get("/wmts/1.0.0/WMTSCapabilities.xml", this::handleGetCapabilities);
        app.get("/wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{colFile}", this::handleGetTile);
    }

    private void handleGetCapabilities(Context ctx) {
        ctx.contentType(CONTENT_TYPE_XML);
        ctx.result(capabilitiesGenerator.getCapabilitiesXml());
    }

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

        int zoom;
        int row;
        int col;
        try {
            zoom = Integer.parseInt(zoomStr);
            row = Integer.parseInt(rowStr);
            col = Integer.parseInt(colStr);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid tile coordinates: zoom, row, and col must be integers");
        }

        // Validate coordinates
        try {
            TileCoordinates.of(zoom, row, col);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid tile coordinates: " + e.getMessage());
        }

        // Retrieve tile
        TileService.TileResult result;
        try {
            result = tileService.getTile(layerName, zoom, row, col);
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
