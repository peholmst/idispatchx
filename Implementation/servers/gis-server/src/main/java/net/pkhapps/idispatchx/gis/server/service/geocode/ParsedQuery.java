package net.pkhapps.idispatchx.gis.server.service.geocode;

import java.util.Objects;

/**
 * A sealed interface representing a parsed geocoding query.
 * <p>
 * The query parser analyzes a raw search string and classifies it into one of
 * four query types: address, street, intersection, or place.
 *
 * @see QueryParser
 */
sealed interface ParsedQuery permits
        ParsedQuery.AddressQuery,
        ParsedQuery.StreetQuery,
        ParsedQuery.IntersectionQuery,
        ParsedQuery.PlaceQuery {

    /**
     * A query for a specific street address (e.g., "Mannerheimintie 5").
     *
     * @param streetName the street name (non-blank)
     * @param number     the address number (must be at least 1)
     */
    record AddressQuery(String streetName, int number) implements ParsedQuery {
        public AddressQuery {
            Objects.requireNonNull(streetName, "streetName must not be null");
            if (streetName.isBlank()) {
                throw new IllegalArgumentException("streetName must not be blank");
            }
            if (number < 1) {
                throw new IllegalArgumentException("number must be at least 1, got " + number);
            }
        }
    }

    /**
     * A query for a street by name without a specific number (e.g., "Mannerheimintie").
     *
     * @param name the street name (non-blank)
     */
    record StreetQuery(String name) implements ParsedQuery {
        public StreetQuery {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    /**
     * A query for a road intersection (e.g., "Mannerheimintie / Aleksanterinkatu").
     *
     * @param roadA the first road name (non-blank)
     * @param roadB the second road name (non-blank)
     */
    record IntersectionQuery(String roadA, String roadB) implements ParsedQuery {
        public IntersectionQuery {
            Objects.requireNonNull(roadA, "roadA must not be null");
            Objects.requireNonNull(roadB, "roadB must not be null");
            if (roadA.isBlank()) {
                throw new IllegalArgumentException("roadA must not be blank");
            }
            if (roadB.isBlank()) {
                throw new IllegalArgumentException("roadB must not be blank");
            }
        }
    }

    /**
     * A query for a named place (e.g., "Helsinki").
     *
     * @param name the place name (non-blank)
     */
    record PlaceQuery(String name) implements ParsedQuery {
        public PlaceQuery {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
