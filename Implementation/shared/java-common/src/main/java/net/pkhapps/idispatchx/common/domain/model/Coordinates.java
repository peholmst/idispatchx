package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;

/**
 * A sealed interface representing geographical coordinates in a known Coordinate Reference System (CRS).
 * <p>
 * The system supports two CRS:
 * <ul>
 *   <li>{@link Epsg4326} — WGS 84 (latitude/longitude), used for operational data</li>
 *   <li>{@link Epsg3067} — EUREF-FIN / TM35FIN(E,N), used for geospatial material from NLS Finland</li>
 * </ul>
 *
 * @see Epsg4326
 * @see Epsg3067
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "crs")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Coordinates.Epsg4326.class, name = "EPSG:4326"),
        @JsonSubTypes.Type(value = Coordinates.Epsg3067.class, name = "EPSG:3067")
})
public sealed interface Coordinates permits Coordinates.Epsg4326, Coordinates.Epsg3067 {

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
    }

    private static void requireInRange(double value, double min, double max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be between " + min + " and " + max + ", got " + value);
        }
    }

    /**
     * WGS 84 coordinates (latitude/longitude) used for operational data storage and processing.
     * <p>
     * Validation rules (from NFR Internationalization):
     * <ul>
     *   <li>Values must be finite (not NaN or Infinity)</li>
     *   <li>Maximum 6 decimal places precision</li>
     *   <li>Latitude bounds: 58.84° to 70.09° N (inclusive)</li>
     *   <li>Longitude bounds: 19.08° to 31.59° E (inclusive)</li>
     * </ul>
     *
     * @param latitude  the latitude in decimal degrees
     * @param longitude the longitude in decimal degrees
     */
    record Epsg4326(double latitude, double longitude) implements Coordinates {

        private static final double MIN_LATITUDE = 58.84;
        private static final double MAX_LATITUDE = 70.09;
        private static final double MIN_LONGITUDE = 19.08;
        private static final double MAX_LONGITUDE = 31.59;
        private static final int MAX_DECIMAL_PLACES = 6;

        /**
         * Compact constructor that validates latitude and longitude.
         *
         * @param latitude  the latitude in decimal degrees
         * @param longitude the longitude in decimal degrees
         * @throws IllegalArgumentException if values are not finite, exceed precision, or are out of bounds
         */
        public Epsg4326 {
            requireFinite(latitude, "latitude");
            requireFinite(longitude, "longitude");
            requireMaxDecimalPlaces(latitude, "latitude");
            requireMaxDecimalPlaces(longitude, "longitude");
            requireInRange(latitude, MIN_LATITUDE, MAX_LATITUDE, "latitude");
            requireInRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "longitude");
        }

        /**
         * Creates an Epsg4326 coordinate pair.
         *
         * @param latitude  the latitude in decimal degrees
         * @param longitude the longitude in decimal degrees
         * @return the coordinate instance
         * @throws IllegalArgumentException if values are not finite, exceed precision, or are out of bounds
         */
        @JsonCreator
        public static Epsg4326 of(@JsonProperty("latitude") double latitude,
                                  @JsonProperty("longitude") double longitude) {
            return new Epsg4326(latitude, longitude);
        }

        private static void requireMaxDecimalPlaces(double value, String name) {
            var scale = BigDecimal.valueOf(value).stripTrailingZeros().scale();
            if (scale > MAX_DECIMAL_PLACES) {
                throw new IllegalArgumentException(
                        name + " must have at most " + MAX_DECIMAL_PLACES + " decimal places, got " + value);
            }
        }

        @Override
        public String toString() {
            return "EPSG:4326[" + latitude + ", " + longitude + "]";
        }
    }

    /**
     * EUREF-FIN / TM35FIN(E,N) coordinates (easting/northing) used for geospatial material from NLS Finland.
     * <p>
     * Validation rules (from NFR Internationalization):
     * <ul>
     *   <li>Values must be finite (not NaN or Infinity)</li>
     *   <li>Easting bounds: 43,547.79 to 764,796.72 m (inclusive)</li>
     *   <li>Northing bounds: 6,522,236.87 to 7,795,461.19 m (inclusive)</li>
     * </ul>
     *
     * @param easting  the easting coordinate in meters
     * @param northing the northing coordinate in meters
     */
    record Epsg3067(double easting, double northing) implements Coordinates {

        private static final double MIN_EASTING = 43_547.79;
        private static final double MAX_EASTING = 764_796.72;
        private static final double MIN_NORTHING = 6_522_236.87;
        private static final double MAX_NORTHING = 7_795_461.19;

        /**
         * Compact constructor that validates easting and northing.
         *
         * @param easting  the easting coordinate in meters
         * @param northing the northing coordinate in meters
         * @throws IllegalArgumentException if values are not finite or are out of bounds
         */
        public Epsg3067 {
            requireFinite(easting, "easting");
            requireFinite(northing, "northing");
            requireInRange(easting, MIN_EASTING, MAX_EASTING, "easting");
            requireInRange(northing, MIN_NORTHING, MAX_NORTHING, "northing");
        }

        /**
         * Creates an Epsg3067 coordinate pair.
         *
         * @param easting  the easting coordinate in meters
         * @param northing the northing coordinate in meters
         * @return the coordinate instance
         * @throws IllegalArgumentException if values are not finite or are out of bounds
         */
        @JsonCreator
        public static Epsg3067 of(@JsonProperty("easting") double easting,
                                  @JsonProperty("northing") double northing) {
            return new Epsg3067(easting, northing);
        }

        @Override
        public String toString() {
            return "EPSG:3067[" + easting + ", " + northing + "]";
        }
    }
}
