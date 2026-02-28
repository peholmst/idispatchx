package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

/**
 * A result record representing an address point found during a search.
 * <p>
 * Contains the address point data including coordinates, street name,
 * municipality information, and the similarity score indicating how well
 * the result matches the search query.
 *
 * @param id              the unique identifier of the address point
 * @param number          the street number (e.g., "12", "12A"), may be null
 * @param streetName      the multilingual street name
 * @param municipality    the municipality containing this address, may be null if not found
 * @param coordinates     the WGS 84 coordinates of the address point
 * @param similarityScore the similarity score (0.0 to 1.0) indicating match quality
 */
public record AddressSearchResult(
        long id,
        @Nullable String number,
        MultilingualName streetName,
        @Nullable Municipality municipality,
        Coordinates.Epsg4326 coordinates,
        double similarityScore
) {

    /**
     * Creates an AddressSearchResult with validation.
     *
     * @param id              the unique identifier
     * @param number          the street number, may be null
     * @param streetName      the multilingual street name
     * @param municipality    the municipality, may be null
     * @param coordinates     the coordinates
     * @param similarityScore the similarity score
     * @throws NullPointerException     if streetName or coordinates is null
     * @throws IllegalArgumentException if similarityScore is not between 0.0 and 1.0
     */
    public AddressSearchResult {
        if (streetName == null) {
            throw new NullPointerException("streetName must not be null");
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
