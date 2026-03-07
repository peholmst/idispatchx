package net.pkhapps.idispatchx.gis.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.gis.server.ApiIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeocodeControllerIntegrationTest extends ApiIntegrationTestBase {

    @BeforeEach
    void insertTestData() {
        truncateAllTables();
        dsl.execute("""
                INSERT INTO gis.municipality (municipality_code, name_fi, name_sv)
                VALUES ('091', 'Helsinki', 'Helsingfors')
                """);
        dsl.execute("""
                INSERT INTO gis.address_point (id, number, name_fi, municipality_code, location)
                VALUES (100, '5', 'Mannerheimintie', '091',
                        ST_SetSRID(ST_MakePoint(24.9384, 60.1699), 4326))
                """);
    }

    @AfterEach
    void cleanUp() {
        truncateAllTables();
    }

    // ==================== Successful search ====================

    @Test
    void search_validQueryWithToken_returns200() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        assertEquals(200, response.statusCode());
    }

    @Test
    void search_contentTypeIsJson() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        var contentType = response.headers().firstValue("content-type").orElse("");
        assertTrue(contentType.contains("application/json"),
                "Expected application/json but got: " + contentType);
    }

    @Test
    void search_responseIncludesQueryAndResults() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        assertEquals(200, response.statusCode());
        var body = objectMapper().readTree(response.body());
        assertNotNull(body.get("query"));
        assertEquals("Mannerheimintie", body.get("query").asText());
        assertTrue(body.get("results").isArray());
    }

    @Test
    void search_matchingAddress_returnsNonEmptyResults() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        var body = objectMapper().readTree(response.body());
        JsonNode results = body.get("results");
        assertFalse(results.isEmpty(), "Expected at least one result for 'Mannerheimintie'");
    }

    @Test
    void search_noMatch_returnsEmptyResults() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Zyxwvutsrq", token);

        assertEquals(200, response.statusCode());
        var body = objectMapper().readTree(response.body());
        assertEquals(0, body.get("results").size());
    }

    @Test
    void search_withMunicipalityFilter_returns200() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie&municipality=091", token);

        assertEquals(200, response.statusCode());
    }

    @Test
    void search_withLimitParameter_returns200() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie&limit=5", token);

        assertEquals(200, response.statusCode());
        var body = objectMapper().readTree(response.body());
        assertTrue(body.get("results").size() <= 5);
    }

    // ==================== Validation errors ====================

    @Test
    void search_withoutToken_returns401() throws Exception {
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie");

        assertEquals(401, response.statusCode());
    }

    @Test
    void search_queryTooShort_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=ab", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void search_missingQuery_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void search_invalidLimit_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=Helsinki&limit=abc", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void search_invalidMunicipalityCode_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        // Municipality code must be 3 digits
        var response = httpGet("/api/v1/geocode/search?q=Helsinki&municipality=INVALID", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void search_errorResponseHasCorrectFormat() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/geocode/search?q=ab", token);

        assertEquals(400, response.statusCode());
        var body = objectMapper().readTree(response.body());
        assertNotNull(body.get("code"), "Error response should have 'code' field");
        assertNotNull(body.get("message"), "Error response should have 'message' field");
        assertNotNull(body.get("timestamp"), "Error response should have 'timestamp' field");
    }

    // ==================== Role authorization ====================

    @Test
    void search_withObserverRole_returns200() throws Exception {
        var token = createToken(Role.OBSERVER);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        assertEquals(200, response.statusCode());
    }

    @Test
    void search_withAdminRole_returns403() throws Exception {
        var token = createToken(Role.ADMIN);
        var response = httpGet("/api/v1/geocode/search?q=Mannerheimintie", token);

        assertEquals(403, response.statusCode());
    }
}
