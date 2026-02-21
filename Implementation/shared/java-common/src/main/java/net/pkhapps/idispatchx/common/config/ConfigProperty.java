package net.pkhapps.idispatchx.common.config;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A configuration property definition that supports reading values from environment variables,
 * system properties, or files (for secrets).
 * <p>
 * Properties can be required or optional (with a default value). For sensitive values such as
 * passwords, a separate file path property can be used to read the value from a file.
 *
 * @param <T> the type of the property value
 */
public final class ConfigProperty<T> {

    private final String name;
    private final Function<String, T> converter;
    private final @Nullable T defaultValue;
    private final @Nullable String secretFileEnvVar;
    private final boolean secret;

    private ConfigProperty(String name,
                           Function<String, T> converter,
                           @Nullable T defaultValue,
                           @Nullable String secretFileEnvVar,
                           boolean secret) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.defaultValue = defaultValue;
        this.secretFileEnvVar = secretFileEnvVar;
        this.secret = secret;
    }

    /**
     * Creates a required string property.
     *
     * @param envVarName the environment variable name
     * @return the property definition
     */
    public static ConfigProperty<String> requiredString(String envVarName) {
        return new ConfigProperty<>(envVarName, Function.identity(), null, null, false);
    }

    /**
     * Creates an optional string property with a default value.
     *
     * @param envVarName   the environment variable name
     * @param defaultValue the default value
     * @return the property definition
     */
    public static ConfigProperty<String> optionalString(String envVarName, String defaultValue) {
        return new ConfigProperty<>(envVarName, Function.identity(), defaultValue, null, false);
    }

    /**
     * Creates a required integer property.
     *
     * @param envVarName the environment variable name
     * @return the property definition
     */
    public static ConfigProperty<Integer> requiredInt(String envVarName) {
        return new ConfigProperty<>(envVarName, Integer::parseInt, null, null, false);
    }

    /**
     * Creates an optional integer property with a default value.
     *
     * @param envVarName   the environment variable name
     * @param defaultValue the default value
     * @return the property definition
     */
    public static ConfigProperty<Integer> optionalInt(String envVarName, int defaultValue) {
        return new ConfigProperty<>(envVarName, Integer::parseInt, defaultValue, null, false);
    }

    /**
     * Creates a required path property.
     *
     * @param envVarName the environment variable name
     * @return the property definition
     */
    public static ConfigProperty<Path> requiredPath(String envVarName) {
        return new ConfigProperty<>(envVarName, Path::of, null, null, false);
    }

    /**
     * Creates an optional path property with a default value.
     *
     * @param envVarName   the environment variable name
     * @param defaultValue the default value
     * @return the property definition
     */
    public static ConfigProperty<Path> optionalPath(String envVarName, Path defaultValue) {
        return new ConfigProperty<>(envVarName, Path::of, defaultValue, null, false);
    }

    /**
     * Creates a secret string property that can be read from an environment variable or a file.
     * The file path is specified by a separate environment variable.
     *
     * @param envVarName        the environment variable name for the direct value
     * @param secretFileEnvVar  the environment variable name for the file path
     * @return the property definition
     */
    public static ConfigProperty<String> secretString(String envVarName, String secretFileEnvVar) {
        return new ConfigProperty<>(envVarName, Function.identity(), null, secretFileEnvVar, true);
    }

    /**
     * Returns the name (environment variable name) of this property.
     *
     * @return the property name
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether this property is marked as a secret.
     *
     * @return true if this property is a secret
     */
    public boolean isSecret() {
        return secret;
    }

    /**
     * Returns whether this property has a default value.
     *
     * @return true if this property has a default value
     */
    public boolean hasDefault() {
        return defaultValue != null;
    }

    /**
     * Returns the secret file environment variable name, if any.
     *
     * @return the secret file environment variable name, or empty if not set
     */
    public Optional<String> secretFileEnvVar() {
        return Optional.ofNullable(secretFileEnvVar);
    }

    /**
     * Resolves the property value using the provided value source.
     *
     * @param valueSource a function that returns the value for a given environment variable name
     * @return the resolved value
     * @throws ConfigurationException if a required property is not set or conversion fails
     */
    public T resolve(Function<String, @Nullable String> valueSource) {
        // First, try to read from a secret file if configured
        if (secretFileEnvVar != null) {
            var filePath = valueSource.apply(secretFileEnvVar);
            if (filePath != null && !filePath.isBlank()) {
                return readFromFile(Path.of(filePath));
            }
        }

        // Then, try to read from the environment variable
        var value = valueSource.apply(name);
        if (value != null && !value.isBlank()) {
            return convert(value);
        }

        // Fall back to default value
        if (defaultValue != null) {
            return defaultValue;
        }

        throw new ConfigurationException("Required configuration property not set: " + name);
    }

    private T convert(String value) {
        try {
            return converter.apply(value);
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Failed to convert value for property " + name + ": " + e.getMessage(), e);
        }
    }

    private T readFromFile(Path path) {
        try {
            var content = Files.readString(path).trim();
            return convert(content);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to read secret from file for property " + name + ": " + e.getMessage(), e);
        }
    }
}
