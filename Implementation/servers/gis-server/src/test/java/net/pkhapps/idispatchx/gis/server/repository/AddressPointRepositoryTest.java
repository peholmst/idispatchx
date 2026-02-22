package net.pkhapps.idispatchx.gis.server.repository;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressPointRepositoryTest {

    private AddressPointRepository repository;

    @BeforeEach
    void setUp() {
        // Create a minimal DSLContext without a real connection
        // This is sufficient for testing validation logic
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
        repository = new AddressPointRepository(dsl);
    }

    @Test
    void constructor_withNullDslContext_throws() {
        assertThrows(NullPointerException.class, () ->
                new AddressPointRepository(null));
    }

    @Test
    void constructor_withValidDslContext_createsRepository() {
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
        var repo = new AddressPointRepository(dsl);

        assertNotNull(repo);
    }

    @Test
    void search_withNullQuery_throws() {
        assertThrows(NullPointerException.class, () ->
                repository.search(null, 10, null));
    }

    @Test
    void search_withBlankQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                repository.search("   ", 10, null));

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void search_withEmptyQuery_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                repository.search("", 10, null));

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void search_withZeroLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                repository.search("test", 0, null));

        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void search_withNegativeLimit_throws() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                repository.search("test", -5, null));

        assertTrue(exception.getMessage().contains("limit"));
    }
}
