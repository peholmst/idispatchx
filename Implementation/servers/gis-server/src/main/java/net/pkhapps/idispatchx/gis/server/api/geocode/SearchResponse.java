package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for geocoding search operations.
 * <p>
 * Contains the search results along with metadata about the query.
 *
 * @param results the list of location results (may be empty, never null)
 * @param query   the original search query string
 */
public record SearchResponse(
        List<LocationResult> results,
        String query
) {

    /**
     * Compact constructor that validates all fields.
     *
     * @param results the list of location results
     * @param query   the original search query
     * @throws NullPointerException if results or query is null
     */
    public SearchResponse {
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(query, "query must not be null");
        results = List.copyOf(results);
    }

    /**
     * Creates a SearchResponse from a list of results and the original query.
     *
     * @param results the list of location results
     * @param query   the original search query
     * @return the SearchResponse
     * @throws NullPointerException if results or query is null
     */
    public static SearchResponse of(List<LocationResult> results, String query) {
        return new SearchResponse(results, query);
    }

    /**
     * Creates an empty SearchResponse for a query with no results.
     *
     * @param query the original search query
     * @return the empty SearchResponse
     * @throws NullPointerException if query is null
     */
    public static SearchResponse empty(String query) {
        return new SearchResponse(List.of(), query);
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
