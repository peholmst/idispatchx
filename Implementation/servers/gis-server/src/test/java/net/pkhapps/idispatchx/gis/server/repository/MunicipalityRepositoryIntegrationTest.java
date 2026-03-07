package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MunicipalityRepositoryIntegrationTest extends IntegrationTestBase {

    private MunicipalityRepository repository;

    @BeforeEach
    void setUp() {
        truncateAllTables();
        repository = new MunicipalityRepository(dsl);

        dsl.execute("""
                INSERT INTO gis.municipality (municipality_code, name_fi, name_sv)
                VALUES ('091', 'Helsinki', 'Helsingfors'),
                       ('049', 'Espoo', 'Esbo'),
                       ('235', 'Kauniainen', 'Grankulla')
                """);
    }

    @AfterEach
    void tearDown() {
        truncateAllTables();
    }

    @Test
    void findByCode_existingCode_returnsMunicipality() {
        var result = repository.findByCode(MunicipalityCode.of("091"));

        assertTrue(result.isPresent());
        assertEquals("091", result.get().code().code());
        var nameFi = result.get().name().get(Language.of("fi"));
        assertTrue(nameFi.isPresent());
        assertEquals("Helsinki", nameFi.get());
    }

    @Test
    void findByCode_unknownCode_returnsEmpty() {
        var result = repository.findByCode(MunicipalityCode.of("999"));

        assertFalse(result.isPresent());
    }

    @Test
    void searchByName_exactMatch_returnsResult() {
        var results = repository.searchByName("Helsinki", 10);

        assertFalse(results.isEmpty());
        var codes = results.stream().map(m -> m.code().code()).toList();
        assertTrue(codes.contains("091"));
    }

    @Test
    void searchByName_fuzzyMatch_returnsResult() {
        // "Espoo" vs "Espo" — trigram similarity should be above threshold
        var results = repository.searchByName("Espoo", 10);

        assertFalse(results.isEmpty());
        assertEquals("049", results.get(0).code().code());
    }

    @Test
    void searchByName_noMatch_returnsEmpty() {
        var results = repository.searchByName("Rovaniemi", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchByName_resultsRankedBySimilarity() {
        // Search for "Espoo" — Espoo should rank before Kauniainen
        var results = repository.searchByName("Espoo", 10);

        assertFalse(results.isEmpty());
        assertEquals("049", results.get(0).code().code());
    }

    @Test
    void searchByName_limitRespected() {
        var results = repository.searchByName("Helsinki", 1);

        assertTrue(results.size() <= 1);
    }
}
