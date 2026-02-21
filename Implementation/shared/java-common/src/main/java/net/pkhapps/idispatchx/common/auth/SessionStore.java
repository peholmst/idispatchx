package net.pkhapps.idispatchx.common.auth;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for tracking revoked sessions.
 * <p>
 * When a back-channel logout notification is received, the session ID
 * is added to this store. Subsequent token validation should check
 * this store to determine if a session has been revoked.
 * <p>
 * Revoked sessions are automatically removed after a configurable TTL
 * to prevent unbounded memory growth. The TTL should be at least as long
 * as the maximum token lifetime.
 * <p>
 * This implementation is thread-safe but does not persist across restarts.
 * For production deployments with multiple server instances, consider using
 * a distributed cache like Redis.
 */
public final class SessionStore implements AutoCloseable {

    /**
     * Default session revocation TTL of 1 hour.
     * <p>
     * This should be longer than the maximum access token lifetime to ensure
     * revoked tokens are rejected until they expire naturally.
     */
    public static final Duration DEFAULT_REVOCATION_TTL = Duration.ofHours(1);

    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentMap<String, Instant> revokedSessions = new ConcurrentHashMap<>();
    private final Duration revocationTtl;
    private final @Nullable ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a new session store with the default revocation TTL.
     */
    public SessionStore() {
        this(DEFAULT_REVOCATION_TTL);
    }

    /**
     * Creates a new session store with a custom revocation TTL.
     *
     * @param revocationTtl how long to keep revoked sessions in the store
     */
    public SessionStore(Duration revocationTtl) {
        this(revocationTtl, true);
    }

    /**
     * Creates a new session store with optional cleanup scheduling.
     *
     * @param revocationTtl   how long to keep revoked sessions in the store
     * @param scheduleCleanup whether to schedule periodic cleanup (false for testing)
     */
    SessionStore(Duration revocationTtl, boolean scheduleCleanup) {
        this.revocationTtl = Objects.requireNonNull(revocationTtl, "revocationTtl must not be null");

        if (revocationTtl.isNegative() || revocationTtl.isZero()) {
            throw new IllegalArgumentException("revocationTtl must be positive");
        }

        if (scheduleCleanup) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                var thread = new Thread(r, "session-store-cleanup");
                thread.setDaemon(true);
                return thread;
            });
            cleanupExecutor.scheduleAtFixedRate(
                    this::cleanupExpired,
                    CLEANUP_INTERVAL.toSeconds(),
                    CLEANUP_INTERVAL.toSeconds(),
                    TimeUnit.SECONDS
            );
        } else {
            this.cleanupExecutor = null;
        }
    }

    /**
     * Marks a session as revoked.
     *
     * @param sessionId the session ID to revoke
     */
    public void revokeSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        revokedSessions.put(sessionId, Instant.now().plus(revocationTtl));
    }

    /**
     * Marks a session as revoked with a specific expiration time.
     *
     * @param sessionId  the session ID to revoke
     * @param expiration when the revocation record should expire
     */
    public void revokeSession(String sessionId, Instant expiration) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(expiration, "expiration must not be null");
        revokedSessions.put(sessionId, expiration);
    }

    /**
     * Checks if a session has been revoked.
     *
     * @param sessionId the session ID to check
     * @return true if the session has been revoked and the revocation hasn't expired
     */
    public boolean isRevoked(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        var expiration = revokedSessions.get(sessionId);
        if (expiration == null) {
            return false;
        }
        if (Instant.now().isAfter(expiration)) {
            // Revocation record has expired, remove it
            revokedSessions.remove(sessionId, expiration);
            return false;
        }
        return true;
    }

    /**
     * Validates that a session is not revoked.
     *
     * @param sessionId the session ID to validate
     * @throws TokenValidationException if the session has been revoked
     */
    public void validateSession(String sessionId) {
        if (isRevoked(sessionId)) {
            throw new TokenValidationException(
                    TokenValidationException.ErrorCode.TOKEN_EXPIRED,
                    "Session has been revoked");
        }
    }

    /**
     * Removes expired revocation records.
     * <p>
     * This is called automatically on a schedule, but can be called
     * manually for testing or maintenance.
     */
    public void cleanupExpired() {
        var now = Instant.now();
        revokedSessions.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    /**
     * Returns the number of revoked sessions currently stored.
     *
     * @return the number of revoked sessions
     */
    public int size() {
        return revokedSessions.size();
    }

    /**
     * Clears all revoked sessions.
     */
    public void clear() {
        revokedSessions.clear();
    }

    /**
     * Shuts down the cleanup executor and releases resources.
     */
    @Override
    public void close() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
