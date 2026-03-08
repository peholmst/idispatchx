package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSegmentRepositoryIntegrationTest extends IntegrationTestBase {

    private RoadSegmentRepository repository;

    @BeforeEach
    void setUp() {
        truncateAllTables();
        repository = new RoadSegmentRepository(dsl);

        dsl.execute("""
                INSERT INTO gis.municipality (municipality_code, name_fi, name_sv)
                VALUES ('091', 'Helsinki', 'Helsingfors'),
                       ('049', 'Espoo', 'Esbo')
                """);

        // Two parallel segments of "Mannerheimintie" in Helsinki
        // Segment 1: addresses 1-49 right, 2-48 left; runs north along longitude ~24.938
        // Segment 2: addresses 51-99 right, 50-98 left; continues north
        // One crossing road "Runeberginkatu" that intersects segment 1
        dsl.execute("""
                INSERT INTO gis.road_segment
                  (id, road_class, surface_type, one_way, name_fi, name_sv,
                   min_address_left, max_address_left, min_address_right, max_address_right,
                   municipality_code, geometry)
                VALUES
                  (1, 1, 1, 0, 'Mannerheimintie', 'Mannerheimvägen',
                   2, 48, 1, 49, '091',
                   ST_SetSRID(ST_MakeLine(
                     ST_MakePoint(24.9380, 60.1680),
                     ST_MakePoint(24.9385, 60.1750)
                   ), 4326)),
                  (2, 1, 1, 0, 'Mannerheimintie', 'Mannerheimvägen',
                   50, 98, 51, 99, '091',
                   ST_SetSRID(ST_MakeLine(
                     ST_MakePoint(24.9385, 60.1750),
                     ST_MakePoint(24.9390, 60.1820)
                   ), 4326)),
                  (3, 2, 1, 0, 'Runeberginkatu', null,
                   null, null, null, null, '091',
                   ST_SetSRID(ST_MakeLine(
                     ST_MakePoint(24.9350, 60.1710),
                     ST_MakePoint(24.9420, 60.1710)
                   ), 4326)),
                  (4, 2, 1, 0, 'Tapiolantie', null,
                   2, 20, 1, 19, '049',
                   ST_SetSRID(ST_MakeLine(
                     ST_MakePoint(24.8000, 60.2000),
                     ST_MakePoint(24.8100, 60.2100)
                   ), 4326))
                """);
    }

    @AfterEach
    void tearDown() {
        truncateAllTables();
    }

    // ==================== searchByName ====================

    @Test
    void searchByName_exactMatch_returnsResults() {
        var results = repository.searchByName("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
        assertTrue(results.stream()
                .anyMatch(r -> r.roadName().values().values().stream()
                        .anyMatch(v -> v.contains("Mannerheimintie"))));
    }

    @Test
    void searchByName_municipalityFilter_restrictsResults() {
        var results = repository.searchByName("Tapiolantie", 10, MunicipalityCode.of("091"));

        // Tapiolantie is in Espoo (049), not Helsinki (091)
        assertTrue(results.isEmpty());
    }

    @Test
    void searchByName_municipalityFilterMatchesCorrectMunicipality() {
        var results = repository.searchByName("Tapiolantie", 10, MunicipalityCode.of("049"));

        assertFalse(results.isEmpty());
    }

    @Test
    void searchByName_noMatch_returnsEmpty() {
        var results = repository.searchByName("Zyxwvutsrq", 10, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchByName_limitRespected() {
        var results = repository.searchByName("Mannerheimintie", 1, null);

        assertEquals(1, results.size());
    }

    @Test
    void searchByName_addressRangesIncluded() {
        var results = repository.searchByName("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
        // At least one result should have address range info
        var hasRange = results.stream().anyMatch(r ->
                r.minAddressRight() != null || r.minAddressLeft() != null);
        assertTrue(hasRange);
    }

    // ==================== interpolateAddress ====================

    @Test
    void interpolateAddress_oddNumber_interpolatesOnRightSide() {
        // 25 is odd, should use right side (1-49), in segment 1
        var result = repository.interpolateAddress("Mannerheimintie", 25, null);

        assertTrue(result.isPresent());
        var coords = result.get().coordinates();
        assertNotNull(coords);
        // Coordinates should be near Helsinki area
        assertTrue(coords.longitude() > 24.9 && coords.longitude() < 25.0);
        assertTrue(coords.latitude() > 60.1 && coords.latitude() < 60.2);
    }

    @Test
    void interpolateAddress_evenNumber_interpolatesOnLeftSide() {
        // 24 is even, should use left side (2-48), in segment 1
        var result = repository.interpolateAddress("Mannerheimintie", 24, null);

        assertTrue(result.isPresent());
        var coords = result.get().coordinates();
        assertNotNull(coords);
    }

    @Test
    void interpolateAddress_numberInSegment2_returnsResult() {
        // 75 is odd, uses right side (51-99), in segment 2
        var result = repository.interpolateAddress("Mannerheimintie", 75, null);

        assertTrue(result.isPresent());
    }

    @Test
    void interpolateAddress_numberOutOfAllRanges_returnsEmpty() {
        // 200 is not in any segment's range
        var result = repository.interpolateAddress("Mannerheimintie", 200, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void interpolateAddress_unknownRoad_returnsEmpty() {
        var result = repository.interpolateAddress("Zyxwvutsrq", 5, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void interpolateAddress_resultIncludesRoadName() {
        var result = repository.interpolateAddress("Mannerheimintie", 5, null);

        assertTrue(result.isPresent());
        assertTrue(result.get().streetName().values().values().stream()
                .anyMatch(v -> v.contains("Mannerheimintie")));
    }

    @Test
    void interpolateAddress_coordinatesRoundedTo6DecimalPlaces() {
        var result = repository.interpolateAddress("Mannerheimintie", 5, null);

        assertTrue(result.isPresent());
        var lat = result.get().coordinates().latitude();
        var lon = result.get().coordinates().longitude();
        // Check that coordinates don't have more than 6 decimal places
        assertEquals(lat, Math.round(lat * 1_000_000) / 1_000_000.0, 1e-9);
        assertEquals(lon, Math.round(lon * 1_000_000) / 1_000_000.0, 1e-9);
    }

    // ==================== searchIntersections ====================

    @Test
    void searchIntersections_intersectingRoads_returnsResult() {
        // Runeberginkatu (segment 3) crosses Mannerheimintie (segment 1)
        var results = repository.searchIntersections("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
    }

    @Test
    void searchIntersections_noIntersection_returnsEmpty() {
        // Tapiolantie (in Espoo) doesn't intersect Helsinki roads
        var results = repository.searchIntersections("Tapiolantie", 10, MunicipalityCode.of("091"));

        assertTrue(results.isEmpty());
    }

    @Test
    void searchIntersections_resultIncludesCoordinates() {
        var results = repository.searchIntersections("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
        var coords = results.get(0).coordinates();
        assertNotNull(coords);
        assertTrue(coords.longitude() > 24.0 && coords.longitude() < 25.5);
        assertTrue(coords.latitude() > 59.0 && coords.latitude() < 61.0);
    }

    @Test
    void searchIntersections_limitRespected() {
        var results = repository.searchIntersections("Mannerheimintie", 1, null);

        assertTrue(results.size() <= 1);
    }
}
