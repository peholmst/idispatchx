package net.pkhapps.idispatchx.gis.server.api.geocode;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.pkhapps.idispatchx.gis.server.service.geocode.DatabaseUnavailableException;
import net.pkhapps.idispatchx.gis.server.service.geocode.GeocodeService;

import java.util.Objects;

/**
 * Javalin controller for the geocoding API.
 * <p>
 * Provides the endpoint:
 * <ul>
 *   <li>GET /api/v1/geocode/search – searches for addresses, places, and intersections</li>
 * </ul>
 * <p>
 * All geocoding routes require authentication (applied via before-filters).
 */
public final class GeocodeController {

    private final GeocodeService geocodeService;

    /**
     * Creates a new geocode controller.
     *
     * @param geocodeService the geocode service for performing searches
     */
    public GeocodeController(GeocodeService geocodeService) {
        this.geocodeService = Objects.requireNonNull(geocodeService, "geocodeService must not be null");
    }

    /**
     * Registers all geocoding routes on the given Javalin instance.
     *
     * @param app             the Javalin application
     * @param jwtAuthHandler  the JWT authentication handler (applied as before-filter)
     * @param roleAuthHandler the role authorization handler (applied as before-filter)
     */
    public void registerRoutes(Javalin app, Handler jwtAuthHandler, Handler roleAuthHandler) {
        app.before("/api/v1/geocode/*", jwtAuthHandler);
        app.before("/api/v1/geocode/*", roleAuthHandler);
        app.get("/api/v1/geocode/search", this::handleSearch);
    }

    private void handleSearch(Context ctx) {
        var q = ctx.queryParam("q");
        var limitStr = ctx.queryParam("limit");
        var municipality = ctx.queryParam("municipality");

        SearchRequest request;
        try {
            request = SearchRequest.of(q, limitStr, municipality);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestResponse(e.getMessage());
        }

        try {
            var response = geocodeService.search(request);
            ctx.json(response);
        } catch (DatabaseUnavailableException e) {
            ctx.status(503);
            ctx.json(SearchResponse.empty(request.query()));
        }
    }
}
