package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.adapter.broadcast.EventBroadcaster;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.publisher.DomainEventPublisher;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Decorator around a {@link WalPort} delegate that publishes domain events to the
 * {@link EventBroadcaster} after each successful WAL write.
 * <p>
 * Publishing is triggered by {@link net.pkhapps.idispatchx.cad.application.handler.CommandHandler}
 * <em>after</em> the state mutation is applied, so the repository already reflects the
 * post-command state when the broadcaster reads it.
 * <p>
 * Events are submitted to the {@link Executor} in strict WAL-sequence order even when
 * concurrent command handlers call {@link #publishAfterMutation} out of order: a pending
 * map holds any out-of-order entries until the gap is filled, then drains in order.
 */
public final class EventPublishingWalPort implements WalPort, DomainEventPublisher {

    private final WalPort delegate;
    private final EventBroadcaster broadcaster;
    private final Executor broadcastExecutor;

    private final TreeMap<Long, DomainEvent> pendingBroadcasts = new TreeMap<>();
    private long nextExpectedSeq = -1; // -1 = not yet initialized; set on first publish call

    public EventPublishingWalPort(WalPort delegate, EventBroadcaster broadcaster,
                                  Executor broadcastExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
        this.broadcastExecutor = Objects.requireNonNull(broadcastExecutor, "broadcastExecutor must not be null");
    }

    @Override
    public SequenceNumber write(DomainEvent event) {
        return delegate.write(event);
    }

    @Override
    public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
        return delegate.writeBatch(events);
    }

    @Override
    public synchronized void publishAfterMutation(DomainEvent event, long walSeq) {
        if (nextExpectedSeq < 0) {
            nextExpectedSeq = walSeq;
        }
        pendingBroadcasts.put(walSeq, event);
        drainInOrder();
    }

    @Override
    public synchronized void publishBatchAfterMutation(List<? extends DomainEvent> events, long lastWalSeq) {
        int size = events.size();
        long firstSeq = lastWalSeq - (size - 1);
        if (nextExpectedSeq < 0) {
            nextExpectedSeq = firstSeq;
        }
        for (int i = 0; i < size; i++) {
            pendingBroadcasts.put(firstSeq + i, events.get(i));
        }
        drainInOrder();
    }

    private void drainInOrder() {
        while (!pendingBroadcasts.isEmpty()) {
            var entry = pendingBroadcasts.firstEntry();
            if (entry.getKey() != nextExpectedSeq) {
                break;
            }
            pendingBroadcasts.pollFirstEntry();
            nextExpectedSeq++;
            final long seq = entry.getKey();
            final DomainEvent event = entry.getValue();
            broadcastExecutor.execute(() -> broadcaster.broadcast(event, seq));
        }
    }

    @Override
    public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {
        delegate.replayFrom(from, consumer);
    }

    @Override
    public void replay(Consumer<DomainEvent> consumer) {
        delegate.replay(consumer);
    }

    @Override
    public void truncate(SequenceNumber upTo) {
        delegate.truncate(upTo);
    }

    @Override
    public SequenceNumber currentSequence() {
        return delegate.currentSequence();
    }
}
