package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.repository.NamedPlaceRepository;
import net.pkhapps.idispatchx.gis.server.repository.NamedPlaceSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamedPlaceSearcherTest {

    @Mock
    private NamedPlaceRepository repository;

    private static final MunicipalityCode MUNI_CODE = MunicipalityCode.of("091");
    private static final Municipality MUNICIPALITY = Municipality.of(
            MUNI_CODE, MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.1699, 24.9384);

    @Test
    void search_validResults_mapsToPlaceResults() {
        var searcher = new NamedPlaceSearcher(repository);
        var searchResult = new NamedPlaceSearchResult(
                100L,
                MultilingualName.ofFinnishFields("Senaatintori", "Senatstorget", null, null, null),
                48111, MUNICIPALITY, COORDS, 0.75);

        when(repository.search("Senaatintori", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Senaatintori", 10, null);

        assertEquals(1, results.size());
        var scored = results.getFirst();
        assertEquals(0.75, scored.score());
        assertInstanceOf(PlaceResult.class, scored.result());

        var place = (PlaceResult) scored.result();
        assertEquals(48111, place.placeClass());
        assertEquals(MUNICIPALITY, place.municipality());
    }

    @Test
    void search_nullMunicipality_filtered() {
        var searcher = new NamedPlaceSearcher(repository);
        var searchResult = new NamedPlaceSearchResult(
                100L,
                MultilingualName.ofFinnishFields("Senaatintori", null, null, null, null),
                48111, null, COORDS, 0.75);

        when(repository.search("Senaatintori", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Senaatintori", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_emptyName_filtered() {
        var searcher = new NamedPlaceSearcher(repository);
        var searchResult = new NamedPlaceSearchResult(
                100L,
                MultilingualName.empty(),
                48111, MUNICIPALITY, COORDS, 0.75);

        when(repository.search("test", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("test", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_emptyRepositoryResult_returnsEmptyList() {
        var searcher = new NamedPlaceSearcher(repository);
        when(repository.search("nothing", 10, null)).thenReturn(List.of());

        var results = searcher.search("nothing", 10, null);
        assertTrue(results.isEmpty());
    }
}
