package net.pkhapps.idispatchx.gis.server.api;

import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.gis.server.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayersControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    void getLayers_withValidToken_returns200() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/layers", token);

        assertEquals(200, response.statusCode());
    }

    @Test
    void getLayers_withoutToken_returns401() throws Exception {
        var response = httpGet("/api/v1/layers");

        assertEquals(401, response.statusCode());
    }

    @Test
    void getLayers_withObserverRole_isAllowed() throws Exception {
        var token = createToken(Role.OBSERVER);
        var response = httpGet("/api/v1/layers", token);

        assertNotEquals(401, response.statusCode());
        assertNotEquals(403, response.statusCode());
    }

    @Test
    void getLayers_returnsJsonWithLayersArray() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/api/v1/layers", token);

        assertEquals(200, response.statusCode());
        var json = objectMapper().readTree(response.body());
        assertTrue(json.has("layers"), "Response should have a 'layers' field");
        assertTrue(json.get("layers").isArray(), "The 'layers' field should be an array");
    }
}
