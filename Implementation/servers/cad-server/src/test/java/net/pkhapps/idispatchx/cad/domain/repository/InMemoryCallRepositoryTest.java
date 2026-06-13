package net.pkhapps.idispatchx.cad.domain.repository;

import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCallRepositoryTest {

    private InMemoryCallRepository repository;
    private static final UserId DISPATCHER = UserId.of("dispatcher-1");

    @BeforeEach
    void setUp() {
        repository = new InMemoryCallRepository();
    }

    private Call newActiveCall() {
        return Call.create(new CallId(NanoIdGenerator.generate()), DISPATCHER,
                Instant.now(), null, null, null, null).call();
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertTrue(repository.findById(new CallId(NanoIdGenerator.generate())).isEmpty());
    }

    @Test
    void add_and_findById() {
        var call = newActiveCall();
        repository.add(call);
        assertTrue(repository.findById(call.id()).isPresent());
    }

    @Test
    void findActive_returnsOnlyActiveCalls() {
        var active = newActiveCall();
        var ended = newActiveCall();
        repository.add(active);
        repository.add(ended);
        ended.prepareSetOutcome(Instant.now(), CallOutcome.HOAX, new Description("hoax")).applyMutation().run();
        ended.prepareEnd(Instant.now()).applyMutation().run();

        var activeIds = repository.findActive().map(Call::id).toList();
        assertTrue(activeIds.contains(active.id()));
        assertFalse(activeIds.contains(ended.id()));
    }

    @Test
    void findByIncidentId_returnsLinkedCalls() {
        var incidentId = new IncidentId(NanoIdGenerator.generate());
        var linked = newActiveCall();
        var unlinked = newActiveCall();
        repository.add(linked);
        repository.add(unlinked);
        linked.prepareAttachToIncident(incidentId, Instant.now()).applyMutation().run();

        var result = repository.findByIncidentId(incidentId).toList();
        assertEquals(1, result.size());
        assertEquals(linked.id(), result.getFirst().id());
    }

    @Test
    void existsById() {
        var call = newActiveCall();
        assertFalse(repository.existsById(call.id()));
        repository.add(call);
        assertTrue(repository.existsById(call.id()));
    }

    @Test
    void remove_deletesCall() {
        var call = newActiveCall();
        repository.add(call);
        repository.remove(call.id());
        assertFalse(repository.existsById(call.id()));
    }
}
