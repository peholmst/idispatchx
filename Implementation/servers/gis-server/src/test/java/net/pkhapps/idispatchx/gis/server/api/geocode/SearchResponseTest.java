package net.pkhapps.idispatchx.gis.server.api.geocode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchResponseTest {

    private static final Language FINNISH = Language.of("fi");
    private static final Language SWEDISH = Language.of("sv");

    private static final MultilingualName HELSINKI_NAME = MultilingualName.of(
            Map.of(FINNISH, "Helsinki", SWEDISH, "Helsingfors"));
    private static final Municipality HELSINKI = Municipality.of(
            MunicipalityCode.of("091"), HELSINKI_NAME);
    private static final Coordinates.Epsg4326 COORDS = Coordinates.Epsg4326.of(60.169857, 24.938379);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Construction Tests ===

    @Test
    void constructor_withValidParameters_createsInstance() {
        var results = List.<LocationResult>of(
                new AddressResult(
                        MultilingualName.of(FINNISH, "Mannerheimintie"),
                        "1",
                        HELSINKI,
                        COORDS,
                        AddressSource.ADDRESS_POINT
                )
        );
        var response = new SearchResponse(results, "Mannerheim");

        assertEquals(1, response.results().size());
        assertEquals("Mannerheim", response.query());
    }

    @Test
    void constructor_withEmptyResults_createsInstance() {
        var response = new SearchResponse(List.of(), "test query");

        assertTrue(response.results().isEmpty());
        assertEquals("test query", response.query());
    }

    @Test
    void of_createsResponse() {
        var results = List.<LocationResult>of(
                new PlaceResult(
                        MultilingualName.of(FINNISH, "Kallio"),
                        48111,
                        HELSINKI,
                        COORDS
                )
        );
        var response = SearchResponse.of(results, "Kallio");

        assertEquals(1, response.results().size());
        assertEquals("Kallio", response.query());
    }

    @Test
    void empty_createsEmptyResponse() {
        var response = SearchResponse.empty("test");

        assertTrue(response.results().isEmpty());
        assertEquals("test", response.query());
    }

    // === Validation Tests ===

    @Test
    void constructor_nullResults_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new SearchResponse(null, "query"));
    }

    @Test
    void constructor_nullQuery_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new SearchResponse(List.of(), null));
    }

    // === Immutability Tests ===

    @Test
    void results_isImmutable() {
        var mutableList = new ArrayList<LocationResult>();
        mutableList.add(new PlaceResult(MultilingualName.of(FINNISH, "Test"), 1, HELSINKI, COORDS));

        var response = new SearchResponse(mutableList, "query");

        // Modify original list
        mutableList.clear();

        // Response should still have the original item
        assertEquals(1, response.results().size());
    }

    @Test
    void results_cannotBeModified() {
        var response = SearchResponse.of(
                List.of(new PlaceResult(MultilingualName.of(FINNISH, "Test"), 1, HELSINKI, COORDS)),
                "query"
        );

        assertThrows(UnsupportedOperationException.class,
                () -> response.results().clear());
    }

    // === Query Methods ===

    @Test
    void isEmpty_withResults_returnsFalse() {
        var response = SearchResponse.of(
                List.of(new PlaceResult(MultilingualName.of(FINNISH, "Test"), 1, HELSINKI, COORDS)),
                "query"
        );
        assertFalse(response.isEmpty());
    }

    @Test
    void isEmpty_withoutResults_returnsTrue() {
        var response = SearchResponse.empty("query");
        assertTrue(response.isEmpty());
    }

    // === Equality Tests ===

    @Test
    void equals_sameValues_returnsTrue() {
        var results = List.<LocationResult>of(
                new PlaceResult(MultilingualName.of(FINNISH, "Test"), 1, HELSINKI, COORDS)
        );
        var r1 = new SearchResponse(results, "query");
        var r2 = new SearchResponse(results, "query");
        assertEquals(r1, r2);
    }

    @Test
    void equals_differentResults_returnsFalse() {
        var r1 = SearchResponse.of(
                List.of(new PlaceResult(MultilingualName.of(FINNISH, "Test1"), 1, HELSINKI, COORDS)),
                "query"
        );
        var r2 = SearchResponse.of(
                List.of(new PlaceResult(MultilingualName.of(FINNISH, "Test2"), 1, HELSINKI, COORDS)),
                "query"
        );
        assertNotEquals(r1, r2);
    }

    @Test
    void equals_differentQuery_returnsFalse() {
        var r1 = SearchResponse.empty("query1");
        var r2 = SearchResponse.empty("query2");
        assertNotEquals(r1, r2);
    }

    // === Jackson Serialization Tests ===

    @Test
    void jackson_serializeEmptyResponse_producesExpectedJson() throws JsonProcessingException {
        var response = SearchResponse.empty("test query");
        var json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"results\":[]"));
        assertTrue(json.contains("\"query\":\"test query\""));
        assertFalse(json.contains("\"resultCount\""));
    }

    @Test
    void jackson_serializeWithResults_producesExpectedJson() throws JsonProcessingException {
        var response = SearchResponse.of(
                List.of(
                        new AddressResult(
                                MultilingualName.of(Map.of(FINNISH, "Mannerheimintie", SWEDISH, "Mannerheimvagen")),
                                "1",
                                HELSINKI,
                                COORDS,
                                AddressSource.ADDRESS_POINT
                        )
                ),
                "Mannerheimintie 1"
        );
        var json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"type\":\"address\""));
        assertTrue(json.contains("\"results\":["));
        assertTrue(json.contains("\"query\":\"Mannerheimintie 1\""));
    }

    @Test
    void jackson_deserializeEmptyResponse_correctObject() throws JsonProcessingException {
        var json = """
                {"results":[],"query":"test"}
                """;
        var response = objectMapper.readValue(json, SearchResponse.class);

        assertTrue(response.results().isEmpty());
        assertEquals("test", response.query());
    }

    @Test
    void jackson_roundTrip_preservesData() throws JsonProcessingException {
        var original = SearchResponse.of(
                List.of(
                        new PlaceResult(
                                MultilingualName.of(FINNISH, "Mannerheiminaukio"),
                                48111,
                                HELSINKI,
                                COORDS
                        )
                ),
                "Mannerheiminaukio"
        );

        var json = objectMapper.writeValueAsString(original);
        var restored = objectMapper.readValue(json, SearchResponse.class);

        assertEquals(original, restored);
    }
}
