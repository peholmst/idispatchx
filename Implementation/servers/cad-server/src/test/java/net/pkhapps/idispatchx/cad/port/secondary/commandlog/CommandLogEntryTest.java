package net.pkhapps.idispatchx.cad.port.secondary.commandlog;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CommandLogEntryTest {

    @Test
    void constructor_rejectsBlankCommandType() {
        assertThrows(IllegalArgumentException.class, () -> entry(""));
        assertThrows(IllegalArgumentException.class, () -> entry("   "));
    }

    @Test
    void constructor_rejectsCommandTypeTooLong() {
        String tooLong = "A".repeat(CommandLogEntry.MAX_COMMAND_TYPE_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> entry(tooLong));
    }

    @Test
    void constructor_acceptsCommandTypeAtMaxLength() {
        String maxLength = "A".repeat(CommandLogEntry.MAX_COMMAND_TYPE_LENGTH);
        assertDoesNotThrow(() -> entry(maxLength));
    }

    @Test
    void constructor_rejectsCommandTypeWithIllegalChars() {
        assertThrows(IllegalArgumentException.class, () -> entry("Create-Incident")); // hyphens not legal in class names
        assertThrows(IllegalArgumentException.class, () -> entry("Create Incident")); // spaces illegal
        assertThrows(IllegalArgumentException.class, () -> entry("1CreateIncident")); // must not start with digit
    }

    @Test
    void constructor_acceptsValidCommandType() {
        assertDoesNotThrow(() -> entry("CreateIncidentCommand"));
        assertDoesNotThrow(() -> entry("_InternalCommand"));
        assertDoesNotThrow(() -> entry("Command$Inner"));
    }

    @Test
    void constructor_rejectsNullFields() {
        assertThrows(NullPointerException.class, () ->
                new CommandLogEntry(null, UserId.SYSTEM, Instant.now(), null, "TestCommand"));
        assertThrows(NullPointerException.class, () ->
                new CommandLogEntry(CommandId.generate(), null, Instant.now(), null, "TestCommand"));
        assertThrows(NullPointerException.class, () ->
                new CommandLogEntry(CommandId.generate(), UserId.SYSTEM, null, null, "TestCommand"));
        assertThrows(NullPointerException.class, () ->
                new CommandLogEntry(CommandId.generate(), UserId.SYSTEM, Instant.now(), null, null));
    }

    private CommandLogEntry entry(String commandType) {
        return new CommandLogEntry(CommandId.generate(), UserId.SYSTEM, Instant.now(), null, commandType);
    }
}
