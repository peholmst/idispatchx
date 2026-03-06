package net.pkhapps.idispatchx.gis.server.service.geocode;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stateless parser that classifies a raw search string into a {@link ParsedQuery} variant.
 * <p>
 * Detection order:
 * <ol>
 *   <li><strong>Intersection</strong> — contains a separator ({@code /}, {@code &}, or the words
 *       {@code and}, {@code ja}, {@code och}) with letter-containing parts on both sides</li>
 *   <li><strong>Address</strong> — ends with a number, preceded by a non-blank street name</li>
 *   <li><strong>Street</strong> — contains whitespace (multiple words)</li>
 *   <li><strong>Place</strong> — fallback for single-word queries</li>
 * </ol>
 */
final class QueryParser {

    private static final Pattern INTERSECTION_SEPARATOR =
            Pattern.compile("\\s*/\\s*|\\s*&\\s*|\\s+(?:and|ja|och)\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("^(.+?)\\s+(\\d+)\\s*$");

    private static final Pattern CONTAINS_LETTER =
            Pattern.compile("[a-zA-ZåäöÅÄÖ]");

    /**
     * Parses a raw search query into a typed {@link ParsedQuery}.
     *
     * @param query the raw search string (must not be null or blank)
     * @return the parsed query
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is blank
     */
    ParsedQuery parse(String query) {
        Objects.requireNonNull(query, "query must not be null");
        var trimmed = query.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        // 1. Intersection detection
        var parts = INTERSECTION_SEPARATOR.split(trimmed, -1);
        if (parts.length == 2) {
            var roadA = parts[0].trim();
            var roadB = parts[1].trim();
            if (!roadA.isBlank() && !roadB.isBlank()
                    && CONTAINS_LETTER.matcher(roadA).find()
                    && CONTAINS_LETTER.matcher(roadB).find()) {
                return new ParsedQuery.IntersectionQuery(roadA, roadB);
            }
        }

        // 2. Address detection
        var addressMatcher = ADDRESS_PATTERN.matcher(trimmed);
        if (addressMatcher.matches()) {
            var streetName = addressMatcher.group(1).replaceAll("[/&]+$", "").trim();
            var number = Integer.parseInt(addressMatcher.group(2));
            if (!streetName.isBlank() && number >= 1) {
                return new ParsedQuery.AddressQuery(streetName, number);
            }
        }

        // 3. Street detection (multiple words)
        if (trimmed.contains(" ")) {
            return new ParsedQuery.StreetQuery(trimmed);
        }

        // 4. Fallback to place
        return new ParsedQuery.PlaceQuery(trimmed);
    }
}
