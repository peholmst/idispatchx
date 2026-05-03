package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.Command;
import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogEntry;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentCommandDispatcherTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-01T12:00:00Z");

    private ClockPort clock;
    private RecordingCommandLogPort commandLogPort;
    private IdempotencyTracker tracker;
    private IdempotentCommandDispatcher dispatcher;
    private EntityLockManager lockManager;
    private SimpleWalPort walPort;

    @BeforeEach
    void setUp() {
        clock = () -> FIXED_INSTANT;
        commandLogPort = new RecordingCommandLogPort();
        tracker = new IdempotencyTracker(Duration.ofMinutes(5), clock);
        dispatcher = new IdempotentCommandDispatcher(tracker, commandLogPort, clock);
        lockManager = new EntityLockManager();
        walPort = new SimpleWalPort();
    }

    @Test
    void dispatch_logsCommandOnFirstCall() {
        var handler = new CountingCommandHandler(walPort, lockManager);
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, null);

        dispatcher.dispatch(handler, command);

        assertEquals(1, commandLogPort.entries.size());
        var entry = commandLogPort.entries.getFirst();
        assertEquals(command.commandId(), entry.commandId());
        assertEquals(command.userId(), entry.userId());
        assertEquals(FIXED_INSTANT, entry.timestamp());
        assertNull(entry.ipAddress());
        assertEquals("TestCommand", entry.commandType());
    }

    @Test
    void dispatch_logsCommandOnRetry() {
        var handler = new CountingCommandHandler(walPort, lockManager);
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, null);

        dispatcher.dispatch(handler, command);
        dispatcher.dispatch(handler, command); // retry

        assertEquals(2, commandLogPort.entries.size());
    }

    @Test
    void dispatch_invokesHandlerOnFirstCall() {
        var handler = new CountingCommandHandler(walPort, lockManager);
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, null);

        var result = dispatcher.dispatch(handler, command);

        assertEquals(1, handler.invocationCount.get());
        assertEquals("ok", result);
    }

    @Test
    void dispatch_doesNotInvokeHandlerOnDuplicateCommandId() {
        var handler = new CountingCommandHandler(walPort, lockManager);
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, null);

        var first = dispatcher.dispatch(handler, command);
        var second = dispatcher.dispatch(handler, command);

        assertEquals(1, handler.invocationCount.get());
        assertEquals(first, second);
    }

    @Test
    void dispatch_includesIpAddressInLogEntry() {
        var handler = new CountingCommandHandler(walPort, lockManager);
        var ip = new IPAddress("10.0.0.1");
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, ip);

        dispatcher.dispatch(handler, command);

        assertEquals(ip, commandLogPort.entries.getFirst().ipAddress());
    }

    @Test
    void dispatch_doesNotCacheResultWhenHandlerThrows() {
        var failingWal = new FailingWalPort();
        var handler = new CountingCommandHandler(failingWal, lockManager);
        var command = new TestCommand(CommandId.generate(), UserId.SYSTEM, null);

        assertThrows(RuntimeException.class, () -> dispatcher.dispatch(handler, command));

        // Swap to a working WAL — the command should be retryable
        var workingHandler = new CountingCommandHandler(walPort, lockManager);
        var result = dispatcher.dispatch(workingHandler, command);

        assertEquals("ok", result);
        assertEquals(1, workingHandler.invocationCount.get());
    }

    // --- Test doubles ---

    record TestCommand(CommandId commandId, UserId userId, @Nullable IPAddress ipAddress) implements Command {}

    record TestEvent(EventId eventId, Instant timestamp, @Nullable CommandId causedBy) implements DomainEvent {}

    static class CountingCommandHandler extends CommandHandler<TestCommand, String> {
        final AtomicInteger invocationCount = new AtomicInteger(0);

        CountingCommandHandler(WalPort walPort, EntityLockManager lockManager) {
            super(walPort, lockManager);
        }

        @Override
        protected LockScope determineLockScope(TestCommand command) {
            return LockScope.of("test", command.commandId().value());
        }

        @Override
        protected PendingMutation<TestEvent> prepareExecution(TestCommand command) {
            invocationCount.incrementAndGet();
            var event = new TestEvent(EventId.generate(), Instant.now(), command.commandId());
            return new PendingMutation<>(event, () -> {});
        }

        @Override
        protected String buildResult(TestCommand command, DomainEvent event) {
            return "ok";
        }
    }

    static class RecordingCommandLogPort implements CommandLogPort {
        final List<CommandLogEntry> entries = new ArrayList<>();

        @Override
        public void log(CommandLogEntry entry) {
            entries.add(entry);
        }
    }

    static class SimpleWalPort implements WalPort {
        private long seq = 0;

        @Override
        public SequenceNumber write(DomainEvent event) {
            return new SequenceNumber(++seq);
        }

        @Override
        public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
            seq += events.size();
            return new SequenceNumber(seq);
        }

        @Override
        public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {}

        @Override
        public void replay(Consumer<DomainEvent> consumer) {}

        @Override
        public void truncate(SequenceNumber upTo) {}

        @Override
        public SequenceNumber currentSequence() {
            return seq == 0 ? SequenceNumber.start() : new SequenceNumber(seq);
        }
    }

    static class FailingWalPort extends SimpleWalPort {
        @Override
        public SequenceNumber write(DomainEvent event) {
            throw new RuntimeException("WAL unavailable");
        }
    }
}
