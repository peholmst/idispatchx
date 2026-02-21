package net.pkhapps.idispatchx.common.config;

import java.util.Objects;

/**
 * Database connection configuration.
 * <p>
 * Configuration is loaded from environment variables with optional file-based secret support.
 *
 * @param url      the JDBC connection URL
 * @param username the database username
 * @param password the database password
 * @param poolSize the connection pool size
 */
public record DatabaseConfig(
        String url,
        String username,
        String password,
        int poolSize
) {

    /**
     * Default connection pool size.
     */
    public static final int DEFAULT_POOL_SIZE = 10;

    /**
     * Creates a database configuration with validation.
     *
     * @param url      the JDBC connection URL
     * @param username the database username
     * @param password the database password
     * @param poolSize the connection pool size
     */
    public DatabaseConfig {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1, got " + poolSize);
        }
        if (poolSize > 100) {
            throw new IllegalArgumentException("poolSize must not exceed 100, got " + poolSize);
        }
    }

    /**
     * Creates a database configuration builder for loading from environment variables.
     *
     * @param urlEnvVar          environment variable for JDBC URL
     * @param userEnvVar         environment variable for username
     * @param passwordEnvVar     environment variable for password
     * @param passwordFileEnvVar environment variable for password file path
     * @param poolSizeEnvVar     environment variable for pool size
     * @return the builder
     */
    public static Builder builder(String urlEnvVar,
                                  String userEnvVar,
                                  String passwordEnvVar,
                                  String passwordFileEnvVar,
                                  String poolSizeEnvVar) {
        return new Builder(urlEnvVar, userEnvVar, passwordEnvVar, passwordFileEnvVar, poolSizeEnvVar);
    }

    /**
     * Builder for creating DatabaseConfig from environment variables.
     */
    public static final class Builder {

        private final ConfigProperty<String> urlProperty;
        private final ConfigProperty<String> userProperty;
        private final ConfigProperty<String> passwordProperty;
        private final ConfigProperty<Integer> poolSizeProperty;

        private Builder(String urlEnvVar,
                        String userEnvVar,
                        String passwordEnvVar,
                        String passwordFileEnvVar,
                        String poolSizeEnvVar) {
            this.urlProperty = ConfigProperty.requiredString(urlEnvVar);
            this.userProperty = ConfigProperty.requiredString(userEnvVar);
            this.passwordProperty = ConfigProperty.secretString(passwordEnvVar, passwordFileEnvVar);
            this.poolSizeProperty = ConfigProperty.optionalInt(poolSizeEnvVar, DEFAULT_POOL_SIZE);
        }

        /**
         * Loads the database configuration using the provided config loader.
         *
         * @param loader the configuration loader
         * @return the database configuration
         * @throws ConfigurationException if required properties are missing
         */
        public DatabaseConfig load(ConfigLoader loader) {
            return new DatabaseConfig(
                    loader.get(urlProperty),
                    loader.get(userProperty),
                    loader.get(passwordProperty),
                    loader.get(poolSizeProperty)
            );
        }
    }

    @Override
    public String toString() {
        // Do not include password in toString for security
        return "DatabaseConfig[url=" + url + ", username=" + username + ", poolSize=" + poolSize + "]";
    }
}
