package net.pkhapps.idispatchx.cad.domain.model.call;

import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
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

    private Call call;

    @BeforeEach
    void setUp() {
        var result = Call.create(CALL_ID, DISPATCHER, Instant.now(), null, null, null, null);
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
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(null, null));
    }

    @Test
    void prepareEnd_succeedsWithOutcome() {
        var pending = call.prepareEnd(CallOutcome.CALLER_ADVISED, new Description("rationale"));
        assertNotNull(pending.event());
        pending.applyMutation().run();
        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallOutcome.CALLER_ADVISED, call.outcome());
    }

    @Test
    void prepareEnd_requiresRationaleForCallerAdvised() {
        assertThrows(IllegalStateException.class,
                () -> call.prepareEnd(CallOutcome.CALLER_ADVISED, null));
    }

    @Test
    void prepareEnd_doesNotRequireRationaleForIncidentCreated() {
        // First attach to incident
        var attachPending = call.prepareAttachToIncident(INCIDENT_ID, Instant.now());
        attachPending.applyMutation().run();
        // Can now end with outcome ATTACHED_TO_INCIDENT — no rationale needed
        // But wait, AttachToIncident sets outcome to ATTACHED_TO_INCIDENT, not INCIDENT_CREATED
        // Let's test with a non-rationale-requiring outcome
        var call2 = Call.create(new CallId(NanoIdGenerator.generate()), DISPATCHER, Instant.now(),
                null, null, null, null).call();
        var endPending = call2.prepareEnd(CallOutcome.INCIDENT_CREATED, null);
        assertNotNull(endPending);
    }

    @Test
    void prepareEnd_rejectsEndedCall() {
        call.prepareEnd(CallOutcome.HOAX, new Description("hoax")).applyMutation().run();
        assertThrows(IllegalStateException.class, () -> call.prepareEnd(CallOutcome.HOAX, new Description("r")));
    }

    @Test
    void prepareAttachToIncident_setsOutcomeAndIncidentId() {
        var pending = call.prepareAttachToIncident(INCIDENT_ID, Instant.now());
        pending.applyMutation().run();
        assertEquals(CallOutcome.ATTACHED_TO_INCIDENT, call.outcome());
        assertEquals(INCIDENT_ID, call.incidentId());
    }

    @Test
    void prepareAttachToIncident_rejectsIfOutcomeIsIncidentCreated() {
        // Update outcome to INCIDENT_CREATED via applyEvent (simulating WAL replay)
        var incidentId2 = new IncidentId(NanoIdGenerator.generate());
        call.applyEvent(new CallUpdatedEvent(
                net.pkhapps.idispatchx.cad.domain.event.EventId.generate(), Instant.now(), null,
                DISPATCHER, CALL_ID, null, null, null, null,
                CallOutcome.INCIDENT_CREATED, null, incidentId2));
        assertThrows(IllegalStateException.class,
                () -> call.prepareAttachToIncident(INCIDENT_ID, Instant.now()));
    }

    @Test
    void prepareDetachFromIncident_requiresAttachedToIncidentOutcome() {
        assertThrows(IllegalStateException.class, () -> call.prepareDetachFromIncident());
    }

    @Test
    void prepareDetachFromIncident_clearsOutcomeAndIncidentId() {
        call.prepareAttachToIncident(INCIDENT_ID, Instant.now()).applyMutation().run();
        call.prepareDetachFromIncident().applyMutation().run();
        assertNull(call.outcome());
        assertNull(call.incidentId());
    }

    @Test
    void applyEvent_walReplay_callCreated() {
        var createdEvent = Call.create(CALL_ID, DISPATCHER, Instant.now(), null, null, null, null).event();
        var replayedCall = Call.fromCreatedEvent(createdEvent);
        assertEquals(CallState.ACTIVE, replayedCall.state());
        assertEquals(CALL_ID, replayedCall.id());
    }

    @Test
    void applyEvent_walReplay_callEnded() {
        var result = Call.create(CALL_ID, DISPATCHER, Instant.now(), null, null, null, null);
        var replayedCall = Call.fromCreatedEvent(result.event());
        var endPending = replayedCall.prepareEnd(CallOutcome.HOAX, new Description("hoax"));
        replayedCall.applyEvent(endPending.event());
        assertEquals(CallState.ENDED, replayedCall.state());
    }

    @Test
    void prepareUpdate_rejectsIncidentCreatedOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> call.prepareUpdate(null, null, null, null, CallOutcome.INCIDENT_CREATED, null));
    }

    @Test
    void prepareUpdate_rejectsAttachedToIncidentOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> call.prepareUpdate(null, null, null, null, CallOutcome.ATTACHED_TO_INCIDENT, null));
    }
}
