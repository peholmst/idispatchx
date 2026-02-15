package net.pkhapps.idispatchx.gis.importer.transform;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.referencing.CRS;
/**
 * Transforms coordinates from EPSG:3067 (EUREF-FIN / TM35FIN) to EPSG:4326 (WGS 84).
 * Validates that transformed coordinates fall within Finland's bounds.
 */
public final class CoordinateTransformer {

    private static final double MIN_LATITUDE = 58.84;
    private static final double MAX_LATITUDE = 70.09;
    private static final double MIN_LONGITUDE = 19.08;
    private static final double MAX_LONGITUDE = 31.59;

    private final MathTransform transform;

    public CoordinateTransformer() {
        try {
            CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:3067");
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326");
            this.transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
        } catch (FactoryException e) {
            throw new IllegalStateException("Failed to initialize coordinate transformation", e);
        }
    }

    /**
     * Transforms a single point from EPSG:3067 to EPSG:4326.
     *
     * @param easting  the easting coordinate in EPSG:3067
     * @param northing the northing coordinate in EPSG:3067
     * @return {@code double[]{latitude, longitude}} in EPSG:4326
     * @throws IllegalArgumentException if the transformed coordinates are outside Finland's bounds
     */
    public double[] transformPoint(double easting, double northing) {
        var source = new double[]{easting, northing};
        var target = new double[2];
        try {
            transform.transform(source, 0, target, 0, 1);
        } catch (TransformException e) {
            throw new IllegalArgumentException("Failed to transform point (" + easting + ", " + northing + ")", e);
        }
        // GeoTools CRS.decode("EPSG:4326") returns coordinates as (lat, lon)
        double latitude = target[0];
        double longitude = target[1];
        validateBounds(latitude, longitude);
        return new double[]{latitude, longitude};
    }

    /**
     * Transforms a linestring from EPSG:3067 to EPSG:4326.
     *
     * @param coords array of [easting, northing] pairs in EPSG:3067
     * @return array of [latitude, longitude] pairs in EPSG:4326
     * @throws IllegalArgumentException if any transformed coordinate is outside Finland's bounds
     */
    public double[][] transformLineString(double[][] coords) {
        return transformCoordArray(coords);
    }

    /**
     * Transforms a polygon from EPSG:3067 to EPSG:4326.
     *
     * @param coords array of [easting, northing] pairs in EPSG:3067
     * @return array of [latitude, longitude] pairs in EPSG:4326
     * @throws IllegalArgumentException if any transformed coordinate is outside Finland's bounds
     */
    public double[][] transformPolygon(double[][] coords) {
        return transformCoordArray(coords);
    }

    private double[][] transformCoordArray(double[][] coords) {
        var source = new double[coords.length * 2];
        for (int i = 0; i < coords.length; i++) {
            source[i * 2] = coords[i][0];
            source[i * 2 + 1] = coords[i][1];
        }
        var target = new double[source.length];
        try {
            transform.transform(source, 0, target, 0, coords.length);
        } catch (TransformException e) {
            throw new IllegalArgumentException("Failed to transform coordinates", e);
        }
        var result = new double[coords.length][];
        for (int i = 0; i < coords.length; i++) {
            double latitude = target[i * 2];
            double longitude = target[i * 2 + 1];
            validateBounds(latitude, longitude);
            result[i] = new double[]{latitude, longitude};
        }
        return result;
    }

    private void validateBounds(double latitude, double longitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException(
                    "Transformed coordinate outside Finland bounds: lat=" + latitude + ", lon=" + longitude);
        }
    }
}
