package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

/**
 * Result record for road segment search operations.
 * <p>
 * Contains the segment's identifying information, name, municipality,
 * address ranges for both sides of the road, and the similarity score
 * from the fuzzy name matching.
 *
 * @param id              the unique identifier of the road segment
 * @param roadName        the multilingual name of the road
 * @param municipality    the municipality containing the segment, or null if unknown
 * @param minAddressLeft  minimum address number on the left side, or null if not assigned
 * @param maxAddressLeft  maximum address number on the left side, or null if not assigned
 * @param minAddressRight minimum address number on the right side, or null if not assigned
 * @param maxAddressRight maximum address number on the right side, or null if not assigned
 * @param similarityScore the trigram similarity score (0.0 to 1.0) from pg_trgm matching
 */
public record RoadSegmentSearchResult(
        long id,
        MultilingualName roadName,
        @Nullable Municipality municipality,
        @Nullable Integer minAddressLeft,
        @Nullable Integer maxAddressLeft,
        @Nullable Integer minAddressRight,
        @Nullable Integer maxAddressRight,
        double similarityScore
) {

    /**
     * Creates a new road segment search result.
     *
     * @param id              the unique identifier of the road segment
     * @param roadName        the multilingual name of the road
     * @param municipality    the municipality containing the segment
     * @param minAddressLeft  minimum address number on the left side
     * @param maxAddressLeft  maximum address number on the left side
     * @param minAddressRight minimum address number on the right side
     * @param maxAddressRight maximum address number on the right side
     * @param similarityScore the trigram similarity score
     * @throws NullPointerException     if roadName is null
     * @throws IllegalArgumentException if similarityScore is not between 0.0 and 1.0
     */
    public RoadSegmentSearchResult {
        if (roadName == null) {
            throw new NullPointerException("roadName must not be null");
        }
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException(
                    "similarityScore must be between 0.0 and 1.0, got " + similarityScore);
        }
    }
}
