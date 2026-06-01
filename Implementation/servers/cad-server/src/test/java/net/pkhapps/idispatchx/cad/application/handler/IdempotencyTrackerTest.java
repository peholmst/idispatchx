package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTrackerTest {

    @Test
    void get_returnsEmptyForUnknownCommandId() {
        try (var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now)) {
            assertTrue(tracker.get(CommandId.generate()).isEmpty());
        }
    }

    @Test
    void get_returnsStoredResultAfterStore() {
        try (var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now)) {
            var id = CommandId.generate();
            tracker.store(id, "result");
            assertEquals("result", tracker.get(id).orElseThrow());
        }
    }

    @Test
    void get_returnsEmptyAfterRetentionPeriodExpires() {
        var now = new AtomicReference<>(Instant.now());
        ClockPort controllableClock = now::get;

        try (var tracker = new IdempotencyTracker(Duration.ofSeconds(30), controllableClock)) {
            var id = CommandId.generate();
            tracker.store(id, "result");

            assertTrue(tracker.get(id).isPresent(), "should be present before expiry");

            // Advance clock past retention period
            now.set(now.get().plusSeconds(31));

            assertTrue(tracker.get(id).isEmpty(), "should be empty after expiry");
        }
    }

    @Test
    void constructor_rejectsZeroRetentionPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyTracker(Duration.ZERO, Instant::now));
    }

    @Test
    void constructor_rejectsNegativeRetentionPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyTracker(Duration.ofSeconds(-1), Instant::now));
    }

    @Test
    void close_shutsDownCleanly() {
        var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now);
        assertDoesNotThrow(tracker::close);
    }

    @Test
    void executeOnce_callsSupplierExactlyOnceForConcurrentDuplicates() throws Exception {
        try (var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now)) {
            var id = CommandId.generate();
            var executionCount = new AtomicInteger(0);
            var startLatch = new CountDownLatch(1);

            // Supplier blocks until startLatch released so both threads enter executeOnce first
            var blockingSupplier = (java.util.function.Supplier<String>) () -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executionCount.incrementAndGet();
                return "result";
            };

            var t1 = new Thread(() -> tracker.executeOnce(id, blockingSupplier));
            var t2 = new Thread(() -> tracker.executeOnce(id, blockingSupplier));
            t1.start();
            t2.start();
            Thread.sleep(20); // let both threads reach executeOnce
            startLatch.countDown();
            t1.join(5000);
            t2.join(5000);

            assertEquals(1, executionCount.get());
            assertEquals("result", tracker.get(id).orElseThrow());
        }
    }

    @Test
    void executeOnce_cachesNullResultForVoidCommands() {
        try (var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now)) {
            var id = CommandId.generate();
            var executionCount = new AtomicInteger(0);

            Void first = tracker.executeOnce(id, () -> {
                executionCount.incrementAndGet();
                return null;
            });
            Void second = tracker.executeOnce(id, () -> {
                executionCount.incrementAndGet();
                return null;
            });

            assertNull(first);
            assertNull(second);
            assertEquals(1, executionCount.get());
        }
    }

    @Test
    void executeOnce_allowsRetryAfterSupplierThrows() {
        try (var tracker = new IdempotencyTracker(Duration.ofMinutes(5), Instant::now)) {
            var id = CommandId.generate();

            assertThrows(RuntimeException.class,
                    () -> tracker.executeOnce(id, () -> { throw new RuntimeException("fail"); }));

            // Command is not cached — retry should execute the supplier again
            var result = tracker.executeOnce(id, () -> "recovered");
            assertEquals("recovered", result);
        }
    }
}
