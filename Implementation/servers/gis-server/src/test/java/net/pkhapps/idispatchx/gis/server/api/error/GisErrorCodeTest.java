package net.pkhapps.idispatchx.gis.server.api.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GisErrorCodeTest {

    @Test
    void errorCodes_haveExpectedValues() {
        assertEquals("INVALID_QUERY", GisErrorCode.INVALID_QUERY);
        assertEquals("INVALID_PARAMETER", GisErrorCode.INVALID_PARAMETER);
        assertEquals("INVALID_TILE_COORDINATES", GisErrorCode.INVALID_TILE_COORDINATES);
        assertEquals("LAYER_NOT_FOUND", GisErrorCode.LAYER_NOT_FOUND);
    }
}
