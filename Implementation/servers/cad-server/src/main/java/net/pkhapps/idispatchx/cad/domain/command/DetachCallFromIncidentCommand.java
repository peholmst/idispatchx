package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to detach a call from its current incident.
 */
public record DetachCallFromIncidentCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId callId
) implements Command {

    public DetachCallFromIncidentCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }
}
