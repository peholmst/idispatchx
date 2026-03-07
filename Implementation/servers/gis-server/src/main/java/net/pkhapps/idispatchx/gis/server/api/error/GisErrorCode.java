package net.pkhapps.idispatchx.gis.server.api.error;

/**
 * Error codes specific to the GIS Server.
 * <p>
 * These codes are used in {@link net.pkhapps.idispatchx.common.api.ErrorResponse}
 * to identify GIS-specific error conditions. Common error codes are defined in
 * {@link net.pkhapps.idispatchx.common.api.CommonErrorCode}.
 */
public final class GisErrorCode {

    private GisErrorCode() {
        // Utility class
    }

    /**
     * Search query validation failed (too short, too long, etc.).
     */
    public static final String INVALID_QUERY = "INVALID_QUERY";

    /**
     * Request parameter validation failed (invalid limit, municipality code, etc.).
     */
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";

    /**
     * Tile coordinates are outside the valid range for the requested zoom level.
     */
    public static final String INVALID_TILE_COORDINATES = "INVALID_TILE_COORDINATES";

    /**
     * The requested tile layer does not exist.
     */
    public static final String LAYER_NOT_FOUND = "LAYER_NOT_FOUND";
}
