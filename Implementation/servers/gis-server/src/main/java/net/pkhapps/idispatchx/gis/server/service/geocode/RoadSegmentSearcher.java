package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.repository.RoadSegmentRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Searches for addresses by interpolating along road segments.
 * <p>
 * Results are assigned a fixed score of 0.9 — high, but below exact address point
 * matches — so that address points take priority during result merging.
 */
final class RoadSegmentSearcher {

    private static final double INTERPOLATED_SCORE = 0.9;
    private static final double NAME_ONLY_SCORE = 0.85;

    private final RoadSegmentRepository repository;

    public RoadSegmentSearcher(RoadSegmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    List<ScoredResult> searchAddress(String streetName, int number, @Nullable MunicipalityCode municipality) {
        return repository.interpolateAddress(streetName, number, municipality)
                .filter(r -> r.municipality() != null)
                .map(r -> new ScoredResult(
                        new AddressResult(
                                r.streetName(),
                                r.number(),
                                r.municipality(),
                                r.coordinates(),
                                AddressSource.ROAD_SEGMENT),
                        INTERPOLATED_SCORE))
                .map(List::of)
                .orElse(List.of());
    }

    List<ScoredResult> searchByName(String streetName, int limit, @Nullable MunicipalityCode municipality) {
        return repository.findRepresentativePointsByName(streetName, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .map(r -> new ScoredResult(
                        new AddressResult(
                                r.roadName(),
                                null,
                                r.municipality(),
                                r.coordinates(),
                                AddressSource.ROAD_SEGMENT),
                        NAME_ONLY_SCORE))
                .toList();
    }
}
