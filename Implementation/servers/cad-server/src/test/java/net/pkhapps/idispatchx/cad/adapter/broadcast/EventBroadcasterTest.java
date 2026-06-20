package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.common.auth.UserId;
import tools.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventBroadcasterTest {

    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");
    private static final UserId DISPATCHER = UserId.of("dispatcher-1");
    private static final CallId CALL_ID = new CallId("V1StGXR8_Z5jdHi6B-myT");
    private static final IncidentId INCIDENT_ID = new IncidentId("6byYFiLM_BkBZ5IFKhbRF");

    record BroadcastCall(String type, long sequenceNumber, Instant timestamp, Object payload) {}

    private List<BroadcastCall> broadcastCalls;
    private InMemoryCallRepository callRepository;
    private EventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcastCalls = new ArrayList<>();
        callRepository = new InMemoryCallRepository();

        var recordingService = new DispatcherBroadcastService(new SessionRegistry()) {
            @Override public void sendCallCreated(long seq, Instant ts, Object p) {
                broadcastCalls.add(new BroadcastCall("call.created", seq, ts, p));
            }
            @Override public void sendCallUpdated(long seq, Instant ts, Object p) {
                broadcastCalls.add(new BroadcastCall("call.updated", seq, ts, p));
            }
            @Override public void sendCallEnded(long seq, Instant ts, Object p) {
                broadcastCalls.add(new BroadcastCall("call.ended", seq, ts, p));
            }
            @Override public void sendCallAttachedToIncident(long seq, Instant ts, String callId, String incidentId) {
                broadcastCalls.add(new BroadcastCall("call.attached_to_incident", seq, ts,
                        Map.of("callId", callId, "incidentId", incidentId)));
            }
            @Override public void sendCallDetachedFromIncident(long seq, Instant ts, String callId, String formerIncidentId) {
                broadcastCalls.add(new BroadcastCall("call.detached_from_incident", seq, ts,
                        Map.of("callId", callId, "formerIncidentId", formerIncidentId)));
            }
            @Override public void sendIncidentCreated(long seq, Instant ts, Object p) {
                broadcastCalls.add(new BroadcastCall("incident.created", seq, ts, p));
            }
            @Override public void sendIncidentLogEntryAdded(long seq, Instant ts, Object p) {
                broadcastCalls.add(new BroadcastCall("incident.log_entry_added", seq, ts, p));
            }
        };

        broadcaster = new EventBroadcaster(recordingService, callRepository);
    }

    // -----------------------------------------------------------------------
    // Event type routing
    // -----------------------------------------------------------------------

    @Test
    void broadcast_callCreatedEvent_sendsCallCreatedMessage() {
        addCallToRepo();
        var event = new CallCreatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, null, null, null, null);
        broadcaster.broadcast(event, 1L);
        assertEquals(1, broadcastCalls.size());
        assertEquals("call.created", broadcastCalls.getFirst().type());
        assertEquals(1L, broadcastCalls.getFirst().sequenceNumber());
    }

    @Test
    void broadcast_callUpdatedEvent_sendsCallUpdatedMessage() {
        addCallToRepo();
        var event = new CallUpdatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, null, null, null, null, null, null, null);
        broadcaster.broadcast(event, 2L);
        assertEquals("call.updated", broadcastCalls.getFirst().type());
    }

    @Test
    void broadcast_callEndedEvent_sendsCallEndedMessage() {
        addCallToRepo();
        var event = new CallEndedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, CallOutcome.CALLER_ADVISED, null, null);
        broadcaster.broadcast(event, 3L);
        assertEquals("call.ended", broadcastCalls.getFirst().type());
    }

    @Test
    void broadcast_callAttachedToIncidentEvent_containsCorrectPayload() {
        var event = new CallAttachedToIncidentEvent(EventId.generate(), NOW, CommandId.generate(),
                DISPATCHER, CALL_ID, INCIDENT_ID);
        broadcaster.broadcast(event, 4L);
        var call = broadcastCalls.getFirst();
        assertEquals("call.attached_to_incident", call.type());
        assertEquals(CALL_ID.value(), ((Map<?, ?>) call.payload()).get("callId"));
        assertEquals(INCIDENT_ID.value(), ((Map<?, ?>) call.payload()).get("incidentId"));
    }

    @Test
    void broadcast_callDetachedFromIncidentEvent_containsFormerIncidentId() {
        var event = new CallDetachedFromIncidentEvent(EventId.generate(), NOW, CommandId.generate(),
                DISPATCHER, CALL_ID, INCIDENT_ID);
        broadcaster.broadcast(event, 5L);
        var call = broadcastCalls.getFirst();
        assertEquals("call.detached_from_incident", call.type());
        assertEquals(INCIDENT_ID.value(), ((Map<?, ?>) call.payload()).get("formerIncidentId"));
    }

    @Test
    void broadcast_incidentCreatedEvent_sendsIncidentCreatedMessage() {
        var event = new IncidentCreatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                INCIDENT_ID, null, null, null, null, null);
        broadcaster.broadcast(event, 6L);
        assertEquals("incident.created", broadcastCalls.getFirst().type());
    }

    @Test
    void broadcast_incidentLogEntryAddedEvent_sendsLogEntryMessage() {
        var changeData = JsonNodeFactory.instance.objectNode();
        changeData.put("type", "call_linked");
        var logEntry = new IncidentLogEntry.AutomaticEntry(
                new IncidentLogEntryId("def456789012345678901"), NOW, DISPATCHER, changeData);
        var event = new IncidentLogEntryAddedEvent(EventId.generate(), NOW, CommandId.generate(),
                DISPATCHER, INCIDENT_ID, logEntry);
        broadcaster.broadcast(event, 7L);
        assertEquals("incident.log_entry_added", broadcastCalls.getFirst().type());
    }

    @Test
    void broadcast_unknownEvent_producesNoMessage() {
        broadcaster.broadcast(new UnknownEvent(), 99L);
        assertTrue(broadcastCalls.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Complete snapshot (P1 fix: call.updated / call.ended carry full state)
    // -----------------------------------------------------------------------

    @Test
    void broadcast_callCreatedEvent_payloadContainsCompleteCallObject() {
        addCallToRepo();
        var event = new CallCreatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, null, null, null, null);
        broadcaster.broadcast(event, 1L);
        var payload = (Map<?, ?>) broadcastCalls.getFirst().payload();
        assertEquals(CALL_ID.value(), payload.get("callId"));
        assertEquals("active", payload.get("state"));
        assertNotNull(payload.get("receivingDispatcher")); // from repository
        assertTrue(payload.containsKey("callerName"));    // null is still present
        assertTrue(payload.containsKey("outcome"));
    }

    @Test
    void broadcast_callUpdatedEvent_payloadFromRepositoryNotPartialEvent() {
        // A CallUpdatedEvent only carries changed fields; the broadcaster must
        // fetch the full aggregate from the repository to build the snapshot.
        addCallToRepo();
        var event = new CallUpdatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, null, null, null, null, null, null, null);
        broadcaster.broadcast(event, 2L);
        var payload = (Map<?, ?>) broadcastCalls.getFirst().payload();
        // "active" comes from the repository, not from the event (which has no state field)
        assertEquals("active", payload.get("state"));
        // receivingDispatcher is from the aggregate, not from causedByUser on the event
        assertEquals(DISPATCHER.value(), payload.get("receivingDispatcher"));
        // All 11 fields should be present
        assertEquals(11, payload.size());
    }

    // -----------------------------------------------------------------------
    // Sequence number propagation
    // -----------------------------------------------------------------------

    @Test
    void broadcast_sequenceNumberIsCorrect() {
        addCallToRepo();
        var event = new CallCreatedEvent(EventId.generate(), NOW, CommandId.generate(), DISPATCHER,
                CALL_ID, null, null, null, null);
        broadcaster.broadcast(event, 42L);
        assertEquals(42L, broadcastCalls.getFirst().sequenceNumber());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addCallToRepo() {
        var event = new CallCreatedEvent(EventId.generate(), NOW, null, DISPATCHER,
                CALL_ID, null, null, null, null);
        callRepository.add(Call.fromCreatedEvent(event));
    }

    record UnknownEvent() implements net.pkhapps.idispatchx.cad.domain.event.DomainEvent {
        @Override public EventId eventId() { return EventId.generate(); }
        @Override public Instant timestamp() { return NOW; }
        @Override public CommandId causedBy() { return null; }
        @Override public UserId causedByUser() { return DISPATCHER; }
    }
}
