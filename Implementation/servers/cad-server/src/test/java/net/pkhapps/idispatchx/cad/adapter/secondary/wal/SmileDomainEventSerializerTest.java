package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmileDomainEventSerializerTest {

    private SmileDomainEventSerializer serializer;
    private JsonDomainEventSerializer jsonSerializer;

    @BeforeEach
    void setUp() {
        serializer = new SmileDomainEventSerializer(List.of(TestDomainEvent.class));
        jsonSerializer = new JsonDomainEventSerializer(List.of(TestDomainEvent.class));
    }

    @Test
    void roundTrip_preservesAllFields() throws IOException {
        var event = TestDomainEvent.of("smile test");
        var original = new WalEntry(new SequenceNumber(7), event);
        WalEntry restored = serializer.deserialize(serializer.serialize(original));
        assertEquals(7L, restored.sequenceNumber().value());
        var restoredEvent = (TestDomainEvent) restored.event();
        assertEquals(event.eventId(), restoredEvent.eventId());
        assertEquals(event.timestamp(), restoredEvent.timestamp());
        assertEquals("smile test", restoredEvent.payload());
    }

    @Test
    void output_isSmallerThanJson() throws IOException {
        var entry = new WalEntry(new SequenceNumber(1), TestDomainEvent.of("a".repeat(100)));
        byte[] smileBytes = serializer.serialize(entry);
        byte[] jsonBytes = jsonSerializer.serialize(entry);
        assertTrue(smileBytes.length < jsonBytes.length,
                "SMILE (%d bytes) should be smaller than JSON (%d bytes)".formatted(smileBytes.length, jsonBytes.length));
    }

    @Test
    void deserialize_corruptBytes_throwsIOException() {
        assertThrows(IOException.class, () -> serializer.deserialize(new byte[]{0, 1, 2, 3}));
    }

    @Test
    void deserialize_jsonBytes_throwsIOException() throws IOException {
        // JSON bytes are not valid SMILE
        byte[] jsonBytes = jsonSerializer.serialize(new WalEntry(new SequenceNumber(1), TestDomainEvent.of("x")));
        assertThrows(IOException.class, () -> serializer.deserialize(jsonBytes));
    }
}
