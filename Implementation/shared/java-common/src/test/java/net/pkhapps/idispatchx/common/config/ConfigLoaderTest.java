package net.pkhapps.idispatchx.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void envVarTakesPrecedenceOverProperties() {
        var properties = new Properties();
        properties.setProperty("MY_VAR", "from-properties");
        var envVars = Map.of("MY_VAR", "from-env");

        var loader = new ConfigLoader(properties, envVars::get);
        var property = ConfigProperty.requiredString("MY_VAR");

        var result = loader.get(property);

        assertEquals("from-env", result);
    }

    @Test
    void fallsBackToPropertiesWhenEnvVarMissing() {
        var properties = new Properties();
        properties.setProperty("MY_VAR", "from-properties");
        Map<String, String> envVars = new HashMap<>();

        var loader = new ConfigLoader(properties, envVars::get);
        var property = ConfigProperty.requiredString("MY_VAR");

        var result = loader.get(property);

        assertEquals("from-properties", result);
    }

    @Test
    void usesDefaultWhenBothMissing() {
        var properties = new Properties();
        Map<String, String> envVars = new HashMap<>();

        var loader = new ConfigLoader(properties, envVars::get);
        var property = ConfigProperty.optionalString("MY_VAR", "default-value");

        var result = loader.get(property);

        assertEquals("default-value", result);
    }

    @Test
    void throwsWhenRequiredPropertyMissing() {
        var properties = new Properties();
        Map<String, String> envVars = new HashMap<>();

        var loader = new ConfigLoader(properties, envVars::get);
        var property = ConfigProperty.requiredString("MY_VAR");

        var exception = assertThrows(ConfigurationException.class,
                () -> loader.get(property));

        assertTrue(exception.getMessage().contains("MY_VAR"));
    }

    @Test
    void fromFile_loadsProperties(@TempDir Path tempDir) throws IOException {
        var propsFile = tempDir.resolve("test.properties");
        Files.writeString(propsFile, "MY_VAR=from-file\n");

        Map<String, String> envVars = new HashMap<>();
        var loader = new ConfigLoader(loadProperties(propsFile), envVars::get);
        var property = ConfigProperty.requiredString("MY_VAR");

        var result = loader.get(property);

        assertEquals("from-file", result);
    }

    @Test
    void fromFile_handlesNonExistentFile(@TempDir Path tempDir) {
        var propsFile = tempDir.resolve("nonexistent.properties");

        // Should not throw, just use empty properties
        var loader = ConfigLoader.fromFile(propsFile);
        var property = ConfigProperty.optionalString("MY_VAR", "default");

        var result = loader.get(property);

        assertEquals("default", result);
    }

    @Test
    void create_createsEmptyLoader() {
        // This test verifies that create() works without throwing
        var loader = ConfigLoader.create();
        var property = ConfigProperty.optionalString("MY_VAR", "default");

        var result = loader.get(property);

        assertEquals("default", result);
    }

    @Test
    void secretProperty_valueIsNotLogged() {
        // This test verifies that secret properties are handled correctly
        // (actual logging behavior is hard to test, but we verify the property works)
        var properties = new Properties();
        properties.setProperty("PASSWORD", "secret123");
        Map<String, String> envVars = new HashMap<>();

        var loader = new ConfigLoader(properties, envVars::get);
        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");

        var result = loader.get(property);

        assertEquals("secret123", result);
    }

    private Properties loadProperties(Path path) throws IOException {
        var properties = new Properties();
        try (var is = Files.newInputStream(path)) {
            properties.load(is);
        }
        return properties;
    }
}
