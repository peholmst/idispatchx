package net.pkhapps.idispatchx.common.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    @Test
    void validConfig_createsSuccessfully() {
        var config = new DatabaseConfig(
                "jdbc:postgresql://localhost/test",
                "testuser",
                "testpass",
                10
        );

        assertEquals("jdbc:postgresql://localhost/test", config.url());
        assertEquals("testuser", config.username());
        assertEquals("testpass", config.password());
        assertEquals(10, config.poolSize());
    }

    @Test
    void nullUrl_throws() {
        assertThrows(NullPointerException.class,
                () -> new DatabaseConfig(null, "user", "pass", 10));
    }

    @Test
    void blankUrl_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("  ", "user", "pass", 10));
    }

    @Test
    void nullUsername_throws() {
        assertThrows(NullPointerException.class,
                () -> new DatabaseConfig("jdbc:postgresql://localhost/test", null, "pass", 10));
    }

    @Test
    void blankUsername_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:postgresql://localhost/test", "  ", "pass", 10));
    }

    @Test
    void poolSizeTooSmall_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:postgresql://localhost/test", "user", "pass", 0));
    }

    @Test
    void poolSizeTooLarge_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:postgresql://localhost/test", "user", "pass", 101));
    }

    @Test
    void builder_loadsFromEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("DB_URL", "jdbc:postgresql://localhost/gis");
        envVars.put("DB_USER", "gisuser");
        envVars.put("DB_PASS", "gispass");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = DatabaseConfig.builder(
                "DB_URL", "DB_USER", "DB_PASS", "DB_PASS_FILE", "DB_POOL"
        ).load(loader);

        assertEquals("jdbc:postgresql://localhost/gis", config.url());
        assertEquals("gisuser", config.username());
        assertEquals("gispass", config.password());
        assertEquals(DatabaseConfig.DEFAULT_POOL_SIZE, config.poolSize());
    }

    @Test
    void builder_usesCustomPoolSize() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("DB_URL", "jdbc:postgresql://localhost/gis");
        envVars.put("DB_USER", "gisuser");
        envVars.put("DB_PASS", "gispass");
        envVars.put("DB_POOL", "20");

        var loader = new ConfigLoader(new Properties(), envVars::get);
        var config = DatabaseConfig.builder(
                "DB_URL", "DB_USER", "DB_PASS", "DB_PASS_FILE", "DB_POOL"
        ).load(loader);

        assertEquals(20, config.poolSize());
    }

    @Test
    void toString_doesNotIncludePassword() {
        var config = new DatabaseConfig(
                "jdbc:postgresql://localhost/test",
                "testuser",
                "supersecret",
                10
        );

        var str = config.toString();

        assertFalse(str.contains("supersecret"));
        assertTrue(str.contains("testuser"));
        assertTrue(str.contains("jdbc:postgresql://localhost/test"));
    }
}
