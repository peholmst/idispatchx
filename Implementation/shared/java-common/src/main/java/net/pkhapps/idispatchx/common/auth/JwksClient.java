package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Client for fetching and caching JSON Web Key Sets (JWKS) from an OIDC provider.
 * <p>
 * The client caches the JWKS for a configurable time-to-live (TTL) and automatically
 * refreshes when a key is not found, supporting key rotation scenarios.
 * <p>
 * This class is thread-safe.
 */
public final class JwksClient implements JwksKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksClient.class);

    /**
     * Default cache TTL of 5 minutes.
     */
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Default HTTP request timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final URI jwksUri;
    private final Duration cacheTtl;
    private final HttpClient httpClient;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private @Nullable JWKSet cachedJwkSet;
    private @Nullable Instant cacheExpiry;

    /**
     * Creates a new JWKS client with default settings.
     *
     * @param jwksUri the JWKS endpoint URI
     */
    public JwksClient(URI jwksUri) {
        this(jwksUri, DEFAULT_CACHE_TTL, createDefaultHttpClient());
    }

    /**
     * Creates a new JWKS client with custom settings.
     *
     * @param jwksUri    the JWKS endpoint URI
     * @param cacheTtl   the cache time-to-live
     * @param httpClient the HTTP client to use
     */
    public JwksClient(URI jwksUri, Duration cacheTtl, HttpClient httpClient) {
        this.jwksUri = Objects.requireNonNull(jwksUri, "jwksUri must not be null");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");

        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }

        log.info("JWKS client initialized for {}", jwksUri);
    }

    private static HttpClient createDefaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Gets a JWK by its key ID.
     * <p>
     * If the key is not found in the cache, the JWKS is refreshed from the server
     * to support key rotation scenarios.
     *
     * @param keyId the key ID (kid)
     * @return the JWK, or null if not found
     * @throws JwksException if fetching the JWKS fails
     */
    public @Nullable JWK getKey(String keyId) {
        Objects.requireNonNull(keyId, "keyId must not be null");

        // Try to get from cache first
        var jwkSet = getCachedJwkSet();
        if (jwkSet != null) {
            var key = jwkSet.getKeyByKeyId(keyId);
            if (key != null) {
                return key;
            }
            // Key not found, refresh to handle key rotation
            log.debug("Key {} not found in cache, refreshing JWKS", keyId);
        }

        // Refresh and try again
        jwkSet = refreshJwkSet();
        return jwkSet.getKeyByKeyId(keyId);
    }

    /**
     * Gets the cached JWKS if valid, or null if expired/not cached.
     */
    private @Nullable JWKSet getCachedJwkSet() {
        lock.readLock().lock();
        try {
            if (cachedJwkSet != null && cacheExpiry != null && Instant.now().isBefore(cacheExpiry)) {
                return cachedJwkSet;
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Refreshes the JWKS from the server.
     */
    private JWKSet refreshJwkSet() {
        lock.writeLock().lock();
        try {
            // Double-check in case another thread already refreshed
            if (cachedJwkSet != null && cacheExpiry != null && Instant.now().isBefore(cacheExpiry)) {
                return cachedJwkSet;
            }

            log.debug("Fetching JWKS from {}", jwksUri);
            var jwkSet = fetchJwkSet();
            cachedJwkSet = jwkSet;
            cacheExpiry = Instant.now().plus(cacheTtl);
            log.debug("JWKS cached with {} keys, expires at {}", jwkSet.getKeys().size(), cacheExpiry);
            return jwkSet;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Fetches the JWKS from the server.
     */
    private JWKSet fetchJwkSet() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(jwksUri)
                    .timeout(DEFAULT_TIMEOUT)
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new JwksException("JWKS endpoint returned status " + response.statusCode());
            }

            return JWKSet.parse(response.body());
        } catch (IOException e) {
            throw new JwksException("Failed to fetch JWKS from " + jwksUri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JwksException("JWKS fetch interrupted", e);
        } catch (ParseException e) {
            throw new JwksException("Failed to parse JWKS: " + e.getMessage(), e);
        }
    }

    /**
     * Forces a refresh of the cached JWKS.
     *
     * @throws JwksException if fetching the JWKS fails
     */
    public void refresh() {
        lock.writeLock().lock();
        try {
            cachedJwkSet = null;
            cacheExpiry = null;
        } finally {
            lock.writeLock().unlock();
        }
        refreshJwkSet();
    }

    /**
     * Returns the JWKS endpoint URI.
     *
     * @return the JWKS URI
     */
    public URI getJwksUri() {
        return jwksUri;
    }
}
