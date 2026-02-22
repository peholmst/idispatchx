package net.pkhapps.idispatchx.gis.server.repository;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSegmentRepositoryTest {

    private RoadSegmentRepository repository;

    @BeforeEach
    void setUp() {
        // Create a minimal DSLContext without a real connection
        // This is sufficient for testing validation logic
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
        repository = new RoadSegmentRepository(dsl);
    }

    @Test
    void constructor_withNullDslContext_throws() {
        assertThrows(NullPointerException.class, () ->
                new RoadSegmentRepository(null));
    }

    @Test
    void constructor_withValidDslContext_createsRepository() {
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
        var repo = new RoadSegmentRepository(dsl);

        assertNotNull(repo);
    }

    @Nested
    class SearchByNameTests {

        @Test
        void searchByName_withNullQuery_throws() {
            assertThrows(NullPointerException.class, () ->
                    repository.searchByName(null, 10, null));
        }

        @Test
        void searchByName_withBlankQuery_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchByName("   ", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void searchByName_withEmptyQuery_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchByName("", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void searchByName_withZeroLimit_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchByName("test", 0, null));

            assertTrue(exception.getMessage().contains("limit"));
        }

        @Test
        void searchByName_withNegativeLimit_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchByName("test", -5, null));

            assertTrue(exception.getMessage().contains("limit"));
        }
    }

    @Nested
    class InterpolateAddressTests {

        @Test
        void interpolateAddress_withNullRoadName_throws() {
            assertThrows(NullPointerException.class, () ->
                    repository.interpolateAddress(null, 10, null));
        }

        @Test
        void interpolateAddress_withBlankRoadName_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.interpolateAddress("   ", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void interpolateAddress_withEmptyRoadName_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.interpolateAddress("", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void interpolateAddress_withZeroNumber_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.interpolateAddress("Mannerheimintie", 0, null));

            assertTrue(exception.getMessage().contains("number"));
        }

        @Test
        void interpolateAddress_withNegativeNumber_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.interpolateAddress("Mannerheimintie", -5, null));

            assertTrue(exception.getMessage().contains("number"));
        }
    }

    @Nested
    class SearchIntersectionsTests {

        @Test
        void searchIntersections_withNullQuery_throws() {
            assertThrows(NullPointerException.class, () ->
                    repository.searchIntersections(null, 10, null));
        }

        @Test
        void searchIntersections_withBlankQuery_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchIntersections("   ", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void searchIntersections_withEmptyQuery_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchIntersections("", 10, null));

            assertTrue(exception.getMessage().contains("blank"));
        }

        @Test
        void searchIntersections_withZeroLimit_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchIntersections("test", 0, null));

            assertTrue(exception.getMessage().contains("limit"));
        }

        @Test
        void searchIntersections_withNegativeLimit_throws() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    repository.searchIntersections("test", -5, null));

            assertTrue(exception.getMessage().contains("limit"));
        }
    }
}
