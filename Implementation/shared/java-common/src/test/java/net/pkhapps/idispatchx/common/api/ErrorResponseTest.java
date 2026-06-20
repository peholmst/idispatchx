package net.pkhapps.idispatchx.common.api;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void of_withoutDetails_createsResponseWithNullDetails() {
        var response = ErrorResponse.of("UNAUTHORIZED", "Missing token", "/api/v1/geocode/search");

        assertEquals("UNAUTHORIZED", response.error().code());
        assertEquals("Missing token", response.error().message());
        assertNull(response.error().details());
        assertEquals("/api/v1/geocode/search", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void of_withDetails_createsResponseWithDetails() {
        var details = Map.<String, Object>of("minLength", 3, "actualLength", 2);
        var response = ErrorResponse.of("INVALID_QUERY", "Query too short", details, "/api/v1/geocode/search");

        assertEquals("INVALID_QUERY", response.error().code());
        assertEquals("Query too short", response.error().message());
        assertEquals(details, response.error().details());
        assertEquals("/api/v1/geocode/search", response.path());
    }

    @Test
    void constructor_nullError_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ErrorResponse(null, Instant.now(), "/test"));
    }

    @Test
    void constructor_nullTimestamp_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ErrorResponse(new ErrorResponse.ErrorBody("CODE", "msg", null), null, "/test"));
    }

    @Test
    void constructor_nullPath_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ErrorResponse(new ErrorResponse.ErrorBody("CODE", "msg", null), Instant.now(), null));
    }

    @Test
    void errorBody_nullCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ErrorResponse.ErrorBody(null, "msg", null));
    }

    @Test
    void errorBody_nullMessage_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ErrorResponse.ErrorBody("CODE", null, null));
    }

    @Test
    void jsonSerialization_withoutDetails_excludesDetailsField() throws Exception {
        var response = ErrorResponse.of("UNAUTHORIZED", "Missing token", "/test");
        var json = objectMapper.writeValueAsString(response);
        var tree = objectMapper.readTree(json);

        assertEquals("UNAUTHORIZED", tree.get("error").get("code").asText());
        assertEquals("Missing token", tree.get("error").get("message").asText());
        assertFalse(tree.get("error").has("details"), "details should be excluded when null");
        assertTrue(tree.has("timestamp"));
        assertEquals("/test", tree.get("path").asText());
    }

    @Test
    void jsonSerialization_withDetails_includesDetailsField() throws Exception {
        var details = Map.<String, Object>of("minLength", 3);
        var response = ErrorResponse.of("INVALID_QUERY", "Query too short", details, "/test");
        var json = objectMapper.writeValueAsString(response);
        var tree = objectMapper.readTree(json);

        assertTrue(tree.get("error").has("details"));
        assertEquals(3, tree.get("error").get("details").get("minLength").asInt());
    }

    @Test
    void jsonSerialization_timestampIsIso8601() throws Exception {
        var response = ErrorResponse.of("TEST", "test", "/test");
        var json = objectMapper.writeValueAsString(response);
        var tree = objectMapper.readTree(json);

        var timestampStr = tree.get("timestamp").asText();
        // Should parse as an ISO-8601 instant
        assertDoesNotThrow(() -> Instant.parse(timestampStr));
    }
}
