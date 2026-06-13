package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to end an active call.
 * <p>
 * The call's {@code outcome} and {@code outcomeRationale} must be set via
 * {@link SetCallOutcomeCommand} (or by attaching/creating an incident) before issuing this command.
 */
public record EndCallCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId callId
) implements Command {

    public EndCallCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }
}
