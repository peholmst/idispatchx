package net.pkhapps.idispatchx.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPropertyTest {

    @Test
    void requiredString_resolvesFromEnvVar() {
        var property = ConfigProperty.requiredString("MY_VAR");
        var values = Map.of("MY_VAR", "test-value");

        var result = property.resolve(values::get);

        assertEquals("test-value", result);
    }

    @Test
    void requiredString_throwsWhenMissing() {
        var property = ConfigProperty.requiredString("MY_VAR");
        Map<String, String> values = Map.of();

        var exception = assertThrows(ConfigurationException.class,
                () -> property.resolve(values::get));

        assertTrue(exception.getMessage().contains("MY_VAR"));
    }

    @Test
    void optionalString_usesDefaultWhenMissing() {
        var property = ConfigProperty.optionalString("MY_VAR", "default-value");
        Map<String, String> values = Map.of();

        var result = property.resolve(values::get);

        assertEquals("default-value", result);
    }

    @Test
    void optionalString_usesEnvVarWhenPresent() {
        var property = ConfigProperty.optionalString("MY_VAR", "default-value");
        var values = Map.of("MY_VAR", "actual-value");

        var result = property.resolve(values::get);

        assertEquals("actual-value", result);
    }

    @Test
    void requiredInt_parsesInteger() {
        var property = ConfigProperty.requiredInt("PORT");
        var values = Map.of("PORT", "8080");

        var result = property.resolve(values::get);

        assertEquals(8080, result);
    }

    @Test
    void requiredInt_throwsOnInvalidFormat() {
        var property = ConfigProperty.requiredInt("PORT");
        var values = Map.of("PORT", "not-a-number");

        var exception = assertThrows(ConfigurationException.class,
                () -> property.resolve(values::get));

        assertTrue(exception.getMessage().contains("PORT"));
    }

    @Test
    void optionalInt_usesDefaultWhenMissing() {
        var property = ConfigProperty.optionalInt("PORT", 8080);
        Map<String, String> values = Map.of();

        var result = property.resolve(values::get);

        assertEquals(8080, result);
    }

    @Test
    void requiredPath_parsesPath() {
        var property = ConfigProperty.requiredPath("DATA_DIR");
        var values = Map.of("DATA_DIR", "/var/data");

        var result = property.resolve(values::get);

        assertEquals(Path.of("/var/data"), result);
    }

    @Test
    void secretString_readsFromFile(@TempDir Path tempDir) throws IOException {
        var secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, "super-secret-password\n");

        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");
        var values = Map.of("PASSWORD_FILE", secretFile.toString());

        var result = property.resolve(values::get);

        assertEquals("super-secret-password", result);
    }

    @Test
    void secretString_prefersFileOverEnvVar(@TempDir Path tempDir) throws IOException {
        var secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, "file-secret");

        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");
        Map<String, String> values = new HashMap<>();
        values.put("PASSWORD", "env-secret");
        values.put("PASSWORD_FILE", secretFile.toString());

        var result = property.resolve(values::get);

        assertEquals("file-secret", result);
    }

    @Test
    void secretString_fallsBackToEnvVar() {
        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");
        var values = Map.of("PASSWORD", "env-secret");

        var result = property.resolve(values::get);

        assertEquals("env-secret", result);
    }

    @Test
    void secretString_throwsWhenFileNotReadable() {
        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");
        var values = Map.of("PASSWORD_FILE", "/nonexistent/path/secret.txt");

        var exception = assertThrows(ConfigurationException.class,
                () -> property.resolve(values::get));

        assertTrue(exception.getMessage().contains("Failed to read secret"));
    }

    @Test
    void isSecret_returnsTrueForSecretProperty() {
        var property = ConfigProperty.secretString("PASSWORD", "PASSWORD_FILE");

        assertTrue(property.isSecret());
    }

    @Test
    void isSecret_returnsFalseForNonSecretProperty() {
        var property = ConfigProperty.requiredString("USERNAME");

        assertFalse(property.isSecret());
    }

    @Test
    void hasDefault_returnsTrueForOptionalProperty() {
        var property = ConfigProperty.optionalString("MY_VAR", "default");

        assertTrue(property.hasDefault());
    }

    @Test
    void hasDefault_returnsFalseForRequiredProperty() {
        var property = ConfigProperty.requiredString("MY_VAR");

        assertFalse(property.hasDefault());
    }

    @Test
    void blankValue_treatedAsMissing() {
        var property = ConfigProperty.optionalString("MY_VAR", "default");
        var values = Map.of("MY_VAR", "   ");

        var result = property.resolve(values::get);

        assertEquals("default", result);
    }
}
