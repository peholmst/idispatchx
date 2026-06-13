package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to set the outcome and optional rationale of an active call.
 * <p>
 * Outcomes {@code INCIDENT_CREATED} and {@code ATTACHED_TO_INCIDENT} may not be set here;
 * use {@link CreateIncidentFromCallCommand} or {@link AttachCallToIncidentCommand} instead.
 */
public record SetCallOutcomeCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId callId,
        CallOutcome outcome,
        @Nullable Description outcomeRationale
) implements Command {

    public SetCallOutcomeCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == CallOutcome.INCIDENT_CREATED || outcome == CallOutcome.ATTACHED_TO_INCIDENT) {
            throw new IllegalArgumentException(
                    "Outcome " + outcome + " must be set via CreateIncidentFromCallCommand or AttachCallToIncidentCommand");
        }
    }
}
