package net.pkhapps.idispatchx.common.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidationExceptionTest {

    @Test
    void constructor_withCodeAndMessage_setsFields() {
        var exception = new ValidationException("INVALID_QUERY", "Query too short");

        assertEquals("INVALID_QUERY", exception.getErrorCode());
        assertEquals("Query too short", exception.getMessage());
        assertNull(exception.getDetails());
    }

    @Test
    void constructor_withDetails_setsAllFields() {
        var details = Map.<String, Object>of("minLength", 3, "actualLength", 2);
        var exception = new ValidationException("INVALID_QUERY", "Query too short", details);

        assertEquals("INVALID_QUERY", exception.getErrorCode());
        assertEquals("Query too short", exception.getMessage());
        assertEquals(details, exception.getDetails());
    }

    @Test
    void constructor_nullErrorCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new ValidationException(null, "message"));
    }
}
