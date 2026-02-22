package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MunicipalityRepository.
 * <p>
 * These tests verify parameter validation and basic behavior.
 * Full database interaction tests require integration testing with
 * a PostgreSQL database with pg_trgm extension.
 */
class MunicipalityRepositoryTest {

    private MunicipalityRepository repository;

    @BeforeEach
    void setUp() {
        // Create a DSL context without a connection for validation tests
        var dsl = DSL.using(SQLDialect.POSTGRES);
        repository = new MunicipalityRepository(dsl);
    }

    @Test
    void constructor_nullDsl_throws() {
        assertThrows(NullPointerException.class, () -> new MunicipalityRepository(null));
    }

    @Test
    void findByCode_nullCode_throws() {
        assertThrows(NullPointerException.class, () -> repository.findByCode(null));
    }

    @Test
    void searchByName_nullQuery_throws() {
        assertThrows(NullPointerException.class, () -> repository.searchByName(null, 10));
    }

    @Test
    void searchByName_blankQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.searchByName("   ", 10));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void searchByName_emptyQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.searchByName("", 10));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void searchByName_zeroLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.searchByName("Helsinki", 0));
        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void searchByName_negativeLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.searchByName("Helsinki", -1));
        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void findByCode_validCode_acceptsInput() {
        var code = MunicipalityCode.of("091");
        // Method should accept valid input without throwing validation errors
        // Actual database query will fail without connection, but that's expected
        assertDoesNotThrow(() -> {
            try {
                repository.findByCode(code);
            } catch (org.jooq.exception.DetachedException e) {
                // Expected when DSL has no connection
            }
        });
    }

    @Test
    void searchByName_validInput_acceptsInput() {
        // Method should accept valid input without throwing validation errors
        // Actual database query will fail without connection, but that's expected
        assertDoesNotThrow(() -> {
            try {
                repository.searchByName("Helsinki", 10);
            } catch (org.jooq.exception.DetachedException e) {
                // Expected when DSL has no connection
            }
        });
    }
}
