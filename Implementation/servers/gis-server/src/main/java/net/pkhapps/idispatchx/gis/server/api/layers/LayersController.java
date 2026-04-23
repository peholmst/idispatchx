package net.pkhapps.idispatchx.gis.server.api.layers;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Javalin controller for the layers API.
 * <p>
 * Provides the endpoint:
 * <ul>
 *   <li>GET /api/v1/layers – returns available WMTS layers as JSON</li>
 * </ul>
 * <p>
 * All layers routes require authentication (applied via before-filters).
 */
public final class LayersController {

    private final Map<String, TileLayer> layers;

    /**
     * Creates a new layers controller.
     *
     * @param layers the available tile layers, keyed by layer identifier
     */
    public LayersController(Map<String, TileLayer> layers) {
        this.layers = Objects.requireNonNull(layers, "layers must not be null");
    }

    /**
     * Registers all layers routes on the given Javalin instance.
     *
     * @param app             the Javalin application
     * @param jwtAuthHandler  the JWT authentication handler (applied as before-filter)
     * @param roleAuthHandler the role authorization handler (applied as before-filter)
     * @param contextPath     the URL context path prefix (empty or starts with {@code /})
     */
    public void registerRoutes(Javalin app, Handler jwtAuthHandler, Handler roleAuthHandler, String contextPath) {
        app.before(contextPath + "/api/v1/layers*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        app.before(contextPath + "/api/v1/layers*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) roleAuthHandler.handle(ctx); });
        app.get(contextPath + "/api/v1/layers", this::handleGetLayers);
    }

    private void handleGetLayers(Context ctx) {
        var layerInfos = layers.entrySet().stream()
                .map(entry -> new LayerInfo(entry.getKey(), toTitle(entry.getKey())))
                .toList();
        ctx.json(new LayersResponse(layerInfos));
    }

    static String toTitle(String id) {
        var words = id.split("[-_]");
        var sb = new StringBuilder();
        for (var word : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    record LayerInfo(String id, String title) {}

    record LayersResponse(List<LayerInfo> layers) {}
}
