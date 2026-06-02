package net.pkhapps.idispatchx.cad.application.replay;

import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentState;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryIncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WalReplayServiceTest {

    private static final UserId DISPATCHER = UserId.of("dispatcher-1");

    private List<DomainEvent> walEvents;
    private InMemoryCallRepository callRepository;
    private InMemoryIncidentRepository incidentRepository;
    private WalReplayService replayService;

    @BeforeEach
    void setUp() {
        walEvents = new ArrayList<>();
        callRepository = new InMemoryCallRepository();
        incidentRepository = new InMemoryIncidentRepository();
        replayService = new WalReplayService(new InMemoryWalPort(), callRepository, incidentRepository);
    }

    @Test
    void replayAll_reconstructsCallFromCallCreatedEvent() {
        var callId = new CallId(NanoIdGenerator.generate());
        var now = Instant.now();
        walEvents.add(new CallCreatedEvent(EventId.generate(), now, null, DISPATCHER,
                callId, now, null, null, null, null));

        replayService.replayAll();

        assertTrue(callRepository.existsById(callId));
        var call = callRepository.findById(callId).orElseThrow();
        assertEquals(CallState.ACTIVE, call.state());
    }

    @Test
    void replayAll_reconstructsIncidentFromIncidentCreatedEvent() {
        var incidentId = new IncidentId(NanoIdGenerator.generate());
        var now = Instant.now();
        walEvents.add(new IncidentCreatedEvent(EventId.generate(), now, null, DISPATCHER,
                incidentId, null, null, null, null, null));

        replayService.replayAll();

        assertTrue(incidentRepository.existsById(incidentId));
        var incident = incidentRepository.findById(incidentId).orElseThrow();
        assertEquals(IncidentState.NEW, incident.state());
    }

    @Test
    void replayAll_appliesCallEndedEvent() {
        var callId = new CallId(NanoIdGenerator.generate());
        var now = Instant.now();
        walEvents.add(new CallCreatedEvent(EventId.generate(), now, null, DISPATCHER,
                callId, now, null, null, null, null));
        walEvents.add(new CallEndedEvent(EventId.generate(), now, null, DISPATCHER,
                callId, CallOutcome.HOAX, new Description("hoax"), null));

        replayService.replayAll();

        var call = callRepository.findById(callId).orElseThrow();
        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallOutcome.HOAX, call.outcome());
    }

    // --- Test doubles ---

    class InMemoryWalPort implements WalPort {
        @Override
        public SequenceNumber write(DomainEvent event) {
            walEvents.add(event);
            return new SequenceNumber(walEvents.size());
        }

        @Override
        public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
            walEvents.addAll(events);
            return new SequenceNumber(walEvents.size());
        }

        @Override
        public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {
            walEvents.stream().skip(from.value()).forEach(consumer);
        }

        @Override
        public void replay(Consumer<DomainEvent> consumer) {
            walEvents.forEach(consumer);
        }

        @Override
        public void truncate(SequenceNumber upTo) {}

        @Override
        public SequenceNumber currentSequence() {
            return walEvents.isEmpty() ? SequenceNumber.start() : new SequenceNumber(walEvents.size());
        }
    }
}
