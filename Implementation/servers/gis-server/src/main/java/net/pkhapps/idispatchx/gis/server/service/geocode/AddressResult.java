package net.pkhapps.idispatchx.gis.server.service.geocode;

import com.fasterxml.jackson.annotation.JsonTypeName;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A location result representing a street address or road name.
 * <p>
 * An address consists of a street name (multilingual), an optional number,
 * municipality, coordinates, and a source. When {@code number} is {@code null}
 * the result represents a road name without a specific address (e.g. the
 * representative midpoint of a road returned by a name-only search).
 *
 * @param name         the multilingual street name
 * @param number       the address number (e.g., "5", "5A"), or null for road-name results
 * @param municipality the municipality where the address is located
 * @param coordinates  the location coordinates in EPSG:4326
 * @param source       the data source for the coordinates
 */
@JsonTypeName("address")
public record AddressResult(
        MultilingualName name,
        @Nullable String number,
        Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        AddressSource source
) implements LocationResult {

    /**
     * Compact constructor that validates all fields.
     *
     * @param name         the multilingual street name
     * @param number       the address number, or null for road-name results
     * @param municipality the municipality
     * @param coordinates  the coordinates
     * @param source       the data source
     * @throws NullPointerException     if name, municipality, coordinates, or source is null
     * @throws IllegalArgumentException if name is empty, or if number is non-null but blank
     */
    public AddressResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(municipality, "municipality must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (number != null && number.isBlank()) {
            throw new IllegalArgumentException("number must not be blank when provided");
        }
    }
}
