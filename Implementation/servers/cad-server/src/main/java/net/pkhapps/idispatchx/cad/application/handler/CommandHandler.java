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

    protected final WalPort walPort;
    protected final EntityLockManager lockManager;

    protected CommandHandler(WalPort walPort, EntityLockManager lockManager) {
        this.walPort = Objects.requireNonNull(walPort, "walPort must not be null");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
    }

    /**
     * Handles the command with proper locking and WAL ordering.
     * <p>
     * If {@link #prepareBatchExecution} returns a non-null result, batch WAL write is used.
     * Otherwise falls back to {@link #prepareExecution} for a single-event write.
     *
     * @param command the command to handle
     * @return the result of handling the command
     */
    final R handle(C command) {
        Objects.requireNonNull(command, "command must not be null");

        var lockScope = determineLockScope(command);

        try (var lock = lockManager.acquire(lockScope)) {
            // Check for batch path (cross-aggregate handlers)
            var batchMutation = prepareBatchExecution(command);
            if (batchMutation != null) {
                walPort.writeBatch(batchMutation.events());
                batchMutation.applyMutation().run();
                return buildBatchResult(command, batchMutation.events());
            }

            // Single-event path (most handlers)
            var pendingMutation = prepareExecution(command);
            walPort.write(pendingMutation.event());
            pendingMutation.applyMutation().run();
            return buildResult(command, pendingMutation.event());
        }
    }

    /**
     * Determines the lock scope for the command.
     *
     * @param command the command being handled
     * @return the lock scope
     */
    protected abstract LockScope determineLockScope(C command);

    /**
     * Prepares a single-event execution.
     * <p>
     * Must NOT modify any state. Override this in single-aggregate handlers.
     * Batch handlers should override {@link #prepareBatchExecution} instead and may
     * leave this throwing {@link UnsupportedOperationException}.
     *
     * @param command the command being handled
     * @return the pending mutation containing the event and deferred state change
     */
    protected PendingMutation<? extends DomainEvent> prepareExecution(C command) {
        throw new UnsupportedOperationException(
                "Override prepareExecution or prepareBatchExecution in " + getClass().getSimpleName());
    }

    /**
     * Prepares a batch execution for cross-aggregate commands.
     * <p>
     * Default returns {@code null}, meaning the single-event path is used.
     * Cross-aggregate handlers override this to return a {@link PendingBatchMutation}.
     *
     * @param command the command being handled
     * @return the batch mutation, or {@code null} to fall back to {@link #prepareExecution}
     */
    protected PendingBatchMutation<? extends DomainEvent> prepareBatchExecution(C command) {
        return null;
    }

    /**
     * Builds the result after a single-event execution.
     *
     * @param command the command that was handled
     * @param event   the event that was written to WAL
     * @return the result
     */
    protected R buildResult(C command, DomainEvent event) {
        throw new UnsupportedOperationException(
                "Override buildResult or buildBatchResult in " + getClass().getSimpleName());
    }

    /**
     * Builds the result after a batch execution.
     * <p>
     * Override in cross-aggregate handlers that use {@link #prepareBatchExecution}.
     *
     * @param command the command that was handled
     * @param events  the events that were written to WAL
     * @return the result
     */
    protected R buildBatchResult(C command, java.util.List<? extends DomainEvent> events) {
        throw new UnsupportedOperationException(
                "Override buildBatchResult in batch command handlers: " + getClass().getSimpleName());
    }
}
