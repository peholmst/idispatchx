package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.IntersectionResult;
import net.pkhapps.idispatchx.gis.server.repository.IntersectionSearchResult;
import net.pkhapps.idispatchx.gis.server.repository.RoadSegmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntersectionSearcherTest {

    @Mock
    private RoadSegmentRepository repository;

    private static final MunicipalityCode MUNI_CODE = MunicipalityCode.of("091");
    private static final Municipality MUNICIPALITY = Municipality.of(
            MUNI_CODE, MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.1699, 24.9384);

    @Test
    void search_validResults_mapsToIntersectionResults() {
        var searcher = new IntersectionSearcher(repository);
        var roadA = MultilingualName.ofFinnishFields("Mannerheimintie", "Mannerheimvägen", null, null, null);
        var roadB = MultilingualName.ofFinnishFields("Aleksanterinkatu", "Alexandersgatan", null, null, null);
        var searchResult = new IntersectionSearchResult(roadA, roadB, MUNICIPALITY, COORDS, 0.80);

        when(repository.searchIntersections("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);

        assertEquals(1, results.size());
        var scored = results.getFirst();
        assertEquals(0.80, scored.score());
        assertInstanceOf(IntersectionResult.class, scored.result());

        var intersection = (IntersectionResult) scored.result();
        assertEquals(roadA, intersection.roadA());
        assertEquals(roadB, intersection.roadB());
    }

    @Test
    void search_nullMunicipality_filtered() {
        var searcher = new IntersectionSearcher(repository);
        var roadA = MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null);
        var roadB = MultilingualName.ofFinnishFields("Aleksanterinkatu", null, null, null, null);
        var searchResult = new IntersectionSearchResult(roadA, roadB, null, COORDS, 0.80);

        when(repository.searchIntersections("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_emptyRoadName_filtered() {
        var searcher = new IntersectionSearcher(repository);
        var roadA = MultilingualName.empty();
        var roadB = MultilingualName.ofFinnishFields("Aleksanterinkatu", null, null, null, null);
        var searchResult = new IntersectionSearchResult(roadA, roadB, MUNICIPALITY, COORDS, 0.80);

        when(repository.searchIntersections("test", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("test", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_emptyRepositoryResult_returnsEmptyList() {
        var searcher = new IntersectionSearcher(repository);
        when(repository.searchIntersections("nothing", 10, null)).thenReturn(List.of());

        var results = searcher.search("nothing", 10, null);
        assertTrue(results.isEmpty());
    }

    // ==================== searchPair ====================

    @Test
    void searchPair_matchingPair_returnsResult() {
        var searcher = new IntersectionSearcher(repository);
        var roadA = MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null);
        var roadB = MultilingualName.ofFinnishFields("Aleksanterinkatu", null, null, null, null);
        var match = new IntersectionSearchResult(roadA, roadB, MUNICIPALITY, COORDS, 0.80);

        // Also include an unrelated intersection that should be filtered out
        var roadC = MultilingualName.ofFinnishFields("Kaivokatu", null, null, null, null);
        var unrelated = new IntersectionSearchResult(roadA, roadC, MUNICIPALITY,
                Coordinates.Epsg4326.of(60.18, 24.94), 0.75);

        when(repository.searchIntersections("Mannerheimintie", 10, null))
                .thenReturn(List.of(match, unrelated));

        var results = searcher.searchPair("Mannerheimintie", "Aleksanterinkatu", 10, null);

        assertEquals(1, results.size());
        var intersection = (IntersectionResult) results.getFirst().result();
        assertEquals(roadA, intersection.roadA());
        assertEquals(roadB, intersection.roadB());
    }

    @Test
    void searchPair_noMatchForOtherRoad_returnsEmpty() {
        var searcher = new IntersectionSearcher(repository);
        var roadA = MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null);
        var roadC = MultilingualName.ofFinnishFields("Kaivokatu", null, null, null, null);
        var unrelated = new IntersectionSearchResult(roadA, roadC, MUNICIPALITY, COORDS, 0.80);

        when(repository.searchIntersections("Mannerheimintie", 10, null))
                .thenReturn(List.of(unrelated));

        var results = searcher.searchPair("Mannerheimintie", "Aleksanterinkatu", 10, null);
        assertTrue(results.isEmpty());
    }
}
