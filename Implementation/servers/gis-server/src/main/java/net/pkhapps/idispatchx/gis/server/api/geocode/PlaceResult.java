package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonTypeName;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;

import java.util.Objects;

/**
 * A location result representing a named place.
 * <p>
 * A named place is a geographic feature such as a village, island, landmark,
 * park, or other point of interest from the National Land Survey of Finland
 * place name database.
 *
 * @param name         the multilingual place name
 * @param placeClass   the NLS place class code (e.g., 48111 for a square/plaza)
 * @param municipality the municipality where the place is located
 * @param coordinates  the location coordinates in EPSG:4326
 */
@JsonTypeName("place")
public record PlaceResult(
        MultilingualName name,
        int placeClass,
        Municipality municipality,
        Coordinates.Epsg4326 coordinates
) implements LocationResult {

    /**
     * Compact constructor that validates all fields.
     *
     * @param name         the multilingual place name
     * @param placeClass   the NLS place class code
     * @param municipality the municipality
     * @param coordinates  the coordinates
     * @throws NullPointerException     if any object parameter is null
     * @throws IllegalArgumentException if name is empty or placeClass is negative
     */
    public PlaceResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(municipality, "municipality must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (placeClass < 0) {
            throw new IllegalArgumentException("placeClass must not be negative");
        }
    }
}
