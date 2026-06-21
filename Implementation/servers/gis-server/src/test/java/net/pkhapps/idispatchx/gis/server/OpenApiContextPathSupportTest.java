package net.pkhapps.idispatchx.gis.server;

import io.javalin.openapi.schema.OpenApiSchemaBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenApiContextPathSupportTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void applyContextPathServer_withEmptyContextPath_keepsServersUnset() throws Exception {
        var definition = new OpenApiSchemaBuilder();

        var updated = OpenApiContextPathSupport.applyContextPathServer(definition, "");
        var json = JSON_MAPPER.readTree(updated.toJson());

        assertNull(json.get("servers"));
    }

    @Test
    void applyContextPathServer_withNonEmptyContextPath_setsServerUrl() throws Exception {
        var definition = new OpenApiSchemaBuilder();

        var updated = OpenApiContextPathSupport.applyContextPathServer(definition, "/gis");
        var json = JSON_MAPPER.readTree(updated.toJson());

        assertEquals(1, json.get("servers").size());
        assertEquals("/gis", json.get("servers").get(0).get("url").asText());
    }
}