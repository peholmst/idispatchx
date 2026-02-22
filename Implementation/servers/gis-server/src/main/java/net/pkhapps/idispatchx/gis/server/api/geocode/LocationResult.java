package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Municipality;

/**
 * Sealed interface representing a geocoding location result.
 * <p>
 * Location results can be one of three types:
 * <ul>
 *   <li>{@link AddressResult} - A street address with number</li>
 *   <li>{@link PlaceResult} - A named place (landmark, village, etc.)</li>
 *   <li>{@link IntersectionResult} - A road intersection</li>
 * </ul>
 * <p>
 * Jackson serialization uses the {@code type} property as a discriminator.
 *
 * @see AddressResult
 * @see PlaceResult
 * @see IntersectionResult
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddressResult.class, name = "address"),
        @JsonSubTypes.Type(value = PlaceResult.class, name = "place"),
        @JsonSubTypes.Type(value = IntersectionResult.class, name = "intersection")
})
public sealed interface LocationResult permits AddressResult, PlaceResult, IntersectionResult {

    /**
     * Returns the municipality where this location is situated.
     *
     * @return the municipality
     */
    Municipality municipality();

    /**
     * Returns the coordinates of this location in EPSG:4326 (WGS 84).
     *
     * @return the coordinates
     */
    Coordinates.Epsg4326 coordinates();
}
