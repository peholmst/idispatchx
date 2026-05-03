package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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
}
