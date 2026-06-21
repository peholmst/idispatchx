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
 * A non-null field value replaces the existing value. A {@code null} value with the corresponding
 * {@code clearXxx = false} leaves the field unchanged. A {@code null} value with {@code clearXxx = true}
 * explicitly clears the field to {@code null}.
 * Use {@link SetCallOutcomeCommand} to set the call outcome and rationale.
 */
public record UpdateCallDetailsCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        CallId callId,
        @Nullable CallerName callerName,
        boolean clearCallerName,
        @Nullable PhoneNumber callerPhoneNumber,
        boolean clearCallerPhoneNumber,
        @Nullable Location location,
        boolean clearLocation,
        @Nullable Description description,
        boolean clearDescription
) implements Command {

    public UpdateCallDetailsCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
    }
}
