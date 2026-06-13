package net.pkhapps.idispatchx.cad.domain.command;

import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Command to create a new call.
 * <p>
 * All call-detail fields are optional; a call may be created with no information yet.
 */
public record CreateCallCommand(
        CommandId commandId,
        UserId userId,
        @Nullable IPAddress ipAddress,
        @Nullable CallerName callerName,
        @Nullable PhoneNumber callerPhoneNumber,
        @Nullable Location location,
        @Nullable Description description
) implements Command {

    public CreateCallCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
