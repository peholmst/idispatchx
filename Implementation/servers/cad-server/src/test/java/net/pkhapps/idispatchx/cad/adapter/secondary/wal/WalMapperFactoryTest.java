package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import tools.jackson.databind.node.JsonNodeFactory;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
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

    /**
     * Verifies that a legacy WAL entry serialized before the clearXxx flags were added can still
     * be deserialized. Jackson 3.2 must supply null for missing @Nullable Boolean record components
     * rather than throwing a MismatchedInputException.
     */
    @Test
    void callUpdatedEvent_legacyJsonWithoutClearFlags_deserializesWithNullClearFlags() throws Exception {
        var mapper = WalMapperFactory.buildJson(List.of(CallUpdatedEvent.class));

        // Write a current event (all four clear flags are null — treated as "not present in old JSON").
        var event = new CallUpdatedEvent(
                EventId.generate(), NOW, null, DISPATCHER,
                new CallId(NanoIdGenerator.generate()),
                new CallerName("Test Caller"), null, null, null,
                null, null, null,
                null, null, null, null);
        var doc = new WalMapperFactory.WalEntryDocument(1L, event);
        var jsonNode = mapper.valueToTree(doc);

        // Simulate a legacy entry by removing the four new fields from the serialized event node.
        var eventNode = (tools.jackson.databind.node.ObjectNode) jsonNode.get("event");
        eventNode.remove("clearCallerName");
        eventNode.remove("clearCallerPhoneNumber");
        eventNode.remove("clearLocation");
        eventNode.remove("clearDescription");

        // Re-serialize and deserialize — must not throw.
        var bytes = mapper.writeValueAsBytes(jsonNode);
        var deserialized = mapper.readValue(bytes, WalMapperFactory.WalEntryDocument.class);

        var deserializedEvent = (CallUpdatedEvent) deserialized.event;
        assertNull(deserializedEvent.clearCallerName(),
                "Missing clearCallerName in legacy JSON must deserialize as null");
        assertNull(deserializedEvent.clearCallerPhoneNumber(),
                "Missing clearCallerPhoneNumber in legacy JSON must deserialize as null");
        assertNull(deserializedEvent.clearLocation(),
                "Missing clearLocation in legacy JSON must deserialize as null");
        assertNull(deserializedEvent.clearDescription(),
                "Missing clearDescription in legacy JSON must deserialize as null");
        // Sanity-check that the non-clear fields survived the round-trip.
        assertEquals(new CallerName("Test Caller"), deserializedEvent.callerName());
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
