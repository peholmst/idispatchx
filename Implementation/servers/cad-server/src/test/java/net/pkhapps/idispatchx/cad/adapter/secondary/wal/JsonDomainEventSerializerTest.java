package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonDomainEventSerializerTest {

    private JsonDomainEventSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JsonDomainEventSerializer(List.of(TestDomainEvent.class));
    }

    @Test
    void serialize_producesNonEmptyBytes() {
        var entry = new WalEntry(new SequenceNumber(1), TestDomainEvent.of("hello"));
        byte[] bytes = serializer.serialize(entry);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void roundTrip_preservesSequenceNumber() {
        var original = new WalEntry(new SequenceNumber(42), TestDomainEvent.of("test"));
        WalEntry restored = serializer.deserialize(serializer.serialize(original));
        assertEquals(42L, restored.sequenceNumber().value());
    }

    @Test
    void roundTrip_preservesEventFields() {
        var event = TestDomainEvent.of("hello world");
        var original = new WalEntry(new SequenceNumber(1), event);
        WalEntry restored = serializer.deserialize(serializer.serialize(original));
        var restoredEvent = (TestDomainEvent) restored.event();
        assertEquals(event.eventId(), restoredEvent.eventId());
        assertEquals(event.timestamp(), restoredEvent.timestamp());
        assertNull(restoredEvent.causedBy());
        assertEquals("hello world", restoredEvent.payload());
    }

    @Test
    void roundTrip_preservesNullCausedBy() {
        var event = TestDomainEvent.of("no cause");
        var original = new WalEntry(new SequenceNumber(1), event);
        WalEntry restored = serializer.deserialize(serializer.serialize(original));
        assertNull(((TestDomainEvent) restored.event()).causedBy());
    }

    @Test
    void roundTrip_preservesNonNullCausedBy() {
        var causedBy = net.pkhapps.idispatchx.cad.domain.command.CommandId.generate();
        var event = TestDomainEvent.of("caused", causedBy);
        var original = new WalEntry(new SequenceNumber(5), event);
        WalEntry restored = serializer.deserialize(serializer.serialize(original));
        assertEquals(causedBy, ((TestDomainEvent) restored.event()).causedBy());
    }

    @Test
    void serialize_producesJsonText() {
        var entry = new WalEntry(new SequenceNumber(1), TestDomainEvent.of("test"));
        byte[] bytes = serializer.serialize(entry);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"seq\""), "Expected seq field in JSON: " + json);
        assertTrue(json.contains("\"@type\""), "Expected @type field in JSON: " + json);
    }

    @Test
    void deserialize_unknownType_throwsJacksonException() {
        byte[] corrupt = "{\"seq\":1,\"event\":{\"@type\":\"com.example.NonExistentEvent\"}}".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(InvalidTypeIdException.class, () -> serializer.deserialize(corrupt));
    }

    @Test
    void deserialize_corruptBytes_throwsJacksonException() {
        byte[] corrupt = "NOT_JSON_AT_ALL".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(JacksonException.class, () -> serializer.deserialize(corrupt));
    }

    @Test
    void objectMapper_returnsSameInstance() {
        assertSame(serializer.objectMapper(), serializer.objectMapper());
    }
}
