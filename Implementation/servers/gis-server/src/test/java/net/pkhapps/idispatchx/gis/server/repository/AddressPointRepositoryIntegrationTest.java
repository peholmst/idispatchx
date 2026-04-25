package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressPointRepositoryIntegrationTest extends IntegrationTestBase {

    private AddressPointRepository repository;

    @BeforeEach
    void setUp() {
        truncateAllTables();
        repository = new AddressPointRepository(dsl);

        dsl.execute("""
                INSERT INTO gis.municipality (municipality_code, name_fi, name_sv)
                VALUES ('091', 'Helsinki', 'Helsingfors'),
                       ('049', 'Espoo', 'Esbo')
                """);

        // Use high-precision coordinates (>6 decimal places) to match real NLS data and ensure
        // the repository rounds them correctly before constructing Coordinates.Epsg4326.
        dsl.execute("""
                INSERT INTO gis.address_point (id, number, name_fi, name_sv, municipality_code, location)
                VALUES
                  (1, '5', 'Mannerheimintie', 'Mannerheimvägen', '091',
                   ST_SetSRID(ST_MakePoint(24.93840123456, 60.16990123456), 4326)),
                  (2, '10', 'Mannerheimintie', 'Mannerheimvägen', '091',
                   ST_SetSRID(ST_MakePoint(24.93900123456, 60.17100123456), 4326)),
                  (3, '1', 'Esplanadi', null, '091',
                   ST_SetSRID(ST_MakePoint(24.94500123456, 60.16800123456), 4326)),
                  (4, '2', 'Tapiontie', null, '049',
                   ST_SetSRID(ST_MakePoint(24.80100123456, 60.21000123456), 4326))
                """);
    }

    @AfterEach
    void tearDown() {
        truncateAllTables();
    }

    @Test
    void search_exactNameMatch_returnsResults() {
        var results = repository.search("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
        assertTrue(results.stream()
                .anyMatch(r -> r.streetName().values().values().stream()
                        .anyMatch(v -> v.contains("Mannerheimintie"))));
    }

    @Test
    void search_fuzzyNameMatch_returnsResults() {
        // "Mannerheiminitie" has a typo but should still match "Mannerheimintie"
        var results = repository.search("Mannerheiminit", 10, null);

        assertFalse(results.isEmpty());
    }

    @Test
    void search_municipalityFilter_returnsOnlyMatchingMunicipality() {
        var results = repository.search("Mannerheimintie", 10, MunicipalityCode.of("091"));

        assertFalse(results.isEmpty());
        assertTrue(results.stream()
                .allMatch(r -> r.municipality() == null
                        || "091".equals(r.municipality().code().code())));
    }

    @Test
    void search_municipalityFilterExcludesOtherMunicipality() {
        // Tapiontie is in municipality 049; searching in 091 should not return it
        var results = repository.search("Tapiontie", 10, MunicipalityCode.of("091"));

        assertTrue(results.isEmpty());
    }

    @Test
    void search_coordinatesArePresent() {
        var results = repository.search("Esplanadi", 10, null);

        assertFalse(results.isEmpty());
        var coords = results.get(0).coordinates();
        assertNotNull(coords);
        // Longitude ~24.945, latitude ~60.168
        assertTrue(coords.longitude() > 24.0 && coords.longitude() < 25.5);
        assertTrue(coords.latitude() > 59.0 && coords.latitude() < 61.0);
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var results = repository.search("Zyxwvutsrq", 10, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void search_limitRespected() {
        var results = repository.search("Mannerheimintie", 1, null);

        assertEquals(1, results.size());
    }

    @Test
    void search_resultsRankedBySimilarity() {
        var results = repository.search("Mannerheimintie", 10, null);

        // All results should have positive similarity scores, and be in descending order
        double prevScore = Double.MAX_VALUE;
        for (var result : results) {
            assertTrue(result.similarityScore() <= prevScore);
            prevScore = result.similarityScore();
        }
    }

    @Test
    void search_municipalityNameIncluded() {
        var results = repository.search("Mannerheimintie", 10, null);

        assertFalse(results.isEmpty());
        var first = results.get(0);
        assertNotNull(first.municipality());
        assertEquals("091", first.municipality().code().code());
    }
}
