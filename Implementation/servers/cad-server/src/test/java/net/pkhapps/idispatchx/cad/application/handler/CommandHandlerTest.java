package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.Command;
import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalWriteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CommandHandlerTest {

    private EntityLockManager lockManager;
    private RecordingWalPort walPort;
    private AtomicBoolean mutationApplied;
    private AtomicInteger stateValue;

    @BeforeEach
    void setUp() {
        lockManager = new EntityLockManager();
        walPort = new RecordingWalPort();
        mutationApplied = new AtomicBoolean(false);
        stateValue = new AtomicInteger(0);
    }

    @Test
    void handle_writesEventToWalBeforeApplyingMutation() {
        var handler = new TestCommandHandler(walPort, lockManager, mutationApplied);
        var command = new TestCommand(CommandId.generate(), "test-123");

        var result = handler.handle(command);

        assertEquals("test-123", result);
        assertTrue(mutationApplied.get());
        assertEquals(1, walPort.writtenEvents.size());
        assertInstanceOf(TestEvent.class, walPort.writtenEvents.getFirst());
    }

    @Test
    void handle_doesNotApplyMutationIfWalWriteFails() {
        walPort.failOnWrite = true;
        var handler = new TestCommandHandler(walPort, lockManager, mutationApplied);
        var command = new TestCommand(CommandId.generate(), "test-123");

        assertThrows(WalWriteException.class, () -> handler.handle(command));

        assertFalse(mutationApplied.get(), "Mutation should not be applied when WAL write fails");
        assertEquals(0, walPort.writtenEvents.size());
    }

    @Test
    void handle_acquiresLockBeforeExecution() throws Exception {
        var executionOrder = java.util.Collections.synchronizedList(new ArrayList<String>());
        var latch = new CountDownLatch(2);
        var counter = new AtomicInteger(0);

        var trackingWalPort = new OrderTrackingWalPort(executionOrder, counter);
        var handler = new OrderTrackingCommandHandler(trackingWalPort, lockManager, executionOrder, counter);

        // First command holds lock while second waits
        var thread1 = new Thread(() -> {
            try {
                handler.handle(new TestCommand(CommandId.generate(), "same-id"));
            } finally {
                latch.countDown();
            }
        });

        var thread2 = new Thread(() -> {
            try {
                Thread.sleep(10); // Ensure thread1 gets lock first
                handler.handle(new TestCommand(CommandId.generate(), "same-id"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Commands should execute sequentially, not interleaved
        assertEquals(List.of(
                "prepare-1", "wal-write-1", "mutation-1",
                "prepare-2", "wal-write-2", "mutation-2"
        ), executionOrder);
    }

    @Test
    void handle_stateNotVisibleBeforeWalSync() throws Exception {
        var slowWalPort = new SlowWalPort(100); // 100ms delay
        var handler = new StateModifyingCommandHandler(slowWalPort, lockManager, stateValue);
        var stateObservedDuringWalWrite = new AtomicInteger(-1);

        var handlerThread = new Thread(() -> {
            handler.handle(new TestCommand(CommandId.generate(), "test"));
        });

        handlerThread.start();

        // Wait a bit for handler to start WAL write
        Thread.sleep(30);

        // Read state while WAL is still syncing
        stateObservedDuringWalWrite.set(stateValue.get());

        handlerThread.join(5000);

        // State should have been 0 during WAL write (mutation not yet applied)
        assertEquals(0, stateObservedDuringWalWrite.get());
        // State should be 42 after handler completes
        assertEquals(42, stateValue.get());
    }

    // --- Test doubles ---

    record TestCommand(CommandId commandId, String targetId) implements Command {
    }

    record TestEvent(EventId eventId, Instant timestamp, CommandId causedBy,
                     String targetId) implements DomainEvent {
    }

    static class RecordingWalPort implements WalPort {
        final List<DomainEvent> writtenEvents = new ArrayList<>();
        boolean failOnWrite = false;

        @Override
        public void write(DomainEvent event) {
            if (failOnWrite) {
                throw new WalWriteException("Simulated WAL failure");
            }
            writtenEvents.add(event);
        }

        @Override
        public void writeBatch(List<? extends DomainEvent> events) {
            if (failOnWrite) {
                throw new WalWriteException("Simulated WAL failure");
            }
            writtenEvents.addAll(events);
        }

        @Override
        public void replay(Consumer<DomainEvent> consumer) {
            writtenEvents.forEach(consumer);
        }

        @Override
        public void truncate(EventId upToEventId) {
            // Not needed for tests
        }
    }

    static class SlowWalPort extends RecordingWalPort {
        private final long delayMs;

        SlowWalPort(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void write(DomainEvent event) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.write(event);
        }
    }

    static class TestCommandHandler extends CommandHandler<TestCommand, String> {
        private final AtomicBoolean mutationApplied;

        TestCommandHandler(WalPort walPort, EntityLockManager lockManager, AtomicBoolean mutationApplied) {
            super(walPort, lockManager);
            this.mutationApplied = mutationApplied;
        }

        @Override
        protected LockScope determineLockScope(TestCommand command) {
            return LockScope.of("test", command.targetId());
        }

        @Override
        protected PendingMutation<TestEvent> prepareExecution(TestCommand command) {
            var event = new TestEvent(
                    EventId.generate(),
                    Instant.now(),
                    command.commandId(),
                    command.targetId()
            );
            return new PendingMutation<>(event, () -> mutationApplied.set(true));
        }

        @Override
        protected String buildResult(TestCommand command, DomainEvent event) {
            return command.targetId();
        }
    }

    static class OrderTrackingCommandHandler extends CommandHandler<TestCommand, String> {
        private final List<String> executionOrder;
        private final AtomicInteger counter;

        OrderTrackingCommandHandler(WalPort walPort, EntityLockManager lockManager, List<String> executionOrder, AtomicInteger counter) {
            super(walPort, lockManager);
            this.executionOrder = executionOrder;
            this.counter = counter;
        }

        @Override
        protected LockScope determineLockScope(TestCommand command) {
            return LockScope.of("test", command.targetId());
        }

        @Override
        protected PendingMutation<TestEvent> prepareExecution(TestCommand command) {
            int n = counter.incrementAndGet();
            synchronized (executionOrder) {
                executionOrder.add("prepare-" + n);
            }

            var event = new TestEvent(EventId.generate(), Instant.now(), command.commandId(), command.targetId());

            return new PendingMutation<>(event, () -> {
                try {
                    Thread.sleep(20); // Simulate some work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (executionOrder) {
                    executionOrder.add("mutation-" + n);
                }
            });
        }

        @Override
        protected String buildResult(TestCommand command, DomainEvent event) {
            return command.targetId();
        }
    }

    // Custom WalPort that tracks when write happens
    static class OrderTrackingWalPort extends RecordingWalPort {
        private final List<String> executionOrder;
        private final AtomicInteger counter;

        OrderTrackingWalPort(List<String> executionOrder, AtomicInteger counter) {
            this.executionOrder = executionOrder;
            this.counter = counter;
        }

        @Override
        public void write(DomainEvent event) {
            executionOrder.add("wal-write-" + counter.get());
            super.write(event);
        }
    }

    static class StateModifyingCommandHandler extends CommandHandler<TestCommand, String> {
        private final AtomicInteger stateValue;

        StateModifyingCommandHandler(WalPort walPort, EntityLockManager lockManager, AtomicInteger stateValue) {
            super(walPort, lockManager);
            this.stateValue = stateValue;
        }

        @Override
        protected LockScope determineLockScope(TestCommand command) {
            return LockScope.of("test", command.targetId());
        }

        @Override
        protected PendingMutation<TestEvent> prepareExecution(TestCommand command) {
            var event = new TestEvent(EventId.generate(), Instant.now(), command.commandId(), command.targetId());
            return new PendingMutation<>(event, () -> stateValue.set(42));
        }

        @Override
        protected String buildResult(TestCommand command, DomainEvent event) {
            return command.targetId();
        }
    }
}
