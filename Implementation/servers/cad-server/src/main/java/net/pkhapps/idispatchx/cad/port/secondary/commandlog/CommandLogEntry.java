package net.pkhapps.idispatchx.cad.port.secondary.commandlog;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable record of a command received by the CAD Server, suitable for audit logging.
 * <p>
 * Command parameters are intentionally excluded to avoid logging sensitive information.
 *
 * @param commandId   the command identifier supplied by the client
 * @param userId      the identity of the issuing user, or {@link UserId#SYSTEM} for system-initiated commands
 * @param timestamp   the time the command was received
 * @param ipAddress   the IP address of the issuing user, or {@code null} if not available
 * @param commandType the simple class name of the command type
 */
public record CommandLogEntry(
        CommandId commandId,
        UserId userId,
        Instant timestamp,
        @Nullable IPAddress ipAddress,
        String commandType
) {

    /**
     * Maximum allowed length for the command type name.
     */
    static final int MAX_COMMAND_TYPE_LENGTH = 100;

    public CommandLogEntry {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(commandType, "commandType must not be null");
        if (commandType.isBlank()) {
            throw new IllegalArgumentException("commandType must not be blank");
        }
        if (commandType.length() > MAX_COMMAND_TYPE_LENGTH) {
            throw new IllegalArgumentException(
                    "commandType exceeds maximum length of " + MAX_COMMAND_TYPE_LENGTH + " characters");
        }
        for (int i = 0; i < commandType.length(); i++) {
            char c = commandType.charAt(i);
            boolean legal = i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c);
            if (!legal) {
                throw new IllegalArgumentException(
                        "commandType contains character illegal in a Java class name: '" + c + "'");
            }
        }
    }
}
