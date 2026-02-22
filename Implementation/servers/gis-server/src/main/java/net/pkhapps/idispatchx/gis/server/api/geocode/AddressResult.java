package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonTypeName;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;

import java.util.Objects;

/**
 * A location result representing a street address.
 * <p>
 * An address consists of a street name (multilingual), a number, municipality,
 * coordinates, and a source indicating whether the coordinates came from an
 * exact address point or were interpolated from a road segment.
 *
 * @param name         the multilingual street name
 * @param number       the address number (e.g., "5", "5A", "5-7")
 * @param municipality the municipality where the address is located
 * @param coordinates  the location coordinates in EPSG:4326
 * @param source       the data source for the coordinates
 */
@JsonTypeName("address")
public record AddressResult(
        MultilingualName name,
        String number,
        Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        AddressSource source
) implements LocationResult {

    /**
     * Compact constructor that validates all fields.
     *
     * @param name         the multilingual street name
     * @param number       the address number
     * @param municipality the municipality
     * @param coordinates  the coordinates
     * @param source       the data source
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if name is empty or number is blank
     */
    public AddressResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(municipality, "municipality must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (number.isBlank()) {
            throw new IllegalArgumentException("number must not be blank");
        }
    }
}
