package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.Command;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogEntry;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * Dispatcher that enforces idempotency and audit logging for all commands.
 * <p>
 * REST controllers must use this dispatcher instead of calling
 * {@link CommandHandler} directly. {@code CommandHandler.handle()} is package-private;
 * only this class (in the same package) can invoke it.
 * <p>
 * Every invocation is written to the audit log, including retries. The underlying
 * handler is called at most once per unique {@link net.pkhapps.idispatchx.cad.domain.command.CommandId};
 * subsequent calls with the same ID return the cached result without re-executing.
 */
@NullMarked
public final class IdempotentCommandDispatcher {

    private final IdempotencyTracker idempotencyTracker;
    private final CommandLogPort commandLogPort;
    private final ClockPort clock;

    public IdempotentCommandDispatcher(IdempotencyTracker idempotencyTracker,
                                       CommandLogPort commandLogPort,
                                       ClockPort clock) {
        this.idempotencyTracker = Objects.requireNonNull(idempotencyTracker, "idempotencyTracker must not be null");
        this.commandLogPort = Objects.requireNonNull(commandLogPort, "commandLogPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Dispatches the command through the given handler with idempotency and audit logging.
     *
     * @param handler the handler to delegate to on a cache miss
     * @param command the command to execute
     * @return the result, either replayed from cache or from fresh execution
     */
    public <C extends Command, R> R dispatch(CommandHandler<C, R> handler, C command) {
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(command, "command must not be null");

        // Always log — retries must appear in the audit trail
        commandLogPort.log(new CommandLogEntry(
                command.commandId(),
                command.userId(),
                clock.now(),
                command.ipAddress(),
                command.getClass().getSimpleName()
        ));

        // Execute at most once per commandId; concurrent duplicates block until the first completes
        return idempotencyTracker.executeOnce(command.commandId(), () -> handler.handle(command));
    }
}
