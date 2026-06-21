package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.repository.NamedPlaceRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Searches named places and maps results to {@link ScoredResult} entries.
 */
final class NamedPlaceSearcher {

    private final NamedPlaceRepository repository;

    public NamedPlaceSearcher(NamedPlaceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    List<ScoredResult> search(String query, int limit, @Nullable MunicipalityCode municipality) {
        return repository.search(query, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .filter(r -> !r.name().isEmpty())
                .map(r -> new ScoredResult(
                        new PlaceResult(
                                r.name(),
                                r.placeClass(),
                                Objects.requireNonNull(r.municipality()),
                                r.coordinates()),
                        r.similarityScore()))
                .toList();
    }
}
