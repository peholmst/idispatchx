package net.pkhapps.idispatchx.cad.domain.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandIdTest {

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new CommandId(null));
    }

    @Test
    void constructor_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CommandId(""));
        assertThrows(IllegalArgumentException.class, () -> new CommandId("   "));
    }

    @Test
    void constructor_rejectsTooLongValue() {
        String tooLong = "a".repeat(CommandId.MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> new CommandId(tooLong));
    }

    @Test
    void constructor_rejectsIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new CommandId("id with spaces"));
        assertThrows(IllegalArgumentException.class, () -> new CommandId("id|pipe"));
        assertThrows(IllegalArgumentException.class, () -> new CommandId("id@symbol"));
    }

    @Test
    void constructor_acceptsUUID() {
        var uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertDoesNotThrow(() -> new CommandId(uuid));
    }

    @Test
    void constructor_acceptsNanoId() {
        var nanoId = "V1StGXR8_Z5jdHi6B-myT";
        assertDoesNotThrow(() -> new CommandId(nanoId));
    }

    @Test
    void generate_producesValidCommandId() {
        var id = CommandId.generate();
        assertNotNull(id);
        assertFalse(id.value().isBlank());
    }

    @Test
    void toString_returnsValue() {
        var id = new CommandId("abc123");
        assertEquals("abc123", id.toString());
    }

    @Test
    void equality_basedOnValue() {
        assertEquals(new CommandId("abc123"), new CommandId("abc123"));
        assertNotEquals(new CommandId("abc123"), new CommandId("xyz789"));
    }
}
