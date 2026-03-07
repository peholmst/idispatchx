package net.pkhapps.idispatchx.gis.server.api;

import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.gis.server.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmtsControllerIntegrationTest extends ApiIntegrationTestBase {

    // ==================== GetCapabilities ====================

    @Test
    void getCapabilities_withValidToken_returns200() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml", token);

        assertEquals(200, response.statusCode());
    }

    @Test
    void getCapabilities_contentTypeIsXml() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml", token);

        var contentType = response.headers().firstValue("content-type").orElse("");
        assertTrue(contentType.contains("xml"),
                "Expected XML content type but got: " + contentType);
    }

    @Test
    void getCapabilities_responseBodyContainsWmtsCapabilities() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml", token);

        assertTrue(response.body().contains("WMTSCapabilities"),
                "Expected WMTS capabilities XML but got: " + response.body().substring(0, Math.min(200, response.body().length())));
    }

    @Test
    void getCapabilities_withoutToken_returns401() throws Exception {
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml");

        assertEquals(401, response.statusCode());
    }

    // ==================== GetTile ====================

    @Test
    void getTile_unknownLayer_returns404() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/nonexistent-layer/ETRS-TM35FIN/10/100/200.png", token);

        assertEquals(404, response.statusCode());
    }

    @Test
    void getTile_invalidZoom_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        // zoom=20 is out of range (max is 15)
        var response = httpGet("/wmts/terrain/ETRS-TM35FIN/20/0/0.png", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void getTile_withoutToken_returns401() throws Exception {
        var response = httpGet("/wmts/terrain/ETRS-TM35FIN/10/100/200.png");

        assertEquals(401, response.statusCode());
    }

    @Test
    void getTile_withObserverRole_isAllowed() throws Exception {
        var token = createToken(Role.OBSERVER);
        // Unknown layer → 404, but that's not an auth error
        var response = httpGet("/wmts/nonexistent/ETRS-TM35FIN/10/0/0.png", token);

        assertNotEquals(401, response.statusCode());
        assertNotEquals(403, response.statusCode());
    }

    @Test
    void getTile_invalidCoordinateFormat_returns400() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/terrain/ETRS-TM35FIN/abc/def/ghi.png", token);

        assertEquals(400, response.statusCode());
    }
}
