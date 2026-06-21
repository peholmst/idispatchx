package net.pkhapps.idispatchx.gis.server;

import io.javalin.openapi.schema.OpenApiSchemaBuilder;

final class OpenApiContextPathSupport {

    private OpenApiContextPathSupport() {
    }

    static OpenApiSchemaBuilder applyContextPathServer(OpenApiSchemaBuilder definition, String contextPath) {
        if (contextPath.isBlank()) {
            return definition;
        }
        return definition.server(server -> server.url(contextPath));
    }
}