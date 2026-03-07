package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.gis.server.api.geocode.LocationResult;

import java.util.Objects;

/**
 * A location result paired with a relevance score for ranking during result merging.
 *
 * @param result the location result
 * @param score  the relevance score (0.0 to 1.0, higher is better)
 */
record ScoredResult(LocationResult result, double score) {

    ScoredResult {
        Objects.requireNonNull(result, "result must not be null");
    }
}
