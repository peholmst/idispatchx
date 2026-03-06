package net.pkhapps.idispatchx.gis.server.service.geocode;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressResult;
import net.pkhapps.idispatchx.gis.server.api.geocode.AddressSource;
import net.pkhapps.idispatchx.gis.server.api.geocode.IntersectionResult;
import net.pkhapps.idispatchx.gis.server.api.geocode.PlaceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultMergerTest {

    private final ResultMerger merger = new ResultMerger();

    private static final Municipality MUNICIPALITY = Municipality.of(
            MunicipalityCode.of("091"),
            MultilingualName.ofFinnishFields("Helsinki", "Helsingfors", null, null, null));

    private static final MultilingualName STREET_NAME =
            MultilingualName.ofFinnishFields("Mannerheimintie", "Mannerheimvägen", null, null, null);

    // ==================== Score sorting ====================

    @Test
    void merge_sortsResultsByScoreDescending() {
        var low = scoredAddress("5", Coordinates.Epsg4326.of(60.17, 24.93), AddressSource.ADDRESS_POINT, 0.5);
        var high = scoredAddress("7", Coordinates.Epsg4326.of(60.18, 24.94), AddressSource.ADDRESS_POINT, 0.9);
        var mid = scoredAddress("9", Coordinates.Epsg4326.of(60.19, 24.95), AddressSource.ADDRESS_POINT, 0.7);

        var results = merger.merge(List.of(low, high, mid), 10);

        assertEquals(3, results.size());
        assertEquals("7", ((AddressResult) results.get(0)).number());
        assertEquals("9", ((AddressResult) results.get(1)).number());
        assertEquals("5", ((AddressResult) results.get(2)).number());
    }

    // ==================== Limit ====================

    @Test
    void merge_limitsResultCount() {
        var a = scoredAddress("1", Coordinates.Epsg4326.of(60.17, 24.93), AddressSource.ADDRESS_POINT, 0.9);
        var b = scoredAddress("2", Coordinates.Epsg4326.of(60.18, 24.94), AddressSource.ADDRESS_POINT, 0.8);
        var c = scoredAddress("3", Coordinates.Epsg4326.of(60.19, 24.95), AddressSource.ADDRESS_POINT, 0.7);

        var results = merger.merge(List.of(a, b, c), 2);
        assertEquals(2, results.size());
    }

    // ==================== ADDRESS_POINT > ROAD_SEGMENT ====================

    @Test
    void merge_addressPointPreferredOverRoadSegment_sameAddress() {
        var addressPoint = scoredAddress("5", Coordinates.Epsg4326.of(60.17, 24.93), AddressSource.ADDRESS_POINT, 0.85);
        var roadSegment = scoredAddress("5", Coordinates.Epsg4326.of(60.171, 24.931), AddressSource.ROAD_SEGMENT, 0.90);

        // roadSegment has higher score but ADDRESS_POINT should win for same address
        var results = merger.merge(List.of(roadSegment, addressPoint), 10);

        assertEquals(1, results.size());
        var addr = (AddressResult) results.getFirst();
        assertEquals(AddressSource.ADDRESS_POINT, addr.source());
    }

    @Test
    void merge_addressPointReplacesRoadSegment_whenAddressPointComesSecond() {
        var roadSegment = scoredAddress("5", Coordinates.Epsg4326.of(60.171, 24.931), AddressSource.ROAD_SEGMENT, 0.95);
        var addressPoint = scoredAddress("5", Coordinates.Epsg4326.of(60.17, 24.93), AddressSource.ADDRESS_POINT, 0.80);

        var results = merger.merge(List.of(roadSegment, addressPoint), 10);

        assertEquals(1, results.size());
        var addr = (AddressResult) results.getFirst();
        assertEquals(AddressSource.ADDRESS_POINT, addr.source());
    }

    // ==================== Proximity dedup ====================

    @Test
    void merge_proximityDedup_sameType() {
        var a = scoredPlace("Senaatintori", Coordinates.Epsg4326.of(60.1699, 24.9527), 0.9);
        var b = scoredPlace("Senatstorget", Coordinates.Epsg4326.of(60.16995, 24.95275), 0.85);

        var results = merger.merge(List.of(a, b), 10);

        assertEquals(1, results.size());
    }

    @Test
    void merge_noProximityDedup_differentTypes() {
        var addr = scoredAddress("5", Coordinates.Epsg4326.of(60.17, 24.93), AddressSource.ADDRESS_POINT, 0.9);
        var place = scoredPlace("Senaatintori", Coordinates.Epsg4326.of(60.17, 24.93), 0.85);

        var results = merger.merge(List.of(addr, place), 10);

        assertEquals(2, results.size());
    }

    @Test
    void merge_noProximityDedup_farApart() {
        var a = scoredPlace("Helsinki", Coordinates.Epsg4326.of(60.17, 24.93), 0.9);
        var b = scoredPlace("Espoo", Coordinates.Epsg4326.of(60.20, 24.65), 0.85);

        var results = merger.merge(List.of(a, b), 10);

        assertEquals(2, results.size());
    }

    // ==================== Empty input ====================

    @Test
    void merge_emptyInput_returnsEmptyList() {
        var results = merger.merge(List.of(), 10);
        assertTrue(results.isEmpty());
    }

    // ==================== helpers ====================

    private ScoredResult scoredAddress(String number, Coordinates.Epsg4326 coords, AddressSource source, double score) {
        return new ScoredResult(new AddressResult(STREET_NAME, number, MUNICIPALITY, coords, source), score);
    }

    private ScoredResult scoredPlace(String nameFi, Coordinates.Epsg4326 coords, double score) {
        return new ScoredResult(new PlaceResult(
                MultilingualName.ofFinnishFields(nameFi, null, null, null, null),
                48111, MUNICIPALITY, coords), score);
    }
}
