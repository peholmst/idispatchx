package net.pkhapps.idispatchx.common.config;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/**
 * Configuration loader that reads values from multiple sources with the following precedence:
 * <ol>
 *   <li>Environment variables (highest priority)</li>
 *   <li>System properties</li>
 *   <li>Properties file (lowest priority)</li>
 * </ol>
 * <p>
 * For secrets, values can also be read from files specified by a separate environment variable,
 * which takes precedence over the direct value.
 * <p>
 * Secret values are never logged.
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private final Properties properties;
    private final Function<String, @Nullable String> envProvider;

    /**
     * Creates a new configuration loader with the given properties.
     *
     * @param properties  the properties to use as fallback
     * @param envProvider function to retrieve environment variables (for testing)
     */
    public ConfigLoader(Properties properties, Function<String, @Nullable String> envProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.envProvider = Objects.requireNonNull(envProvider, "envProvider must not be null");
    }

    /**
     * Creates a configuration loader with an empty properties file.
     *
     * @return the configuration loader
     */
    public static ConfigLoader create() {
        return new ConfigLoader(new Properties(), System::getenv);
    }

    /**
     * Creates a configuration loader that loads properties from the specified file.
     *
     * @param propertiesPath path to the properties file
     * @return the configuration loader
     * @throws ConfigurationException if the file cannot be read
     */
    public static ConfigLoader fromFile(Path propertiesPath) {
        var properties = new Properties();
        if (Files.exists(propertiesPath)) {
            try (InputStream is = Files.newInputStream(propertiesPath)) {
                properties.load(is);
                log.info("Loaded configuration from {}", propertiesPath);
            } catch (IOException e) {
                throw new ConfigurationException(
                        "Failed to load configuration from " + propertiesPath + ": " + e.getMessage(), e);
            }
        } else {
            log.debug("Configuration file not found: {}", propertiesPath);
        }
        return new ConfigLoader(properties, System::getenv);
    }

    /**
     * Creates a configuration loader that loads properties from the classpath.
     *
     * @param resourcePath path to the resource (e.g., "application.properties")
     * @return the configuration loader
     */
    public static ConfigLoader fromClasspath(String resourcePath) {
        var properties = new Properties();
        try (var is = ConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                properties.load(is);
                log.info("Loaded configuration from classpath: {}", resourcePath);
            } else {
                log.debug("Configuration resource not found on classpath: {}", resourcePath);
            }
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to load configuration from classpath " + resourcePath + ": " + e.getMessage(), e);
        }
        return new ConfigLoader(properties, System::getenv);
    }

    /**
     * Resolves the value of a configuration property.
     *
     * @param property the property definition
     * @param <T>      the type of the property value
     * @return the resolved value
     * @throws ConfigurationException if a required property is not set or conversion fails
     */
    public <T> T get(ConfigProperty<T> property) {
        T value = property.resolve(this::getValue);
        logProperty(property, value);
        return value;
    }

    private @Nullable String getValue(String name) {
        // Environment variables take precedence
        var envValue = envProvider.apply(name);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        // Then system properties
        var sysValue = System.getProperty(name);
        if (sysValue != null && !sysValue.isBlank()) {
            return sysValue;
        }

        // Finally, properties file
        return properties.getProperty(name);
    }

    private <T> void logProperty(ConfigProperty<T> property, T value) {
        if (property.isSecret()) {
            log.debug("Configuration property {} = [REDACTED]", property.name());
        } else {
            log.debug("Configuration property {} = {}", property.name(), value);
        }
    }
}
