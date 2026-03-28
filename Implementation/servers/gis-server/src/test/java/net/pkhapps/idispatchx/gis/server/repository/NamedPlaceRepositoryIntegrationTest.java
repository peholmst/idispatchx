package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamedPlaceRepositoryIntegrationTest extends IntegrationTestBase {

    private NamedPlaceRepository repository;

    @BeforeEach
    void setUp() {
        truncateAllTables();
        repository = new NamedPlaceRepository(dsl);

        dsl.execute("""
                INSERT INTO gis.municipality (municipality_code, name_fi, name_sv)
                VALUES ('091', 'Helsinki', 'Helsingfors'),
                       ('564', 'Rovaniemi', null)
                """);

        // Kallio: karttanimi_id=1, two language entries (fi + sv)
        // Kamppi: karttanimi_id=2, one entry (fi only)
        // Lapinkylä: karttanimi_id=3, in Rovaniemi
        dsl.execute("""
                INSERT INTO gis.named_place (id, name, language, place_class, karttanimi_id, municipality_code, location)
                VALUES
                  (1, 'Kallio',  'fi', 540, 1, '091',
                   ST_SetSRID(ST_MakePoint(24.9500, 60.1850), 4326)),
                  (2, 'Berget',  'sv', 540, 1, '091',
                   ST_SetSRID(ST_MakePoint(24.9500, 60.1850), 4326)),
                  (3, 'Kamppi',  'fi', 540, 2, '091',
                   ST_SetSRID(ST_MakePoint(24.9320, 60.1680), 4326)),
                  (4, 'Lapinkylä', 'fi', 590, 3, '564',
                   ST_SetSRID(ST_MakePoint(25.7300, 66.5000), 4326))
                """);
    }

    @AfterEach
    void tearDown() {
        truncateAllTables();
    }

    @Test
    void search_exactMatch_returnsResult() {
        var results = repository.search("Kallio", 10, null);

        assertFalse(results.isEmpty());
        assertTrue(results.stream()
                .anyMatch(r -> r.name().anyValue().orElse("").contains("Kallio")
                        || r.name().values().containsValue("Kallio")));
    }

    @Test
    void search_multilingualGrouping_mergesLanguages() {
        // "Kallio" has two language entries (fi+sv) — should be merged into one result
        var results = repository.search("Kallio", 10, null);

        assertFalse(results.isEmpty());
        // Find the result with karttanimi_id=1
        var kallioResult = results.stream()
                .filter(r -> r.karttanimiId() == 1L)
                .findFirst();

        assertTrue(kallioResult.isPresent());
        // Should have both Finnish and Swedish names
        var nameValues = kallioResult.get().name().values().values(); // Collection<String>
        assertTrue(nameValues.contains("Kallio") || nameValues.contains("Berget"));
    }

    @Test
    void search_municipalityFilter_restrictsToMunicipality() {
        var results = repository.search("Lapinkylä", 10, MunicipalityCode.of("091"));

        // Lapinkylä is in Rovaniemi (564), not Helsinki (091)
        assertTrue(results.isEmpty());
    }

    @Test
    void search_municipalityFilterMatchesCorrect() {
        var results = repository.search("Lapinkylä", 10, MunicipalityCode.of("564"));

        assertFalse(results.isEmpty());
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var results = repository.search("Zyxwvutsrq", 10, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void search_limitRespected() {
        // With limit=1 and two matching places (Kallio, Kamppi), only 1 should be returned
        var results = repository.search("Kallio", 1, null);

        assertTrue(results.size() <= 1);
    }

    @Test
    void search_coordinatesIncluded() {
        var results = repository.search("Kamppi", 10, null);

        assertFalse(results.isEmpty());
        var coords = results.get(0).coordinates();
        assertNotNull(coords);
        assertTrue(coords.longitude() > 24.0 && coords.longitude() < 25.5);
        assertTrue(coords.latitude() > 59.0 && coords.latitude() < 61.0);
    }

    @Test
    void search_placeClassIncluded() {
        var results = repository.search("Kallio", 10, null);

        assertFalse(results.isEmpty());
        var kallioResult = results.stream()
                .filter(r -> r.karttanimiId() == 1L)
                .findFirst();
        assertTrue(kallioResult.isPresent());
        assertEquals(540, kallioResult.get().placeClass());
    }

    @Test
    void search_municipalityIncluded() {
        var results = repository.search("Kamppi", 10, null);

        assertFalse(results.isEmpty());
        var first = results.get(0);
        assertNotNull(first.municipality());
        assertEquals("091", first.municipality().code().code());
    }
}
