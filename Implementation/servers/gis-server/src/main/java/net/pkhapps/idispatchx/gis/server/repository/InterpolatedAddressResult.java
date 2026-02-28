package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Result record for address interpolation along a road segment.
 * <p>
 * Contains the street name, address number, municipality, and the
 * interpolated coordinates calculated based on the address position
 * along the road segment geometry.
 *
 * @param streetName   the multilingual name of the street
 * @param number       the address number as a string
 * @param municipality the municipality containing the address, or null if unknown
 * @param coordinates  the interpolated WGS 84 coordinates
 */
public record InterpolatedAddressResult(
        MultilingualName streetName,
        String number,
        @Nullable Municipality municipality,
        Coordinates.Epsg4326 coordinates
) {

    /**
     * Creates a new interpolated address result.
     *
     * @param streetName   the multilingual name of the street
     * @param number       the address number
     * @param municipality the municipality containing the address
     * @param coordinates  the interpolated WGS 84 coordinates
     * @throws NullPointerException if streetName, number, or coordinates is null
     */
    public InterpolatedAddressResult {
        Objects.requireNonNull(streetName, "streetName must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
    }
}
