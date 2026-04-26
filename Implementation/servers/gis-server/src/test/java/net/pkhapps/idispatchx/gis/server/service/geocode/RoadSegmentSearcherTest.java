package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.repository.InterpolatedAddressResult;
import net.pkhapps.idispatchx.gis.server.repository.RoadNamePoint;
import net.pkhapps.idispatchx.gis.server.repository.RoadSegmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class RoadSegmentSearcherTest {

    @Mock
    private RoadSegmentRepository repository;

    private static final MunicipalityCode MUNI_CODE = MunicipalityCode.of("091");
    private static final Municipality MUNICIPALITY = Municipality.of(
            MUNI_CODE, MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.1699, 24.9384);

    @Test
    void searchAddress_found_returnsSingleResult() {
        var searcher = new RoadSegmentSearcher(repository);
        var interpolated = new InterpolatedAddressResult(
                MultilingualName.ofFinnishFields("Mannerheimintie", "Mannerheimvägen", null, null, null),
                "5", MUNICIPALITY, COORDS);

        when(repository.interpolateAddress("Mannerheimintie", 5, null))
                .thenReturn(Optional.of(interpolated));

        var results = searcher.searchAddress("Mannerheimintie", 5, null);

        assertEquals(1, results.size());
        var scored = results.getFirst();
        assertEquals(0.9, scored.score());
        assertInstanceOf(AddressResult.class, scored.result());

        var addr = (AddressResult) scored.result();
        assertEquals("5", addr.number());
        assertEquals(AddressSource.ROAD_SEGMENT, addr.source());
    }

    @Test
    void searchAddress_notFound_returnsEmptyList() {
        var searcher = new RoadSegmentSearcher(repository);
        when(repository.interpolateAddress("NoSuchRoad", 1, null))
                .thenReturn(Optional.empty());

        var results = searcher.searchAddress("NoSuchRoad", 1, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchAddress_nullMunicipality_returnsEmptyList() {
        var searcher = new RoadSegmentSearcher(repository);
        var interpolated = new InterpolatedAddressResult(
                MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                "5", null, COORDS);

        when(repository.interpolateAddress("Mannerheimintie", 5, null))
                .thenReturn(Optional.of(interpolated));

        var results = searcher.searchAddress("Mannerheimintie", 5, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchByName_found_returnsSingleResultWithNullNumber() {
        var searcher = new RoadSegmentSearcher(repository);
        var point = new RoadNamePoint(
                MultilingualName.ofFinnishFields("Mannerheimintie", "Mannerheimvägen", null, null, null),
                MUNICIPALITY, COORDS, 0.95);

        when(repository.findRepresentativePointsByName("Mannerheimintie", 5, null))
                .thenReturn(List.of(point));

        var results = searcher.searchByName("Mannerheimintie", 5, null);

        assertEquals(1, results.size());
        var scored = results.getFirst();
        assertEquals(0.85, scored.score());
        assertInstanceOf(AddressResult.class, scored.result());

        var addr = (AddressResult) scored.result();
        assertNull(addr.number());
        assertEquals(AddressSource.ROAD_SEGMENT, addr.source());
    }

    @Test
    void searchByName_notFound_returnsEmptyList() {
        var searcher = new RoadSegmentSearcher(repository);
        when(repository.findRepresentativePointsByName("NoSuchRoad", 5, null))
                .thenReturn(List.of());

        var results = searcher.searchByName("NoSuchRoad", 5, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchByName_nullMunicipality_returnsEmptyList() {
        var searcher = new RoadSegmentSearcher(repository);
        var point = new RoadNamePoint(
                MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                null, COORDS, 0.9);

        when(repository.findRepresentativePointsByName("Mannerheimintie", 5, null))
                .thenReturn(List.of(point));

        var results = searcher.searchByName("Mannerheimintie", 5, null);
        assertTrue(results.isEmpty());
    }
}
