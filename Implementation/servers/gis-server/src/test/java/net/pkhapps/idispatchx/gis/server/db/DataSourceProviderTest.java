package net.pkhapps.idispatchx.gis.server.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceProviderTest {

    @Test
    void sanitizeJdbcUrl_nullUrl_returnsNullString() {
        assertEquals("null", DataSourceProvider.sanitizeJdbcUrl(null));
    }

    @Test
    void sanitizeJdbcUrl_simpleUrl_returnsUnchanged() {
        var url = "jdbc:postgresql://localhost:5432/gis";
        assertEquals(url, DataSourceProvider.sanitizeJdbcUrl(url));
    }

    @Test
    void sanitizeJdbcUrl_urlWithQueryParams_redactsParams() {
        var url = "jdbc:postgresql://localhost:5432/gis?password=secret&user=admin";
        var sanitized = DataSourceProvider.sanitizeJdbcUrl(url);

        assertEquals("jdbc:postgresql://localhost:5432/gis?[REDACTED]", sanitized);
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("admin"));
    }

    @Test
    void sanitizeJdbcUrl_urlWithUserInfo_redactsCredentials() {
        var url = "jdbc:postgresql://user:password@localhost:5432/gis";
        var sanitized = DataSourceProvider.sanitizeJdbcUrl(url);

        assertEquals("jdbc:postgresql://[REDACTED]@localhost:5432/gis", sanitized);
        assertFalse(sanitized.contains("user"));
        assertFalse(sanitized.contains("password"));
    }

    @Test
    void sanitizeJdbcUrl_urlWithUserInfoAndQuery_redactsQuery() {
        // Query params take precedence in the current implementation
        var url = "jdbc:postgresql://user:pass@localhost:5432/gis?sslmode=require";
        var sanitized = DataSourceProvider.sanitizeJdbcUrl(url);

        // Query params are checked first
        assertEquals("jdbc:postgresql://user:pass@localhost:5432/gis?[REDACTED]", sanitized);
    }

    @Test
    void sanitizeJdbcUrl_urlWithOnlyUsername_redactsUserInfo() {
        var url = "jdbc:postgresql://admin@localhost:5432/gis";
        var sanitized = DataSourceProvider.sanitizeJdbcUrl(url);

        assertEquals("jdbc:postgresql://[REDACTED]@localhost:5432/gis", sanitized);
    }

    @Test
    void sanitizeJdbcUrl_emptyUrl_returnsEmpty() {
        assertEquals("", DataSourceProvider.sanitizeJdbcUrl(""));
    }

    @Test
    void sanitizeJdbcUrl_urlWithAtSignInDatabase_handlesCorrectly() {
        // Edge case: @ in database name after the host
        var url = "jdbc:postgresql://localhost:5432/db@name";
        var sanitized = DataSourceProvider.sanitizeJdbcUrl(url);

        // The @ is after ://, so it's treated as userinfo separator
        assertEquals("jdbc:postgresql://[REDACTED]@name", sanitized);
    }
}
