package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.Command;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.Objects;

/**
 * Abstract base class for command handlers that enforces the WAL-before-state pattern.
 * <p>
 * The handle method:
 * <ol>
 *   <li>Acquires locks on affected aggregate(s)</li>
 *   <li>Validates and prepares the mutation (no state change yet)</li>
 *   <li>Writes the event to WAL and blocks until synced</li>
 *   <li>Applies the state mutation only after WAL confirms durability</li>
 *   <li>Returns the result</li>
 * </ol>
 *
 * @param <C> the command type
 * @param <R> the result type
 */
public abstract class CommandHandler<C extends Command, R> {

    private final WalPort walPort;
    private final EntityLockManager lockManager;

    protected CommandHandler(WalPort walPort, EntityLockManager lockManager) {
        this.walPort = Objects.requireNonNull(walPort, "walPort must not be null");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
    }

    /**
     * Handles the command with proper locking and WAL ordering.
     *
     * @param command the command to handle
     * @return the result of handling the command
     */
    public final R handle(C command) {
        Objects.requireNonNull(command, "command must not be null");

        var lockScope = determineLockScope(command);

        try (var lock = lockManager.acquire(lockScope)) {
            // 1. Validate command and compute pending mutation (NO state change yet)
            var pendingMutation = prepareExecution(command);

            // 2. Write event to WAL and block until synced to disk
            walPort.write(pendingMutation.event());

            // 3. ONLY after successful WAL write, apply state mutation
            pendingMutation.applyMutation().run();

            // 4. Return result
            return buildResult(command, pendingMutation.event());
        }
    }

    /**
     * Determines the lock scope for the command.
     * <p>
     * Subclasses must return the appropriate lock scope identifying which
     * aggregates need to be locked before executing this command.
     *
     * @param command the command being handled
     * @return the lock scope
     */
    protected abstract LockScope determineLockScope(C command);

    /**
     * Prepares the execution by validating the command and creating the pending mutation.
     * <p>
     * This method must NOT modify any state. It should:
     * <ul>
     *   <li>Validate that the command can be executed</li>
     *   <li>Create the domain event that represents the change</li>
     *   <li>Create a runnable that will apply the state mutation</li>
     * </ul>
     *
     * @param command the command being handled
     * @return the pending mutation containing the event and deferred state change
     */
    protected abstract PendingMutation<? extends DomainEvent> prepareExecution(C command);

    /**
     * Builds the result after successful execution.
     *
     * @param command the command that was handled
     * @param event   the event that was written to WAL
     * @return the result
     */
    protected abstract R buildResult(C command, DomainEvent event);
}
