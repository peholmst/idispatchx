package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for geocoding search operations.
 * <p>
 * Contains the search results along with metadata about the query.
 *
 * @param results     the list of location results (may be empty, never null)
 * @param query       the original search query string
 * @param resultCount the number of results returned
 */
public record SearchResponse(
        List<LocationResult> results,
        String query,
        int resultCount
) {

    /**
     * Compact constructor that validates all fields.
     *
     * @param results     the list of location results
     * @param query       the original search query
     * @param resultCount the number of results
     * @throws NullPointerException     if results or query is null
     * @throws IllegalArgumentException if resultCount does not match results size or is negative
     */
    public SearchResponse {
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (resultCount < 0) {
            throw new IllegalArgumentException("resultCount must not be negative");
        }
        if (resultCount != results.size()) {
            throw new IllegalArgumentException(
                    "resultCount must match results size: expected " + results.size() + ", got " + resultCount);
        }
        results = List.copyOf(results);
    }

    /**
     * Creates a SearchResponse from a list of results and the original query.
     * <p>
     * The resultCount is automatically set to the size of the results list.
     *
     * @param results the list of location results
     * @param query   the original search query
     * @return the SearchResponse
     * @throws NullPointerException if results or query is null
     */
    public static SearchResponse of(List<LocationResult> results, String query) {
        return new SearchResponse(results, query, results.size());
    }

    /**
     * Creates an empty SearchResponse for a query with no results.
     *
     * @param query the original search query
     * @return the empty SearchResponse
     * @throws NullPointerException if query is null
     */
    public static SearchResponse empty(String query) {
        return new SearchResponse(List.of(), query, 0);
    }

    /**
     * Returns true if this response contains no results.
     *
     * @return true if results is empty
     */
    @JsonIgnore
    public boolean isEmpty() {
        return results.isEmpty();
    }
}
