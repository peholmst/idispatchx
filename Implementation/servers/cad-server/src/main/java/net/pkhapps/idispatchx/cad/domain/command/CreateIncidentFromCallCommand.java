package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentPriority;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentType;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to create a new incident from an existing call.
 */
public record CreateIncidentFromCallCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId sourceCallId,
        @Nullable IncidentType incidentType,
        @Nullable IncidentPriority incidentPriority,
        @Nullable Location location,
        @Nullable Description description
) implements Command {

    public CreateIncidentFromCallCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceCallId, "sourceCallId must not be null");
    }
}
