package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.SearchRequest;
import net.pkhapps.idispatchx.gis.server.api.geocode.SearchResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates geocoding by parsing the query, dispatching parallel searches
 * across multiple data sources using virtual threads, and merging the results.
 */
public final class GeocodeService {

    private static final Logger log = LoggerFactory.getLogger(GeocodeService.class);

    private final AddressPointSearcher addressPointSearcher;
    private final RoadSegmentSearcher roadSegmentSearcher;
    private final NamedPlaceSearcher namedPlaceSearcher;
    private final IntersectionSearcher intersectionSearcher;
    private final QueryParser queryParser = new QueryParser();
    private final ResultMerger resultMerger = new ResultMerger();

    /**
     * Creates a new geocode service with the given searchers.
     *
     * @param addressPointSearcher  the address point searcher
     * @param roadSegmentSearcher   the road segment searcher
     * @param namedPlaceSearcher    the named place searcher
     * @param intersectionSearcher  the intersection searcher
     */
    public GeocodeService(AddressPointSearcher addressPointSearcher,
                          RoadSegmentSearcher roadSegmentSearcher,
                          NamedPlaceSearcher namedPlaceSearcher,
                          IntersectionSearcher intersectionSearcher) {
        this.addressPointSearcher = Objects.requireNonNull(addressPointSearcher);
        this.roadSegmentSearcher = Objects.requireNonNull(roadSegmentSearcher);
        this.namedPlaceSearcher = Objects.requireNonNull(namedPlaceSearcher);
        this.intersectionSearcher = Objects.requireNonNull(intersectionSearcher);
    }

    /**
     * Performs a geocoding search based on the given request.
     *
     * @param request the search request
     * @return the search response with merged results
     * @throws DatabaseUnavailableException if database access fails
     */
    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        var query = request.query();
        var limit = request.limit();
        @Nullable MunicipalityCode municipality = request.municipalityCode();

        var parsedQuery = queryParser.parse(query);
        log.info("Geocode search: queryLength={}, limit={}, municipality={}, parsedType={}",
                query.length(), limit, municipality, parsedQuery.getClass().getSimpleName());

        try {
            var searchTasks = dispatchSearches(parsedQuery, limit, municipality);
            var allResults = collectResults(searchTasks);
            var merged = resultMerger.merge(allResults, limit);

            log.info("Geocode search completed: resultCount={}", merged.size());
            return SearchResponse.of(merged, query);
        } catch (RuntimeException e) {
            throw new DatabaseUnavailableException("Geocoding search failed", e);
        }
    }

    private List<Future<List<ScoredResult>>> dispatchSearches(ParsedQuery parsedQuery, int limit,
                                                               @Nullable MunicipalityCode municipality) {
        var futures = new ArrayList<Future<List<ScoredResult>>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            switch (parsedQuery) {
                case ParsedQuery.AddressQuery aq -> {
                    futures.add(executor.submit(() ->
                            addressPointSearcher.searchByStreetAndNumber(
                                    aq.streetName(), aq.number(), limit, municipality)));
                    futures.add(executor.submit(() ->
                            roadSegmentSearcher.searchAddress(aq.streetName(), aq.number(), municipality)));
                }
                case ParsedQuery.StreetQuery sq -> {
                    futures.add(executor.submit(() ->
                            addressPointSearcher.search(sq.name(), limit, municipality)));
                    futures.add(executor.submit(() ->
                            namedPlaceSearcher.search(sq.name(), limit, municipality)));
                }
                case ParsedQuery.IntersectionQuery iq -> {
                    futures.add(executor.submit(() ->
                            intersectionSearcher.searchPair(iq.roadA(), iq.roadB(), limit, municipality)));
                    futures.add(executor.submit(() ->
                            intersectionSearcher.searchPair(iq.roadB(), iq.roadA(), limit, municipality)));
                }
                case ParsedQuery.PlaceQuery pq -> {
                    futures.add(executor.submit(() ->
                            namedPlaceSearcher.search(pq.name(), limit, municipality)));
                    futures.add(executor.submit(() ->
                            addressPointSearcher.search(pq.name(), limit, municipality)));
                }
            }
        }
        // The try-with-resources will wait for all tasks to complete

        return futures;
    }

    private List<ScoredResult> collectResults(List<Future<List<ScoredResult>>> futures) {
        var allResults = new ArrayList<ScoredResult>();
        for (var future : futures) {
            try {
                allResults.addAll(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Search interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Search task failed", e.getCause());
            }
        }
        return allResults;
    }
}
