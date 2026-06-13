package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort;

import java.time.Duration;
import java.util.Objects;

/**
 * Factory for creating fully-configured {@link IdempotentCommandDispatcher} instances.
 * <p>
 * Encapsulates construction of the package-private {@link IdempotencyTracker} so that
 * call sites outside the package do not need visibility into the tracker.
 */
public final class DispatcherFactory {

    private DispatcherFactory() {}

    /**
     * Creates a new {@link IdempotentCommandDispatcher} with a dedicated
     * {@link IdempotencyTracker}.
     *
     * @param commandLog      port for writing audit log entries
     * @param clock           port for obtaining the current time
     * @param retentionPeriod how long processed command IDs are retained for idempotency
     * @return a ready-to-use dispatcher
     */
    public static IdempotentCommandDispatcher create(
            CommandLogPort commandLog, ClockPort clock, Duration retentionPeriod) {
        Objects.requireNonNull(commandLog, "commandLog must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(retentionPeriod, "retentionPeriod must not be null");
        return new IdempotentCommandDispatcher(
                new IdempotencyTracker(retentionPeriod, clock),
                commandLog,
                clock);
    }
}
