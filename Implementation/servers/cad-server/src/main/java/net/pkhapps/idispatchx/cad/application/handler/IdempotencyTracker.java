package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stores processed {@link CommandId} → result mappings with time-based expiration.
 * <p>
 * Named per Domain Core spec section 10.3. Package-private; access only through
 * {@link IdempotentCommandDispatcher}.
 */
@NullMarked
final class IdempotencyTracker implements AutoCloseable {

    private record StoredEntry(Object result, Instant storedAt) {}

    private final ConcurrentHashMap<CommandId, StoredEntry> entries = new ConcurrentHashMap<>();
    private final Duration retentionPeriod;
    private final ClockPort clock;
    private final ScheduledExecutorService cleaner;

    IdempotencyTracker(Duration retentionPeriod, ClockPort clock) {
        this.retentionPeriod = Objects.requireNonNull(retentionPeriod, "retentionPeriod must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (retentionPeriod.isNegative() || retentionPeriod.isZero()) {
            throw new IllegalArgumentException("retentionPeriod must be positive");
        }
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "idempotency-cleaner");
            t.setDaemon(true);
            return t;
        });
        long intervalSeconds = Math.max(1, retentionPeriod.toSeconds() / 2);
        cleaner.scheduleAtFixedRate(this::evictExpired, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Returns the stored result for the given command ID, or empty if not found or expired.
     */
    Optional<Object> get(CommandId commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");
        var entry = entries.get(commandId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            entries.remove(commandId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    /**
     * Stores the result for the given command ID.
     */
    void store(CommandId commandId, Object result) {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        entries.put(commandId, new StoredEntry(result, clock.now()));
    }

    private boolean isExpired(StoredEntry entry) {
        return clock.now().isAfter(entry.storedAt().plus(retentionPeriod));
    }

    private void evictExpired() {
        entries.entrySet().removeIf(e -> isExpired(e.getValue()));
    }

    @Override
    public void close() {
        cleaner.shutdown();
    }
}
