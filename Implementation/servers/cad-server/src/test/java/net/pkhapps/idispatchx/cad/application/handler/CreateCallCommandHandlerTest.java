package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.command.CreateCallCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalWriteException;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CreateCallCommandHandlerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-31T10:00:00Z");
    private static final UserId DISPATCHER = UserId.of("dispatcher-1");

    private RecordingWalPort walPort;
    private InMemoryCallRepository callRepository;
    private CreateCallCommandHandler handler;

    @BeforeEach
    void setUp() {
        walPort = new RecordingWalPort();
        callRepository = new InMemoryCallRepository();
        handler = new CreateCallCommandHandler(walPort, new EntityLockManager(),
                callRepository, () -> FIXED_INSTANT);
    }

    @Test
    void handle_returnsCallId() {
        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        var callId = handler.handle(command);
        assertNotNull(callId);
    }

    @Test
    void handle_writesEventToWalBeforeAddingToRepository() {
        var mutationApplied = new AtomicBoolean(false);
        walPort.onWrite = event -> assertFalse(mutationApplied.get(),
                "Repository should not be modified before WAL write");

        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        handler.handle(command);
    }

    @Test
    void handle_addsCallToRepository() {
        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        var callId = handler.handle(command);
        assertTrue(callRepository.existsById(callId));
    }

    @Test
    void handle_doesNotAddToRepositoryIfWalFails() {
        walPort.failOnWrite = true;
        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        assertThrows(WalWriteException.class, () -> handler.handle(command));
        assertEquals(0, callRepository.findAll().count());
    }

    @Test
    void handle_setsReceivingDispatcherFromCommandUserId() {
        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        var callId = handler.handle(command);
        var call = callRepository.findById(callId).orElseThrow();
        assertEquals(DISPATCHER, call.receivingDispatcher());
    }

    @Test
    void handle_writesCallCreatedEvent() {
        var command = new CreateCallCommand(CommandId.generate(), DISPATCHER, null, null, null, null, null);
        handler.handle(command);
        assertEquals(1, walPort.writtenEvents.size());
        assertInstanceOf(CallCreatedEvent.class, walPort.writtenEvents.getFirst());
    }

    // --- Test doubles ---

    static class RecordingWalPort implements WalPort {
        final List<DomainEvent> writtenEvents = new ArrayList<>();
        boolean failOnWrite = false;
        Consumer<DomainEvent> onWrite = e -> {};
        private long seq = 0;

        @Override
        public SequenceNumber write(DomainEvent event) {
            if (failOnWrite) throw new WalWriteException("Simulated WAL failure");
            onWrite.accept(event);
            writtenEvents.add(event);
            return new SequenceNumber(++seq);
        }

        @Override
        public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
            if (failOnWrite) throw new WalWriteException("Simulated WAL failure");
            writtenEvents.addAll(events);
            seq += events.size();
            return new SequenceNumber(seq);
        }

        @Override
        public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {}

        @Override
        public void replay(Consumer<DomainEvent> consumer) { writtenEvents.forEach(consumer); }

        @Override
        public void truncate(SequenceNumber upTo) {}

        @Override
        public SequenceNumber currentSequence() {
            return seq == 0 ? SequenceNumber.start() : new SequenceNumber(seq);
        }
    }
}
