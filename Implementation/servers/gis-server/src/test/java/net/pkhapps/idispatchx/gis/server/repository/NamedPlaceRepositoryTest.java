package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NamedPlaceRepository.
 * <p>
 * These tests verify parameter validation and basic behavior.
 * Full database interaction tests require integration testing with
 * a PostgreSQL database with pg_trgm extension.
 */
class NamedPlaceRepositoryTest {

    private NamedPlaceRepository repository;

    @BeforeEach
    void setUp() {
        // Create a DSL context without a connection for validation tests
        var dsl = DSL.using(SQLDialect.POSTGRES);
        repository = new NamedPlaceRepository(dsl);
    }

    @Test
    void constructor_nullDsl_throws() {
        assertThrows(NullPointerException.class, () -> new NamedPlaceRepository(null));
    }

    @Test
    void search_nullQuery_throws() {
        assertThrows(NullPointerException.class, () -> repository.search(null, 10, null));
    }

    @Test
    void search_blankQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.search("   ", 10, null));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void search_emptyQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.search("", 10, null));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void search_zeroLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.search("Helsinki", 0, null));
        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void search_negativeLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> repository.search("Helsinki", -1, null));
        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void search_withMunicipalityFilter_acceptsValidCode() {
        var municipalityCode = MunicipalityCode.of("091");

        // Method should accept valid input without throwing validation errors
        // Actual database query will fail without connection, but that's expected
        assertDoesNotThrow(() -> {
            try {
                repository.search("Helsinki", 10, municipalityCode);
            } catch (org.jooq.exception.DetachedException e) {
                // Expected when DSL has no connection
            }
        });
    }

    @Test
    void search_withNullMunicipalityFilter_acceptsNull() {
        // Method should accept null municipality without throwing validation errors
        // Actual database query will fail without connection, but that's expected
        assertDoesNotThrow(() -> {
            try {
                repository.search("Helsinki", 10, null);
            } catch (org.jooq.exception.DetachedException e) {
                // Expected when DSL has no connection
            }
        });
    }

    @Test
    void search_validInput_acceptsInput() {
        // Method should accept valid input without throwing validation errors
        // Actual database query will fail without connection, but that's expected
        assertDoesNotThrow(() -> {
            try {
                repository.search("Munkkiniemi", 5, null);
            } catch (org.jooq.exception.DetachedException e) {
                // Expected when DSL has no connection
            }
        });
    }
}
