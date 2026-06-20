package net.pkhapps.idispatchx.gis.server.api.geocode;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.gis.server.api.error.GisErrorCode;
import net.pkhapps.idispatchx.gis.server.service.geocode.DatabaseUnavailableException;
import net.pkhapps.idispatchx.gis.server.service.geocode.GeocodeService;
import net.pkhapps.idispatchx.gis.server.service.geocode.SearchRequest;
import net.pkhapps.idispatchx.gis.server.service.geocode.SearchResponse;

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
     * @param router          the Javalin routing API
     * @param jwtAuthHandler  the JWT authentication handler (applied as before-filter)
     * @param roleAuthHandler the role authorization handler (applied as before-filter)
     * @param contextPath     the URL context path prefix (empty or starts with {@code /})
     */
    public void registerRoutes(JavalinDefaultRoutingApi router, Handler jwtAuthHandler, Handler roleAuthHandler, String contextPath) {
        router.before(contextPath + "/api/v1/geocode/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        router.before(contextPath + "/api/v1/geocode/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) roleAuthHandler.handle(ctx); });
        router.get(contextPath + "/api/v1/geocode/search", this::handleSearch);
    }

    @OpenApi(
        path = "/api/v1/geocode/search",
        methods = {HttpMethod.GET},
        operationId = "geocodeSearch",
        tags = {"Geocode"},
        summary = "Search for addresses, places, and intersections",
        queryParams = {
            @OpenApiParam(name = "q", description = "Search query (minimum 3 characters)", required = true),
            @OpenApiParam(name = "limit", description = "Maximum number of results to return", type = Integer.class),
            @OpenApiParam(name = "municipality", description = "Filter results by municipality code")
        },
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = SearchResponse.class)}),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "503", content = {@OpenApiContent(from = SearchResponse.class)})
        }
    )
    private void handleSearch(Context ctx) {
        var q = ctx.queryParam("q");
        var limitStr = ctx.queryParam("limit");
        var municipality = ctx.queryParam("municipality");

        // Validate the query parameter first so it produces INVALID_QUERY (not INVALID_PARAMETER)
        if (q == null || q.isBlank()) {
            throw new ValidationException(GisErrorCode.INVALID_QUERY, "query is required");
        }
        var trimmedQuery = q.trim();
        if (trimmedQuery.length() < SearchRequest.MIN_QUERY_LENGTH) {
            throw new ValidationException(GisErrorCode.INVALID_QUERY,
                    "query must be at least " + SearchRequest.MIN_QUERY_LENGTH + " characters");
        }
        if (trimmedQuery.length() > SearchRequest.MAX_QUERY_LENGTH) {
            throw new ValidationException(GisErrorCode.INVALID_QUERY,
                    "query must not exceed " + SearchRequest.MAX_QUERY_LENGTH + " characters");
        }

        // Remaining validation (limit, municipality) produces INVALID_PARAMETER
        SearchRequest request;
        try {
            request = SearchRequest.of(q, limitStr, municipality);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(GisErrorCode.INVALID_PARAMETER,
                    e.getMessage() != null ? e.getMessage() : "Invalid parameter");
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
