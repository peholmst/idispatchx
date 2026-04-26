package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A representative point for a road name, used when searching by road name only.
 * <p>
 * Contains the road name, municipality, the midpoint coordinates of the first
 * matching road segment, and the trigram similarity score.
 *
 * @param roadName        the multilingual name of the road
 * @param municipality    the municipality containing the road, or null if unknown
 * @param coordinates     the representative WGS 84 coordinates (midpoint of first segment)
 * @param similarityScore the trigram similarity score (0.0 to 1.0)
 */
public record RoadNamePoint(
        MultilingualName roadName,
        @Nullable Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        double similarityScore
) {

    public RoadNamePoint {
        Objects.requireNonNull(roadName, "roadName must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException(
                    "similarityScore must be between 0.0 and 1.0, got " + similarityScore);
        }
    }
}
