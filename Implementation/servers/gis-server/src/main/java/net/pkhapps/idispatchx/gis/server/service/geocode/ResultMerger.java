package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.gis.server.api.geocode.AddressResult;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressSource;
import net.pkhapps.idispatchx.gis.server.api.geocode.LocationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Merges, deduplicates, and limits scored geocoding results.
 * <p>
 * Deduplication rules:
 * <ul>
 *   <li>Address results with the same street name and number are deduplicated,
 *       preferring {@link AddressSource#ADDRESS_POINT} over {@link AddressSource#ROAD_SEGMENT}</li>
 *   <li>Results of the same type with coordinates within ~10 m (0.0001 degrees)
 *       are considered duplicates</li>
 * </ul>
 */
final class ResultMerger {

    private static final double PROXIMITY_THRESHOLD = 0.0001;

    List<LocationResult> merge(List<ScoredResult> results, int limit) {
        var sorted = results.stream()
                .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
                .toList();

        var accepted = new ArrayList<LocationResult>();

        for (var candidate : sorted) {
            if (accepted.size() >= limit) {
                break;
            }

            var result = candidate.result();

            if (result instanceof AddressResult candidateAddr) {
                if (isDuplicateAddress(accepted, candidateAddr)) {
                    continue;
                }
            }

            if (isProximityDuplicate(accepted, result)) {
                continue;
            }

            accepted.add(result);
        }

        return List.copyOf(accepted);
    }

    private boolean isDuplicateAddress(List<LocationResult> accepted, AddressResult candidate) {
        for (var existing : accepted) {
            if (existing instanceof AddressResult existingAddr) {
                if (sameStreetAndNumber(existingAddr, candidate)) {
                    // If the existing one is ADDRESS_POINT, skip the candidate
                    if (existingAddr.source() == AddressSource.ADDRESS_POINT) {
                        return true;
                    }
                    // If the candidate is ADDRESS_POINT and existing is ROAD_SEGMENT, replace
                    if (candidate.source() == AddressSource.ADDRESS_POINT
                            && existingAddr.source() == AddressSource.ROAD_SEGMENT) {
                        accepted.set(accepted.indexOf(existingAddr), candidate);
                        return true;
                    }
                    // Same source, skip duplicate
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sameStreetAndNumber(AddressResult a, AddressResult b) {
        var nameA = a.name().anyValue().orElse("");
        var nameB = b.name().anyValue().orElse("");
        return nameA.equalsIgnoreCase(nameB) && a.number().equals(b.number());
    }

    private boolean isProximityDuplicate(List<LocationResult> accepted, LocationResult candidate) {
        for (var existing : accepted) {
            if (existing.getClass() == candidate.getClass()) {
                var dx = Math.abs(existing.coordinates().longitude() - candidate.coordinates().longitude());
                var dy = Math.abs(existing.coordinates().latitude() - candidate.coordinates().latitude());
                if (dx < PROXIMITY_THRESHOLD && dy < PROXIMITY_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }
}
