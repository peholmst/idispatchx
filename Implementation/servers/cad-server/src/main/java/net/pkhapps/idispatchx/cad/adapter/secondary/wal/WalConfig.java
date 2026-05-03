package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigProperty;
import net.pkhapps.idispatchx.common.config.ConfigurationException;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the file-based WAL adapter.
 * <p>
 * Configuration is loaded from environment variables via {@link #builder()}.
 *
 * @param walDirectory         directory where WAL segment files are stored
 * @param format               encoding format (TEXT or BINARY)
 * @param corruptionMode       how to handle corrupt entries during replay
 * @param maxEntriesPerSegment maximum number of entries per segment file before rollover
 */
public record WalConfig(
        Path walDirectory,
        WalFormat format,
        CorruptionMode corruptionMode,
        int maxEntriesPerSegment
) {

    public static final int DEFAULT_MAX_ENTRIES_PER_SEGMENT = 10_000;

    public WalConfig {
        Objects.requireNonNull(walDirectory, "walDirectory must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(corruptionMode, "corruptionMode must not be null");
        if (maxEntriesPerSegment < 1) {
            throw new IllegalArgumentException("maxEntriesPerSegment must be at least 1, got " + maxEntriesPerSegment);
        }
    }

    /**
     * Creates a builder for loading configuration from environment variables.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link WalConfig} from environment variables.
     */
    public static final class Builder {

        private static final ConfigProperty<Path> walDirectoryProp =
                ConfigProperty.requiredPath("CAD_WAL_DIRECTORY");
        private static final ConfigProperty<String> formatProp =
                ConfigProperty.optionalString("CAD_WAL_FORMAT", "TEXT");
        private static final ConfigProperty<String> corruptionModeProp =
                ConfigProperty.optionalString("CAD_WAL_CORRUPTION_MODE", "STRICT");
        private static final ConfigProperty<Integer> maxEntriesProp =
                ConfigProperty.optionalInt("CAD_WAL_MAX_ENTRIES_PER_SEGMENT", DEFAULT_MAX_ENTRIES_PER_SEGMENT);

        private Builder() {
        }

        /**
         * Loads the WAL configuration using the provided config loader.
         *
         * @param loader the configuration loader
         * @return the WAL configuration
         * @throws ConfigurationException if required properties are missing or values are invalid
         */
        public WalConfig load(ConfigLoader loader) {
            Path walDirectory = loader.get(walDirectoryProp);
            WalFormat format = parseEnum(WalFormat.class, loader.get(formatProp), "CAD_WAL_FORMAT");
            CorruptionMode corruptionMode = parseEnum(CorruptionMode.class, loader.get(corruptionModeProp), "CAD_WAL_CORRUPTION_MODE");
            int maxEntries = loader.get(maxEntriesProp);
            return new WalConfig(walDirectory, format, corruptionMode, maxEntries);
        }

        private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String propertyName) {
            try {
                return Enum.valueOf(type, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid value '" + value + "' for property " + propertyName);
            }
        }
    }
}
