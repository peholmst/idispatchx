package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Marker interface for all commands in the CAD Server domain.
 * <p>
 * Commands represent intent to change the state of the system. Each command
 * carries a unique {@link CommandId} for idempotency tracking, as well as the
 * identity of the issuing user and their IP address for audit logging.
 */
public interface Command {

    /**
     * Returns the unique identifier for this command, used for idempotency.
     */
    CommandId commandId();

    /**
     * Returns the identity of the user who issued this command.
     * Use {@link UserId#SYSTEM} when the system has acted on its own.
     */
    UserId userId();

    /**
     * Returns the IP address of the user who issued this command, or {@code null} if not available.
     */
    @Nullable IPAddress ipAddress();
}
