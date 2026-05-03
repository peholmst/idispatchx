package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Stores processed {@link CommandId} → result mappings with time-based expiration.
 * <p>
 * Named per Domain Core spec section 10.3. Package-private; access only through
 * {@link IdempotentCommandDispatcher}.
 * <p>
 * {@link #executeOnce} is the primary API: it guarantees that the supplier is called
 * at most once per command ID, even under concurrent duplicate submissions.
 */
@NullMarked
final class IdempotencyTracker implements AutoCloseable {

    private record CompletedEntry(Object result, Instant storedAt) {}

    private final ConcurrentHashMap<CommandId, CompletedEntry> completed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CommandId, CompletableFuture<Object>> inProgress = new ConcurrentHashMap<>();
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
     * Executes the supplier at most once for the given command ID.
     * <p>
     * If a result for this ID is already in the completed store, it is returned immediately.
     * If another thread is currently executing for this ID, the caller blocks until that
     * execution finishes and then receives the same result (or exception).
     * If execution fails the result is not cached, so the command remains retryable.
     */
    @SuppressWarnings("unchecked")
    <R> R executeOnce(CommandId commandId, Supplier<R> execution) {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(execution, "execution must not be null");

        // Fast path: already completed (no locking needed for ConcurrentHashMap read)
        var cached = get(commandId);
        if (cached.isPresent()) {
            return (R) cached.get();
        }

        // Atomically claim the execution slot; only one thread gets a null return
        var myFuture = new CompletableFuture<Object>();
        var theirFuture = inProgress.putIfAbsent(commandId, myFuture);

        if (theirFuture != null) {
            // Another thread is executing — wait and propagate their outcome
            try {
                return (R) theirFuture.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                if (cause instanceof Error err) throw err;
                throw e;
            }
        }

        // We own execution for this command ID
        try {
            R result = execution.get();
            store(commandId, result);
            myFuture.complete(result);
            return result;
        } catch (Throwable t) {
            // Do not cache failures — command must remain retryable
            myFuture.completeExceptionally(t);
            throw t;
        } finally {
            inProgress.remove(commandId, myFuture);
        }
    }

    /**
     * Returns the stored result for the given command ID, or empty if not found or expired.
     */
    Optional<Object> get(CommandId commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");
        var entry = completed.get(commandId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            completed.remove(commandId, entry);
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
        completed.put(commandId, new CompletedEntry(result, clock.now()));
    }

    private boolean isExpired(CompletedEntry entry) {
        return clock.now().isAfter(entry.storedAt().plus(retentionPeriod));
    }

    private void evictExpired() {
        completed.entrySet().removeIf(e -> isExpired(e.getValue()));
    }

    @Override
    public void close() {
        cleaner.shutdown();
    }
}
