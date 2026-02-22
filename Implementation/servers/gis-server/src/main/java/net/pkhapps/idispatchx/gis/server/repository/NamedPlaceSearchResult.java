package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

/**
 * A result record representing a named place found during a search.
 * <p>
 * Contains the named place data including coordinates, multilingual name,
 * place classification, municipality information, and the similarity score
 * indicating how well the result matches the search query.
 * <p>
 * The karttanimiId groups all language versions of the same place. The
 * multilingual name contains all available language versions for the place.
 *
 * @param karttanimiId    the karttanimi ID grouping multilingual entries for the same place
 * @param name            the multilingual name containing all language versions
 * @param placeClass      the place classification code
 * @param municipality    the municipality containing this place, may be null if not found
 * @param coordinates     the WGS 84 coordinates of the named place
 * @param similarityScore the similarity score (0.0 to 1.0) indicating match quality
 */
public record NamedPlaceSearchResult(
        long karttanimiId,
        MultilingualName name,
        int placeClass,
        @Nullable Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        double similarityScore
) {

    /**
     * Creates a NamedPlaceSearchResult with validation.
     *
     * @param karttanimiId    the karttanimi ID
     * @param name            the multilingual name
     * @param placeClass      the place classification code
     * @param municipality    the municipality, may be null
     * @param coordinates     the coordinates
     * @param similarityScore the similarity score
     * @throws NullPointerException     if name or coordinates is null
     * @throws IllegalArgumentException if similarityScore is not between 0.0 and 1.0
     */
    public NamedPlaceSearchResult {
        if (name == null) {
            throw new NullPointerException("name must not be null");
        }
        if (coordinates == null) {
            throw new NullPointerException("coordinates must not be null");
        }
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException(
                    "similarityScore must be between 0.0 and 1.0, got " + similarityScore);
        }
    }
}
