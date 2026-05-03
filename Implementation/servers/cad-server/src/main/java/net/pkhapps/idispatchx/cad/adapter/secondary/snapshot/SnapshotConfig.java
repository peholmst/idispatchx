package net.pkhapps.idispatchx.cad.adapter.secondary.snapshot;

import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalFormat;
import net.pkhapps.idispatchx.common.config.ConfigLoader;
import net.pkhapps.idispatchx.common.config.ConfigProperty;
import net.pkhapps.idispatchx.common.config.ConfigurationException;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the file-based snapshot adapter.
 * <p>
 * Per ADR-0006, snapshots must use the same format as the WAL.
 *
 * @param snapshotDirectory directory where snapshot files are stored
 * @param format            encoding format — must match the WAL format
 */
public record SnapshotConfig(
        Path snapshotDirectory,
        WalFormat format
) {

    public SnapshotConfig {
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory must not be null");
        Objects.requireNonNull(format, "format must not be null");
    }

    /**
     * Creates a builder for loading configuration from environment variables.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link SnapshotConfig} from environment variables.
     */
    public static final class Builder {

        private static final ConfigProperty<Path> snapshotDirectoryProp =
                ConfigProperty.requiredPath("CAD_SNAPSHOT_DIRECTORY");
        private static final ConfigProperty<String> formatProp =
                ConfigProperty.optionalString("CAD_SNAPSHOT_FORMAT", "TEXT");

        private Builder() {
        }

        /**
         * Loads the snapshot configuration using the provided config loader.
         *
         * @param loader the configuration loader
         * @return the snapshot configuration
         * @throws ConfigurationException if required properties are missing or values are invalid
         */
        public SnapshotConfig load(ConfigLoader loader) {
            Path snapshotDirectory = loader.get(snapshotDirectoryProp);
            WalFormat format = parseEnum(loader.get(formatProp));
            return new SnapshotConfig(snapshotDirectory, format);
        }

        private static WalFormat parseEnum(String value) {
            try {
                return WalFormat.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid value '" + value + "' for property CAD_SNAPSHOT_FORMAT");
            }
        }
    }
}
