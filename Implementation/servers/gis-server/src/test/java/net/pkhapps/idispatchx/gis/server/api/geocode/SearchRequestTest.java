package net.pkhapps.idispatchx.gis.server.api.geocode;

import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {

    // === Construction Tests ===

    @Test
    void constructor_withValidParameters_createsInstance() {
        var request = new SearchRequest("test query", 25, MunicipalityCode.of("091"));

        assertEquals("test query", request.query());
        assertEquals(25, request.limit());
        assertEquals(MunicipalityCode.of("091"), request.municipalityCode());
    }

    @Test
    void constructor_withNullMunicipalityCode_createsInstance() {
        var request = new SearchRequest("test query", 20, null);

        assertEquals("test query", request.query());
        assertEquals(20, request.limit());
        assertNull(request.municipalityCode());
    }

    @Test
    void of_withQueryOnly_usesDefaults() {
        var request = SearchRequest.of("test query");

        assertEquals("test query", request.query());
        assertEquals(SearchRequest.DEFAULT_LIMIT, request.limit());
        assertNull(request.municipalityCode());
    }

    @Test
    void of_withAllParameters_parsesCorrectly() {
        var request = SearchRequest.of("test query", "30", "091");

        assertEquals("test query", request.query());
        assertEquals(30, request.limit());
        assertEquals(MunicipalityCode.of("091"), request.municipalityCode());
    }

    @Test
    void of_withNullLimitAndMunicipality_usesDefaults() {
        var request = SearchRequest.of("test query", null, null);

        assertEquals("test query", request.query());
        assertEquals(SearchRequest.DEFAULT_LIMIT, request.limit());
        assertNull(request.municipalityCode());
    }

    @Test
    void of_withBlankLimitAndMunicipality_usesDefaults() {
        var request = SearchRequest.of("test query", "  ", "  ");

        assertEquals("test query", request.query());
        assertEquals(SearchRequest.DEFAULT_LIMIT, request.limit());
        assertNull(request.municipalityCode());
    }

    // === Query Validation Tests ===

    @Test
    void constructor_nullQuery_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new SearchRequest(null, 20, null));
    }

    @Test
    void constructor_queryTooShort_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest("ab", 20, null));
        assertTrue(exception.getMessage().contains("at least " + SearchRequest.MIN_QUERY_LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab"})
    void constructor_queryBelowMinLength_throwsIllegalArgumentException(String query) {
        assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest(query, 20, null));
    }

    @Test
    void constructor_queryExactlyMinLength_createsInstance() {
        var request = new SearchRequest("abc", 20, null);
        assertEquals("abc", request.query());
    }

    @Test
    void constructor_queryExceedsMaxLength_throwsIllegalArgumentException() {
        var longQuery = "a".repeat(SearchRequest.MAX_QUERY_LENGTH + 1);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest(longQuery, 20, null));
        assertTrue(exception.getMessage().contains("must not exceed " + SearchRequest.MAX_QUERY_LENGTH));
    }

    @Test
    void constructor_queryExactlyMaxLength_createsInstance() {
        var maxQuery = "a".repeat(SearchRequest.MAX_QUERY_LENGTH);
        var request = new SearchRequest(maxQuery, 20, null);
        assertEquals(SearchRequest.MAX_QUERY_LENGTH, request.query().length());
    }

    // === Limit Validation Tests ===

    @Test
    void constructor_limitBelowMin_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest("test query", 0, null));
        assertTrue(exception.getMessage().contains("between " + SearchRequest.MIN_LIMIT));
    }

    @Test
    void constructor_limitAboveMax_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest("test query", 51, null));
        assertTrue(exception.getMessage().contains("between"));
    }

    @Test
    void constructor_limitExactlyMin_createsInstance() {
        var request = new SearchRequest("test query", SearchRequest.MIN_LIMIT, null);
        assertEquals(SearchRequest.MIN_LIMIT, request.limit());
    }

    @Test
    void constructor_limitExactlyMax_createsInstance() {
        var request = new SearchRequest("test query", SearchRequest.MAX_LIMIT, null);
        assertEquals(SearchRequest.MAX_LIMIT, request.limit());
    }

    @Test
    void constructor_negativeLimit_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new SearchRequest("test query", -1, null));
    }

    // === Factory Method Validation Tests ===

    @Test
    void of_invalidLimitString_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> SearchRequest.of("test query", "not-a-number", null));
        assertTrue(exception.getMessage().contains("valid integer"));
    }

    @Test
    void of_invalidMunicipalityCode_throwsIllegalArgumentException() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> SearchRequest.of("test query", "20", "12"));
        assertTrue(exception.getMessage().contains("3 digits"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12", "1234", "abc", "12a", "0912"})
    void of_invalidMunicipalityCodeFormats_throwsIllegalArgumentException(String code) {
        assertThrows(IllegalArgumentException.class,
                () -> SearchRequest.of("test query", "20", code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"091", "000", "999", "123"})
    void of_validMunicipalityCodeFormats_createsInstance(String code) {
        var request = SearchRequest.of("test query", "20", code);
        assertEquals(MunicipalityCode.of(code), request.municipalityCode());
    }

    // === Optional Municipality Code Tests ===

    @Test
    void optionalMunicipalityCode_withCode_returnsPresent() {
        var request = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        assertTrue(request.optionalMunicipalityCode().isPresent());
        assertEquals(MunicipalityCode.of("091"), request.optionalMunicipalityCode().orElseThrow());
    }

    @Test
    void optionalMunicipalityCode_withoutCode_returnsEmpty() {
        var request = new SearchRequest("test query", 20, null);
        assertTrue(request.optionalMunicipalityCode().isEmpty());
    }

    // === Equality Tests ===

    @Test
    void equals_sameValues_returnsTrue() {
        var r1 = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        var r2 = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        assertEquals(r1, r2);
    }

    @Test
    void equals_differentQuery_returnsFalse() {
        var r1 = new SearchRequest("test query", 20, null);
        var r2 = new SearchRequest("other query", 20, null);
        assertNotEquals(r1, r2);
    }

    @Test
    void equals_differentLimit_returnsFalse() {
        var r1 = new SearchRequest("test query", 20, null);
        var r2 = new SearchRequest("test query", 25, null);
        assertNotEquals(r1, r2);
    }

    @Test
    void equals_differentMunicipalityCode_returnsFalse() {
        var r1 = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        var r2 = new SearchRequest("test query", 20, MunicipalityCode.of("049"));
        assertNotEquals(r1, r2);
    }

    @Test
    void hashCode_sameValues_sameHashCode() {
        var r1 = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        var r2 = new SearchRequest("test query", 20, MunicipalityCode.of("091"));
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    // === Constants Tests ===

    @Test
    void constants_haveExpectedValues() {
        assertEquals(3, SearchRequest.MIN_QUERY_LENGTH);
        assertEquals(200, SearchRequest.MAX_QUERY_LENGTH);
        assertEquals(1, SearchRequest.MIN_LIMIT);
        assertEquals(50, SearchRequest.MAX_LIMIT);
        assertEquals(20, SearchRequest.DEFAULT_LIMIT);
    }
}
