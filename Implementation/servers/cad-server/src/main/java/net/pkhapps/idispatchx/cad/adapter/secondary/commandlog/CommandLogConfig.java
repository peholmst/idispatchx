package net.pkhapps.idispatchx.cad.adapter.secondary.commandlog;

import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigProperty;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the file-based command log adapter.
 * <p>
 * Configuration is loaded from environment variables via {@link #builder(String, String, String)}.
 *
 * @param logDirectory    the directory where log files are written
 * @param baseFileName    the base name of the log file (e.g. {@value #DEFAULT_BASE_FILE_NAME})
 * @param maxFileSizeMb   the maximum size of a single log file in megabytes before rotation
 */
public record CommandLogConfig(
        Path logDirectory,
        String baseFileName,
        int maxFileSizeMb
) {

    /**
     * Default log file base name.
     */
    public static final String DEFAULT_BASE_FILE_NAME = "commands.log";

    /**
     * Default maximum log file size in megabytes.
     */
    public static final int DEFAULT_MAX_FILE_SIZE_MB = 100;

    /**
     * Creates a command log configuration with validation.
     */
    public CommandLogConfig {
        Objects.requireNonNull(logDirectory, "logDirectory must not be null");
        Objects.requireNonNull(baseFileName, "baseFileName must not be null");
        if (baseFileName.isBlank()) {
            throw new IllegalArgumentException("baseFileName must not be blank");
        }
        if (maxFileSizeMb < 1) {
            throw new IllegalArgumentException("maxFileSizeMb must be at least 1, got " + maxFileSizeMb);
        }
    }

    /**
     * Creates a builder for loading configuration from environment variables.
     *
     * @param logDirectoryEnvVar  environment variable for the log directory path
     * @param baseFileNameEnvVar  environment variable for the log file base name
     * @param maxFileSizeMbEnvVar environment variable for the maximum file size in megabytes
     * @return the builder
     */
    public static Builder builder(String logDirectoryEnvVar,
                                  String baseFileNameEnvVar,
                                  String maxFileSizeMbEnvVar) {
        return new Builder(logDirectoryEnvVar, baseFileNameEnvVar, maxFileSizeMbEnvVar);
    }

    /**
     * Builder for creating {@link CommandLogConfig} from environment variables.
     */
    public static final class Builder {

        private final ConfigProperty<Path> logDirectoryProperty;
        private final ConfigProperty<String> baseFileNameProperty;
        private final ConfigProperty<Integer> maxFileSizeMbProperty;

        private Builder(String logDirectoryEnvVar,
                        String baseFileNameEnvVar,
                        String maxFileSizeMbEnvVar) {
            this.logDirectoryProperty = ConfigProperty.requiredPath(logDirectoryEnvVar);
            this.baseFileNameProperty = ConfigProperty.optionalString(baseFileNameEnvVar, DEFAULT_BASE_FILE_NAME);
            this.maxFileSizeMbProperty = ConfigProperty.optionalInt(maxFileSizeMbEnvVar, DEFAULT_MAX_FILE_SIZE_MB);
        }

        /**
         * Loads the command log configuration using the provided config loader.
         *
         * @param loader the configuration loader
         * @return the command log configuration
         * @throws net.pkhapps.idispatchx.common.config.ConfigurationException if required properties are missing
         */
        public CommandLogConfig load(ConfigLoader loader) {
            return new CommandLogConfig(
                    loader.get(logDirectoryProperty),
                    loader.get(baseFileNameProperty),
                    loader.get(maxFileSizeMbProperty)
            );
        }
    }
}
