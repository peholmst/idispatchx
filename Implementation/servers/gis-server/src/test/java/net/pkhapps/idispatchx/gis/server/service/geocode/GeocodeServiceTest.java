package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodeServiceTest {

    @Mock private AddressPointSearcher addressPointSearcher;
    @Mock private RoadSegmentSearcher roadSegmentSearcher;
    @Mock private NamedPlaceSearcher namedPlaceSearcher;
    @Mock private IntersectionSearcher intersectionSearcher;

    private static final MunicipalityCode MUNI = MunicipalityCode.of("091");
    private static final Municipality HELSINKI = Municipality.of(
            MUNI, MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.1699, 24.9384);
    private static final MultilingualName STREET_NAME = MultilingualName.ofFinnishFields(
            "Mannerheimintie", "Mannerheimvägen", null, null, null);

    @Test
    void search_streetQuery_dispatchesRoadSegmentNameSearch() {
        var service = new GeocodeService(addressPointSearcher, roadSegmentSearcher,
                namedPlaceSearcher, intersectionSearcher);
        var roadResult = new ScoredResult(
                new AddressResult(STREET_NAME, null, HELSINKI, COORDS, AddressSource.ROAD_SEGMENT), 0.85);

        when(addressPointSearcher.search(eq("Iso Roobertinkatu"), anyInt(), isNull()))
                .thenReturn(List.of());
        when(namedPlaceSearcher.search(eq("Iso Roobertinkatu"), anyInt(), isNull()))
                .thenReturn(List.of());
        when(roadSegmentSearcher.searchByName(eq("Iso Roobertinkatu"), anyInt(), isNull()))
                .thenReturn(List.of(roadResult));

        var response = service.search(new SearchRequest("Iso Roobertinkatu", 10, null));

        verify(roadSegmentSearcher).searchByName(eq("Iso Roobertinkatu"), anyInt(), isNull());
        assertEquals(1, response.results().size());
    }

    @Test
    void search_placeQuery_dispatchesRoadSegmentNameSearch() {
        var service = new GeocodeService(addressPointSearcher, roadSegmentSearcher,
                namedPlaceSearcher, intersectionSearcher);
        var roadResult = new ScoredResult(
                new AddressResult(STREET_NAME, null, HELSINKI, COORDS, AddressSource.ROAD_SEGMENT), 0.85);

        when(namedPlaceSearcher.search(eq("Mannerheimintie"), anyInt(), isNull()))
                .thenReturn(List.of());
        when(addressPointSearcher.search(eq("Mannerheimintie"), anyInt(), isNull()))
                .thenReturn(List.of());
        when(roadSegmentSearcher.searchByName(eq("Mannerheimintie"), anyInt(), isNull()))
                .thenReturn(List.of(roadResult));

        var response = service.search(new SearchRequest("Mannerheimintie", 10, null));

        verify(roadSegmentSearcher).searchByName(eq("Mannerheimintie"), anyInt(), isNull());
        assertEquals(1, response.results().size());
    }

    @Test
    void search_addressQuery_doesNotDispatchRoadSegmentNameSearch() {
        var service = new GeocodeService(addressPointSearcher, roadSegmentSearcher,
                namedPlaceSearcher, intersectionSearcher);

        when(addressPointSearcher.searchByStreetAndNumber(any(), anyInt(), anyInt(), isNull()))
                .thenReturn(List.of());
        when(roadSegmentSearcher.searchAddress(any(), anyInt(), isNull()))
                .thenReturn(List.of());

        service.search(new SearchRequest("Mannerheimintie 5", 10, null));

        verify(roadSegmentSearcher, never()).searchByName(any(), anyInt(), isNull());
    }
}
