package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to update call details (caller information, location, description).
 * <p>
 * Only non-null fields are applied; absent fields remain unchanged.
 * Use {@link SetCallOutcomeCommand} to set the call outcome and rationale.
 */
public record UpdateCallDetailsCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId callId,
        @Nullable CallerName callerName,
        @Nullable PhoneNumber callerPhoneNumber,
        @Nullable Location location,
        @Nullable Description description
) implements Command {

    public UpdateCallDetailsCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }
}
