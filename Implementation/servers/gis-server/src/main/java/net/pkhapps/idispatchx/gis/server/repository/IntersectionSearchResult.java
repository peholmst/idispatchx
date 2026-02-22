package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Result record for road intersection search operations.
 * <p>
 * Contains the names of the two intersecting roads, the municipality,
 * the intersection coordinates, and the similarity score from fuzzy matching.
 *
 * @param roadA           the multilingual name of the first road
 * @param roadB           the multilingual name of the second road
 * @param municipality    the municipality containing the intersection, or null if unknown
 * @param coordinates     the WGS 84 coordinates of the intersection point
 * @param similarityScore the trigram similarity score (0.0 to 1.0) from pg_trgm matching
 */
public record IntersectionSearchResult(
        MultilingualName roadA,
        MultilingualName roadB,
        @Nullable Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        double similarityScore
) {

    /**
     * Creates a new intersection search result.
     *
     * @param roadA           the multilingual name of the first road
     * @param roadB           the multilingual name of the second road
     * @param municipality    the municipality containing the intersection
     * @param coordinates     the WGS 84 coordinates of the intersection point
     * @param similarityScore the trigram similarity score
     * @throws NullPointerException     if roadA, roadB, or coordinates is null
     * @throws IllegalArgumentException if similarityScore is not between 0.0 and 1.0
     */
    public IntersectionSearchResult {
        Objects.requireNonNull(roadA, "roadA must not be null");
        Objects.requireNonNull(roadB, "roadB must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException(
                    "similarityScore must be between 0.0 and 1.0, got " + similarityScore);
        }
    }
}
