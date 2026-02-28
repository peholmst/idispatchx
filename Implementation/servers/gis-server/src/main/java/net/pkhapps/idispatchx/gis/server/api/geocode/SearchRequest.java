package net.pkhapps.idispatchx.gis.server.api.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Request DTO for geocoding search operations.
 * <p>
 * This is a self-validating DTO that validates all input parameters in its
 * compact constructor. Invalid parameters will result in {@link IllegalArgumentException}.
 * <p>
 * The request supports:
 * <ul>
 *   <li>{@code query} - The search string (required, 3-200 characters)</li>
 *   <li>{@code limit} - Maximum results to return (optional, 1-50, default 20)</li>
 *   <li>{@code municipalityCode} - Filter by municipality (optional, 3-digit code)</li>
 * </ul>
 *
 * @param query            the search query string (minimum 3 characters, maximum 200)
 * @param limit            the maximum number of results to return (1-50, defaults to 20)
 * @param municipalityCode optional municipality code filter
 */
public record SearchRequest(
        String query,
        int limit,
        @Nullable MunicipalityCode municipalityCode
) {

    /**
     * Minimum length for search queries.
     */
    public static final int MIN_QUERY_LENGTH = 3;

    /**
     * Maximum length for search queries.
     */
    public static final int MAX_QUERY_LENGTH = 200;

    /**
     * Minimum value for the limit parameter.
     */
    public static final int MIN_LIMIT = 1;

    /**
     * Maximum value for the limit parameter.
     */
    public static final int MAX_LIMIT = 50;

    /**
     * Default value for the limit parameter.
     */
    public static final int DEFAULT_LIMIT = 20;

    /**
     * Compact constructor that validates all parameters.
     *
     * @param query            the search query string
     * @param limit            the maximum number of results
     * @param municipalityCode optional municipality code filter
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is too short, too long, or if limit is out of range
     */
    public SearchRequest {
        Objects.requireNonNull(query, "query must not be null");
        if (query.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
    }

    /**
     * Creates a SearchRequest from raw query parameters.
     * <p>
     * This factory method handles the conversion from nullable String parameters
     * (as received from HTTP query parameters) to the validated record.
     *
     * @param query        the search query string
     * @param limitStr     the limit as a string, or null for default
     * @param municipality the municipality code as a string, or null for no filter
     * @return the validated SearchRequest
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static SearchRequest of(String query, @Nullable String limitStr, @Nullable String municipality) {
        int limit = DEFAULT_LIMIT;
        if (limitStr != null && !limitStr.isBlank()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit must be a valid integer");
            }
        }

        MunicipalityCode municipalityCode = null;
        if (municipality != null && !municipality.isBlank()) {
            municipalityCode = MunicipalityCode.of(municipality);
        }

        return new SearchRequest(query, limit, municipalityCode);
    }

    /**
     * Creates a SearchRequest with required parameters only, using defaults for optional ones.
     *
     * @param query the search query string
     * @return the validated SearchRequest with default limit and no municipality filter
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is invalid
     */
    public static SearchRequest of(String query) {
        return new SearchRequest(query, DEFAULT_LIMIT, null);
    }

    /**
     * Returns the municipality code filter as an Optional.
     *
     * @return the municipality code, or empty if not specified
     */
    public Optional<MunicipalityCode> optionalMunicipalityCode() {
        return Optional.ofNullable(municipalityCode);
    }
}
