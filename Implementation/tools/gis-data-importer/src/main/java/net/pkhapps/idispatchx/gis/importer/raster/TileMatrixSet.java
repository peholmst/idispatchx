package net.pkhapps.idispatchx.gis.importer.raster;

/**
 * Constants and coordinate math for the ETRS-TM35FIN tile matrix set (JHS 180 standard).
 * All coordinates are in EPSG:3067.
 */
public final class TileMatrixSet {

    public static final int TILE_SIZE = 256;
    public static final double ORIGIN_X = -548576.0;
    public static final double ORIGIN_Y = 8388608.0;
    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 15;
    public static final String IDENTIFIER = "ETRS-TM35FIN";

    private TileMatrixSet() {
    }

    /**
     * Geographic bounds of a tile in EPSG:3067 meters.
     *
     * @param west  western edge (min easting)
     * @param north northern edge (max northing)
     * @param east  eastern edge (max easting)
     * @param south southern edge (min northing)
     */
    public record TileBounds(double west, double north, double east, double south) {
    }

    /**
     * A tile coordinate in the tile matrix set.
     *
     * @param zoom zoom level (0–15)
     * @param row  row index (increases southward)
     * @param col  column index (increases eastward)
     */
    public record TileCoordinate(int zoom, int row, int col) {
    }

    /**
     * Returns the pixel size in meters at the given zoom level.
     *
     * @param zoom zoom level (0–15)
     * @return pixel size in meters
     * @throws IllegalArgumentException if zoom is out of range
     */
    public static double pixelSize(int zoom) {
        validateZoom(zoom);
        return 8192.0 / Math.pow(2, zoom);
    }

    /**
     * Returns the tile span (geographic extent of one tile) in meters at the given zoom level.
     *
     * @param zoom zoom level (0–15)
     * @return tile span in meters
     * @throws IllegalArgumentException if zoom is out of range
     */
    public static double tileSpan(int zoom) {
        return pixelSize(zoom) * TILE_SIZE;
    }

    /**
     * Returns the tile column index for the given easting at the given zoom level.
     *
     * @param easting easting in EPSG:3067 meters
     * @param zoom    zoom level (0–15)
     * @return column index
     */
    public static int column(double easting, int zoom) {
        return (int) Math.floor((easting - ORIGIN_X) / tileSpan(zoom));
    }

    /**
     * Returns the tile row index for the given northing at the given zoom level.
     *
     * @param northing northing in EPSG:3067 meters
     * @param zoom     zoom level (0–15)
     * @return row index
     */
    public static int row(double northing, int zoom) {
        return (int) Math.floor((ORIGIN_Y - northing) / tileSpan(zoom));
    }

    /**
     * Returns the geographic bounds of the tile at the given coordinate.
     *
     * @param zoom zoom level (0–15)
     * @param row  row index
     * @param col  column index
     * @return tile bounds in EPSG:3067 meters
     */
    public static TileBounds tileBounds(int zoom, int row, int col) {
        var span = tileSpan(zoom);
        var west = ORIGIN_X + col * span;
        var north = ORIGIN_Y - row * span;
        var east = west + span;
        var south = north - span;
        return new TileBounds(west, north, east, south);
    }

    /**
     * Determines the zoom level for the given source pixel width.
     *
     * @param pixelWidth pixel width in meters (must be positive)
     * @return zoom level (0–15)
     * @throws IllegalArgumentException if pixel width doesn't match a valid zoom level within tolerance
     */
    public static int zoomLevel(double pixelWidth) {
        var rawZoom = Math.log(8192.0 / pixelWidth) / Math.log(2);
        var zoom = (int) Math.round(rawZoom);
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException(
                    "Pixel width " + pixelWidth + " does not correspond to a valid zoom level (0–15)");
        }
        var expectedPixelSize = 8192.0 / Math.pow(2, zoom);
        if (Math.abs(pixelWidth - expectedPixelSize) > 0.001) {
            throw new IllegalArgumentException(
                    "Pixel width " + pixelWidth + " does not match expected " + expectedPixelSize
                            + " for zoom level " + zoom);
        }
        return zoom;
    }

    private static void validateZoom(int zoom) {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("Zoom level must be between " + MIN_ZOOM + " and " + MAX_ZOOM + ", got " + zoom);
        }
    }
}
