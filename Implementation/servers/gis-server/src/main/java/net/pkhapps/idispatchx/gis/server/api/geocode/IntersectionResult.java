package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonTypeName;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;

import java.util.Objects;

/**
 * A location result representing a road intersection.
 * <p>
 * An intersection is the point where two roads meet. The result includes
 * the multilingual names of both roads and the coordinates of the intersection
 * point.
 *
 * @param roadA        the multilingual name of the first road
 * @param roadB        the multilingual name of the second road
 * @param municipality the municipality where the intersection is located
 * @param coordinates  the intersection coordinates in EPSG:4326
 */
@JsonTypeName("intersection")
public record IntersectionResult(
        MultilingualName roadA,
        MultilingualName roadB,
        Municipality municipality,
        Coordinates.Epsg4326 coordinates
) implements LocationResult {

    /**
     * Compact constructor that validates all fields.
     *
     * @param roadA        the multilingual name of the first road
     * @param roadB        the multilingual name of the second road
     * @param municipality the municipality
     * @param coordinates  the coordinates
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if roadA or roadB is empty
     */
    public IntersectionResult {
        Objects.requireNonNull(roadA, "roadA must not be null");
        Objects.requireNonNull(roadB, "roadB must not be null");
        Objects.requireNonNull(municipality, "municipality must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        if (roadA.isEmpty()) {
            throw new IllegalArgumentException("roadA must not be empty");
        }
        if (roadB.isEmpty()) {
            throw new IllegalArgumentException("roadB must not be empty");
        }
    }
}
