package net.pkhapps.idispatchx.gis.server.api;

import tools.jackson.databind.JsonNode;
import net.pkhapps.idispatchx.gis.server.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    void health_databaseUp_returns200WithUpStatus() throws Exception {
        var response = httpGet("/health");

        assertEquals(200, response.statusCode());
        var body = objectMapper().readTree(response.body());
        assertEquals("UP", body.get("status").asText());
    }

    @Test
    void health_noAuthRequired_returns200() throws Exception {
        // Health endpoint must not require authentication
        var response = httpGet("/health");

        assertNotEquals(401, response.statusCode());
        assertNotEquals(403, response.statusCode());
        assertEquals(200, response.statusCode());
    }

    @Test
    void health_responseIncludesDatabaseComponent() throws Exception {
        var response = httpGet("/health");
        var body = objectMapper().readTree(response.body());

        JsonNode components = body.get("components");
        assertNotNull(components);
        assertNotNull(components.get("database"));
        assertEquals("UP", components.get("database").get("status").asText());
    }

    @Test
    void health_responseIncludesTileDirectoryComponent() throws Exception {
        var response = httpGet("/health");
        var body = objectMapper().readTree(response.body());

        JsonNode components = body.get("components");
        assertNotNull(components);
        assertNotNull(components.get("tileDirectory"));
        // tileDirectory exists and is readable (created by ApiIntegrationTestBase)
        assertEquals("UP", components.get("tileDirectory").get("status").asText());
    }

    @Test
    void health_responseContentTypeIsJson() throws Exception {
        var response = httpGet("/health");

        var contentType = response.headers().firstValue("content-type").orElse("");
        assertTrue(contentType.contains("application/json"),
                "Expected application/json but got: " + contentType);
    }
}
