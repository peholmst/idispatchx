package net.pkhapps.idispatchx.cad.domain.model.call;

import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CallTest {

    private static final CallId CALL_ID = new CallId(NanoIdGenerator.generate());
    private static final IncidentId INCIDENT_ID = new IncidentId(NanoIdGenerator.generate());
    private static final UserId DISPATCHER = UserId.of("dispatcher-1");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    private Call call;

    @BeforeEach
    void setUp() {
        var result = Call.create(CALL_ID, DISPATCHER, NOW, null, null, null, null);
        call = result.call();
    }

    @Test
    void create_initialStateIsActive() {
        assertEquals(CallState.ACTIVE, call.state());
        assertEquals(CALL_ID, call.id());
        assertEquals(DISPATCHER, call.receivingDispatcher());
        assertNull(call.outcome());
        assertNull(call.incidentId());
    }

    @Test
    void prepareEnd_requiresOutcome() {
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(NOW));
    }

    @Test
    void prepareEnd_succeedsWithOutcomeAlreadySet() {
        call.prepareSetOutcome(NOW, CallOutcome.CALLER_ADVISED, new Description("rationale")).applyMutation().run();
        var pending = call.prepareEnd(NOW);
        assertNotNull(pending.event());
        pending.applyMutation().run();
        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallOutcome.CALLER_ADVISED, call.outcome());
    }

    @Test
    void prepareEnd_requiresRationaleForCallerAdvised() {
        call.prepareSetOutcome(NOW, CallOutcome.CALLER_ADVISED, null).applyMutation().run();
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(NOW));
    }

    @Test
    void prepareEnd_doesNotRequireRationaleForIncidentCreated_whenIncidentAlreadyLinked() {
        // Set INCIDENT_CREATED outcome and incidentId via WAL replay (bypassing validation)
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, null, null, null, null,
                CallOutcome.INCIDENT_CREATED, null, INCIDENT_ID,
                null, null, null, null));
        var endPending = call.prepareEnd(NOW);
        assertNotNull(endPending);
    }

    @Test
    void prepareEnd_rejectsIncidentCreatedOutcomeWithoutIncidentId() {
        // Simulate INCIDENT_CREATED without incidentId via WAL replay
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, null, null, null, null,
                CallOutcome.INCIDENT_CREATED, null, null,
                null, null, null, null));
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(NOW));
    }

    @Test
    void prepareEnd_rejectsAttachedToIncidentOutcomeWithoutIncidentId() {
        // Simulate ATTACHED_TO_INCIDENT without incidentId via WAL replay
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, null, null, null, null,
                CallOutcome.ATTACHED_TO_INCIDENT, null, null,
                null, null, null, null));
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(NOW));
    }

    @Test
    void prepareEnd_allowsAttachedToIncidentOutcomeWhenIncidentIsLinked() {
        call.prepareAttachToIncident(INCIDENT_ID, NOW).applyMutation().run();
        // outcome is now ATTACHED_TO_INCIDENT and incidentId is set — should succeed
        var endPending = call.prepareEnd(NOW);
        assertNotNull(endPending);
    }

    @Test
    void prepareEnd_rejectsEndedCall() {
        call.prepareSetOutcome(NOW, CallOutcome.HOAX, new Description("hoax")).applyMutation().run();
        call.prepareEnd(NOW).applyMutation().run();
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(NOW));
    }

    @Test
    void prepareAttachToIncident_setsOutcomeAndIncidentId() {
        var pending = call.prepareAttachToIncident(INCIDENT_ID, NOW);
        pending.applyMutation().run();
        assertEquals(CallOutcome.ATTACHED_TO_INCIDENT, call.outcome());
        assertEquals(INCIDENT_ID, call.incidentId());
    }

    @Test
    void prepareAttachToIncident_rejectsIfOutcomeIsIncidentCreated() {
        var incidentId2 = new IncidentId(NanoIdGenerator.generate());
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, null, null, null, null,
                CallOutcome.INCIDENT_CREATED, null, incidentId2,
                null, null, null, null));
        assertThrows(IllegalStateException.class,
                () -> call.prepareAttachToIncident(INCIDENT_ID, NOW));
    }

    @Test
    void prepareDetachFromIncident_requiresAttachedToIncidentOutcome() {
        assertThrows(IllegalStateException.class, () -> call.prepareDetachFromIncident(NOW));
    }

    @Test
    void prepareDetachFromIncident_clearsOutcomeAndIncidentId() {
        call.prepareAttachToIncident(INCIDENT_ID, NOW).applyMutation().run();
        call.prepareDetachFromIncident(NOW).applyMutation().run();
        assertNull(call.outcome());
        assertNull(call.incidentId());
    }

    @Test
    void prepareSetOutcome_rejectsIncidentCreatedOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> call.prepareSetOutcome(NOW, CallOutcome.INCIDENT_CREATED, null));
    }

    @Test
    void prepareSetOutcome_rejectsAttachedToIncidentOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> call.prepareSetOutcome(NOW, CallOutcome.ATTACHED_TO_INCIDENT, null));
    }

    @Test
    void prepareSetOutcome_setsOutcomeAndRationale() {
        call.prepareSetOutcome(NOW, CallOutcome.HOAX, new Description("hoax rationale")).applyMutation().run();
        assertEquals(CallOutcome.HOAX, call.outcome());
        assertEquals("hoax rationale", call.outcomeRationale().value());
    }

    @Test
    void applyEvent_walReplay_callCreated() {
        var createdEvent = Call.create(CALL_ID, DISPATCHER, NOW, null, null, null, null).event();
        var replayedCall = Call.fromCreatedEvent(createdEvent);
        assertEquals(CallState.ACTIVE, replayedCall.state());
        assertEquals(CALL_ID, replayedCall.id());
    }

    @Test
    void applyEvent_walReplay_callEnded() {
        var result = Call.create(CALL_ID, DISPATCHER, NOW, null, null, null, null);
        var replayedCall = Call.fromCreatedEvent(result.event());
        replayedCall.prepareSetOutcome(NOW, CallOutcome.HOAX, new Description("hoax")).applyMutation().run();
        var endPending = replayedCall.prepareEnd(NOW);
        replayedCall.applyEvent(endPending.event());
        assertEquals(CallState.ENDED, replayedCall.state());
    }

    @Test
    void prepareUpdate_updatesDetailFields() {
        var pending = call.prepareUpdate(NOW, null, false, null, false, null, false, new Description("updated"), false);
        pending.applyMutation().run();
        assertEquals("updated", call.description().value());
        assertNull(call.outcome());
    }

    @Test
    void prepareUpdate_clearsDetailFieldsWhenFlagSet() {
        call.prepareUpdate(NOW, null, false, null, false, null, false, new Description("initial"), false).applyMutation().run();
        assertEquals("initial", call.description().value());

        call.prepareUpdate(NOW, null, false, null, false, null, false, null, true).applyMutation().run();
        assertNull(call.description());
    }

    @Test
    void prepareUpdate_preservesExistingValueWhenFieldAbsent() {
        call.prepareUpdate(NOW, null, false, null, false, null, false, new Description("keep"), false).applyMutation().run();
        call.prepareUpdate(NOW, null, false, null, false, null, false, null, false).applyMutation().run();
        assertEquals("keep", call.description().value());
    }

    @Test
    void applyEvent_walReplay_clearFlagNullTreatedAsFalse() {
        // Simulate old WAL event without clear flags (null = absent = treat as false)
        var callerName = new net.pkhapps.idispatchx.cad.domain.model.shared.CallerName("John");
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, callerName, null, null, null,
                null, null, null,
                null, null, null, null));
        assertEquals("John", call.callerName().value());

        // Applying an event with null callerName and no clear flag must NOT clear the field
        call.applyEvent(new CallUpdatedEvent(
                EventId.generate(), NOW, null,
                DISPATCHER, CALL_ID, null, null, null, null,
                null, null, null,
                null, null, null, null));
        assertEquals("John", call.callerName().value());
    }
}
