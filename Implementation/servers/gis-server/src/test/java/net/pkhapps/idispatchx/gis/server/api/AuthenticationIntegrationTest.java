package net.pkhapps.idispatchx.gis.server.api;

import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.gis.server.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for JWT authentication and role-based authorization.
 * Uses the geocoding endpoint as a representative protected endpoint.
 */
class AuthenticationIntegrationTest extends ApiIntegrationTestBase {

    private static final String GEOCODE_URL = "/api/v1/geocode/search?q=Helsinki";

    // ==================== Missing / malformed tokens ====================

    @Test
    void request_withoutToken_returns401() throws Exception {
        var response = httpGet(GEOCODE_URL);

        assertEquals(401, response.statusCode());
    }

    @Test
    void request_withMalformedToken_returns401() throws Exception {
        var response = httpGet(GEOCODE_URL, "not-a-jwt");

        assertEquals(401, response.statusCode());
    }

    // ==================== Expired tokens ====================

    @Test
    void request_withExpiredToken_returns401() throws Exception {
        var token = createExpiredToken(Role.DISPATCHER);
        var response = httpGet(GEOCODE_URL, token);

        assertEquals(401, response.statusCode());
    }

    // ==================== Authorized roles ====================

    @Test
    void request_withDispatcherRole_isAllowed() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet(GEOCODE_URL, token);

        // 200 = found results (or empty results), 400 = validation error — both are acceptable
        // 401 or 403 would indicate an auth problem
        int status = response.statusCode();
        assertTrue(status != 401 && status != 403,
                "Expected non-auth error but got: " + status);
    }

    @Test
    void request_withObserverRole_isAllowed() throws Exception {
        var token = createToken(Role.OBSERVER);
        var response = httpGet(GEOCODE_URL, token);

        int status = response.statusCode();
        assertTrue(status != 401 && status != 403,
                "Expected non-auth error but got: " + status);
    }

    // ==================== Unauthorized roles ====================

    @Test
    void request_withAdminRole_returns403() throws Exception {
        var token = createToken(Role.ADMIN);
        var response = httpGet(GEOCODE_URL, token);

        assertEquals(403, response.statusCode());
    }

    @Test
    void request_withStationRole_returns403() throws Exception {
        var token = createToken(Role.STATION);
        var response = httpGet(GEOCODE_URL, token);

        assertEquals(403, response.statusCode());
    }

    @Test
    void request_withNoRoles_returns403() throws Exception {
        var token = createToken(); // no roles
        var response = httpGet(GEOCODE_URL, token);

        assertEquals(403, response.statusCode());
    }

    // ==================== WMTS also requires auth ====================

    @Test
    void wmtsGetCapabilities_withoutToken_returns401() throws Exception {
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml");

        assertEquals(401, response.statusCode());
    }

    @Test
    void wmtsGetCapabilities_withDispatcherRole_isAllowed() throws Exception {
        var token = createToken(Role.DISPATCHER);
        var response = httpGet("/wmts/1.0.0/WMTSCapabilities.xml", token);

        int status = response.statusCode();
        assertTrue(status != 401 && status != 403,
                "Expected non-auth error but got: " + status);
    }

    // ==================== Health endpoint requires no auth ====================

    @Test
    void health_withoutToken_returns200() throws Exception {
        var response = httpGet("/health");

        assertEquals(200, response.statusCode());
    }

    // ==================== Helper ====================

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
