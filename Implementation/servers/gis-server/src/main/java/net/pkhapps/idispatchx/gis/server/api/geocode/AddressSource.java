package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the data source for an address result.
 * <p>
 * Address results can come from two sources:
 * <ul>
 *   <li>{@link #ADDRESS_POINT} - Exact coordinates from address point data</li>
 *   <li>{@link #ROAD_SEGMENT} - Interpolated coordinates along a road segment</li>
 * </ul>
 */
public enum AddressSource {

    /**
     * Address coordinates from exact address point data.
     */
    ADDRESS_POINT("address_point"),

    /**
     * Address coordinates interpolated along a road segment.
     */
    ROAD_SEGMENT("road_segment");

    private final String jsonValue;

    AddressSource(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * Returns the JSON serialization value for this source.
     *
     * @return the JSON value (lowercase with underscore)
     */
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
