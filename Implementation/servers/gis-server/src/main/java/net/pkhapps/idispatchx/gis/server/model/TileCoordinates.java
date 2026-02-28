package net.pkhapps.idispatchx.gis.server.model;

/**
 * Value object representing the coordinates of a WMTS tile in the ETRS-TM35FIN tile matrix.
 * <p>
 * The tile matrix follows the JHS 180 standard with:
 * <ul>
 *   <li>Zoom levels 0-15</li>
 *   <li>Matrix dimensions doubling at each level (2^zoom x 2^zoom)</li>
 * </ul>
 * <p>
 * The row and column values are zero-indexed and must be within the matrix bounds
 * for the specified zoom level.
 *
 * @param zoom the zoom level (0-15)
 * @param row  the row index (0 to 2^zoom - 1)
 * @param col  the column index (0 to 2^zoom - 1)
 */
public record TileCoordinates(int zoom, int row, int col) {

    /**
     * Minimum valid zoom level.
     */
    public static final int MIN_ZOOM = 0;

    /**
     * Maximum valid zoom level.
     */
    public static final int MAX_ZOOM = 15;

    /**
     * Creates new tile coordinates with validation.
     *
     * @param zoom the zoom level (0-15)
     * @param row  the row index
     * @param col  the column index
     * @throws IllegalArgumentException if zoom is not in range 0-15
     * @throws IllegalArgumentException if row or col is negative
     * @throws IllegalArgumentException if row or col exceeds matrix bounds for the zoom level
     */
    public TileCoordinates {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException(
                    "zoom must be between " + MIN_ZOOM + " and " + MAX_ZOOM + ", got " + zoom);
        }

        var matrixDimension = matrixDimensionForZoom(zoom);

        if (row < 0) {
            throw new IllegalArgumentException("row must be non-negative, got " + row);
        }
        if (row >= matrixDimension) {
            throw new IllegalArgumentException(
                    "row must be less than " + matrixDimension + " for zoom level " + zoom + ", got " + row);
        }

        if (col < 0) {
            throw new IllegalArgumentException("col must be non-negative, got " + col);
        }
        if (col >= matrixDimension) {
            throw new IllegalArgumentException(
                    "col must be less than " + matrixDimension + " for zoom level " + zoom + ", got " + col);
        }
    }

    /**
     * Creates new tile coordinates.
     *
     * @param zoom the zoom level (0-15)
     * @param row  the row index
     * @param col  the column index
     * @return the tile coordinates
     * @throws IllegalArgumentException if zoom is not in range 0-15
     * @throws IllegalArgumentException if row or col is negative
     * @throws IllegalArgumentException if row or col exceeds matrix bounds for the zoom level
     */
    public static TileCoordinates of(int zoom, int row, int col) {
        return new TileCoordinates(zoom, row, col);
    }

    /**
     * Returns the matrix dimension (number of rows/columns) for the given zoom level.
     * <p>
     * Following JHS 180, the matrix dimension is 2^zoom.
     *
     * @param zoom the zoom level
     * @return the matrix dimension (1 for zoom 0, 2 for zoom 1, ..., 32768 for zoom 15)
     */
    public static int matrixDimensionForZoom(int zoom) {
        return 1 << zoom; // 2^zoom
    }
}
