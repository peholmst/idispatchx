package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressResult;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressSource;
import net.pkhapps.idispatchx.gis.server.repository.AddressPointRepository;
import net.pkhapps.idispatchx.gis.server.repository.AddressSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressPointSearcherTest {

    @Mock
    private AddressPointRepository repository;

    private static final MunicipalityCode MUNI_CODE = MunicipalityCode.of("091");
    private static final Municipality MUNICIPALITY = Municipality.of(
            MUNI_CODE, MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.1699, 24.9384);

    @Test
    void search_validResults_mapsToAddressResults() {
        var searcher = new AddressPointSearcher(repository);
        var searchResult = new AddressSearchResult(
                1L, "5", MultilingualName.ofFinnishFields("Mannerheimintie", "Mannerheimvägen", null, null, null),
                MUNICIPALITY, COORDS, 0.85);

        when(repository.search("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);

        assertEquals(1, results.size());
        var scored = results.getFirst();
        assertEquals(0.85, scored.score());
        assertInstanceOf(AddressResult.class, scored.result());

        var addr = (AddressResult) scored.result();
        assertEquals("5", addr.number());
        assertEquals(AddressSource.ADDRESS_POINT, addr.source());
        assertEquals(MUNICIPALITY, addr.municipality());
    }

    @Test
    void search_nullMunicipality_filtered() {
        var searcher = new AddressPointSearcher(repository);
        var searchResult = new AddressSearchResult(
                1L, "5", MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                null, COORDS, 0.85);

        when(repository.search("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_nullNumber_filtered() {
        var searcher = new AddressPointSearcher(repository);
        var searchResult = new AddressSearchResult(
                1L, null, MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                MUNICIPALITY, COORDS, 0.85);

        when(repository.search("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_blankNumber_filtered() {
        var searcher = new AddressPointSearcher(repository);
        var searchResult = new AddressSearchResult(
                1L, "  ", MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                MUNICIPALITY, COORDS, 0.85);

        when(repository.search("Mannerheimintie", 10, null)).thenReturn(List.of(searchResult));

        var results = searcher.search("Mannerheimintie", 10, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_emptyRepositoryResult_returnsEmptyList() {
        var searcher = new AddressPointSearcher(repository);
        when(repository.search("nothing", 10, null)).thenReturn(List.of());

        var results = searcher.search("nothing", 10, null);
        assertTrue(results.isEmpty());
    }
}
