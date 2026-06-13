package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalMapperFactoryTest {

    private static final UserId DISPATCHER = UserId.of("dispatcher-1");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void incidentLogEntryAddedEvent_automaticEntry_roundtrip(WalFormat format) throws Exception {
        var eventTypes = List.of(IncidentLogEntryAddedEvent.class);
        var mapper = format == WalFormat.TEXT
                ? WalMapperFactory.buildJson(eventTypes)
                : WalMapperFactory.buildSmile(eventTypes);

        var logEntry = new IncidentLogEntry.AutomaticEntry(
                new IncidentLogEntryId(NanoIdGenerator.generate()),
                NOW, DISPATCHER,
                JsonNodeFactory.instance.objectNode().put("callId", "test-call"));

        var event = new IncidentLogEntryAddedEvent(
                EventId.generate(), NOW, null, DISPATCHER,
                new IncidentId(NanoIdGenerator.generate()), logEntry);

        var doc = new WalMapperFactory.WalEntryDocument(1L, event);
        var bytes = mapper.writeValueAsBytes(doc);
        var deserialized = mapper.readValue(bytes, WalMapperFactory.WalEntryDocument.class);

        assertInstanceOf(IncidentLogEntryAddedEvent.class, deserialized.event);
        var deserializedEvent = (IncidentLogEntryAddedEvent) deserialized.event;
        assertInstanceOf(IncidentLogEntry.AutomaticEntry.class, deserializedEvent.logEntry());
        var deserializedEntry = (IncidentLogEntry.AutomaticEntry) deserializedEvent.logEntry();
        assertEquals(logEntry.id(), deserializedEntry.id());
        assertEquals("test-call", deserializedEntry.changeData().get("callId").asText());
    }

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void incidentLogEntryAddedEvent_manualEntry_roundtrip(WalFormat format) throws Exception {
        var eventTypes = List.of(IncidentLogEntryAddedEvent.class);
        var mapper = format == WalFormat.TEXT
                ? WalMapperFactory.buildJson(eventTypes)
                : WalMapperFactory.buildSmile(eventTypes);

        var logEntry = new IncidentLogEntry.ManualEntry(
                new IncidentLogEntryId(NanoIdGenerator.generate()),
                NOW, DISPATCHER,
                new Description("Manual log entry"));

        var event = new IncidentLogEntryAddedEvent(
                EventId.generate(), NOW, null, DISPATCHER,
                new IncidentId(NanoIdGenerator.generate()), logEntry);

        var doc = new WalMapperFactory.WalEntryDocument(1L, event);
        var bytes = mapper.writeValueAsBytes(doc);
        var deserialized = mapper.readValue(bytes, WalMapperFactory.WalEntryDocument.class);

        assertInstanceOf(IncidentLogEntryAddedEvent.class, deserialized.event);
        var deserializedEvent = (IncidentLogEntryAddedEvent) deserialized.event;
        assertInstanceOf(IncidentLogEntry.ManualEntry.class, deserializedEvent.logEntry());
        var deserializedEntry = (IncidentLogEntry.ManualEntry) deserializedEvent.logEntry();
        assertEquals(logEntry.description().value(), deserializedEntry.description().value());
    }
}
