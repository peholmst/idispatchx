package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.repository.RoadSegmentRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Searches road intersections and maps results to {@link ScoredResult} entries.
 */
final class IntersectionSearcher {

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
                                Objects.requireNonNull(r.municipality()),
                                r.coordinates()),
                        r.similarityScore()))
                .toList();
    }

    List<ScoredResult> searchPair(String roadA, String roadB, int limit,
                                   @Nullable MunicipalityCode municipality) {
        return repository.searchIntersections(roadA, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .filter(r -> !r.roadA().isEmpty() && !r.roadB().isEmpty())
                .filter(r -> nameMatchesAny(r.roadA(), roadB) || nameMatchesAny(r.roadB(), roadB))
                .map(r -> new ScoredResult(
                        new IntersectionResult(
                                r.roadA(),
                                r.roadB(),
                                Objects.requireNonNull(r.municipality()),
                                r.coordinates()),
                        r.similarityScore()))
                .toList();
    }

    private boolean nameMatchesAny(MultilingualName name, String query) {
        return name.values().values().stream()
                .anyMatch(v -> v.toLowerCase().contains(query.toLowerCase()));
    }
}
