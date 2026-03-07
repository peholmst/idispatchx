package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressResult;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressSource;
import net.pkhapps.idispatchx.gis.server.repository.AddressPointRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Searches address points and maps results to {@link ScoredResult} entries
 * with {@link AddressSource#ADDRESS_POINT} source.
 */
public final class AddressPointSearcher {

    private final AddressPointRepository repository;

    public AddressPointSearcher(AddressPointRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    List<ScoredResult> search(String query, int limit, @Nullable MunicipalityCode municipality) {
        return repository.search(query, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .filter(r -> r.number() != null && !r.number().isBlank())
                .map(r -> new ScoredResult(
                        new AddressResult(
                                r.streetName(),
                                r.number(),
                                r.municipality(),
                                r.coordinates(),
                                AddressSource.ADDRESS_POINT),
                        r.similarityScore()))
                .toList();
    }

    List<ScoredResult> searchByStreetAndNumber(String streetName, int number, int limit,
                                                @Nullable MunicipalityCode municipality) {
        var numberStr = String.valueOf(number);
        return repository.search(streetName, limit, municipality).stream()
                .filter(r -> r.municipality() != null)
                .filter(r -> numberStr.equals(r.number()))
                .map(r -> new ScoredResult(
                        new AddressResult(
                                r.streetName(),
                                r.number(),
                                r.municipality(),
                                r.coordinates(),
                                AddressSource.ADDRESS_POINT),
                        r.similarityScore()))
                .toList();
    }
}
