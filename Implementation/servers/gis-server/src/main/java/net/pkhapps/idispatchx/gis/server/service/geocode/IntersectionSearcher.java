package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.IntersectionResult;
import net.pkhapps.idispatchx.gis.server.repository.RoadSegmentRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Searches road intersections and maps results to {@link ScoredResult} entries.
 */
public final class IntersectionSearcher {

    private final RoadSegmentRepository repository;

    public IntersectionSearcher(RoadSegmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    List<ScoredResult> search(String query, int limit, @Nullable MunicipalityCode municipality) {
        return repository.searchIntersections(query, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .filter(r -> !r.roadA().isEmpty() && !r.roadB().isEmpty())
                .map(r -> new ScoredResult(
                        new IntersectionResult(
                                r.roadA(),
                                r.roadB(),
                                r.municipality(),
                                r.coordinates()),
                        r.similarityScore()))
                .toList();
    }
}
