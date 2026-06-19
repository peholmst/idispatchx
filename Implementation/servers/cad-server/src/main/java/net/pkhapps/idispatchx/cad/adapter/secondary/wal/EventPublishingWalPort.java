package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.adapter.broadcast.EventBroadcaster;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.publisher.DomainEventPublisher;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Decorator around a {@link WalPort} delegate that publishes domain events to the
 * {@link EventBroadcaster} after each successful WAL write.
 * <p>
 * Publishing is triggered by {@link net.pkhapps.idispatchx.cad.application.handler.CommandHandler}
 * <em>after</em> the state mutation is applied, so the repository already reflects the
 * post-command state when the snapshot is captured.
 * <p>
 * Call snapshots are captured eagerly (synchronously on the command-handler thread while the
 * entity lock is still held) so that a concurrent mutation on the same call cannot overwrite
 * the snapshot before it is broadcast.
 * <p>
 * Events are submitted to the {@link Executor} in strict WAL-sequence order even when
 * concurrent command handlers call {@link #publishAfterMutation} out of order: the expected
 * sequence is initialized from the WAL state at construction and a pending map holds any
 * out-of-order entries until the gap is filled, then drains in order.
 */
public final class EventPublishingWalPort implements WalPort, DomainEventPublisher {

    private record PendingBroadcast(DomainEvent event, @Nullable Map<String, Object> callSnapshot) {}

    private final WalPort delegate;
    private final EventBroadcaster broadcaster;
    private final Executor broadcastExecutor;

    private final TreeMap<Long, PendingBroadcast> pendingBroadcasts = new TreeMap<>();
    private long nextExpectedSeq;

    /**
     * Tracks the highest WAL sequence number whose repository mutation is fully applied.
     * Initialized to the WAL's current sequence so that all events replayed during startup
     * are implicitly accounted for once replay completes.
     */
    private final AtomicLong lastAppliedSequence;

    public EventPublishingWalPort(WalPort delegate, EventBroadcaster broadcaster,
                                  Executor broadcastExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
        this.broadcastExecutor = Objects.requireNonNull(broadcastExecutor, "broadcastExecutor must not be null");
        // Initialize from WAL state: 0→1 for empty WAL, K→K+1 for a WAL with K events.
        // This prevents the drain from blocking permanently when a higher-sequence publish
        // callback arrives before a lower-sequence one under concurrent command handling.
        long currentSeq = delegate.currentSequenceNumber();
        this.nextExpectedSeq = currentSeq + 1;
        // After startup WAL replay completes, the repository reflects all events up to
        // currentSeq, so it is safe to snapshot up to this point.
        this.lastAppliedSequence = new AtomicLong(currentSeq);
    }

    /**
     * Returns the highest WAL sequence number whose repository mutation has been fully
     * applied. The snapshot can safely be taken up to and including this sequence number.
     *
     * @return last fully-applied sequence, or 0 if no events have been applied
     */
    public long lastAppliedSequence() {
        return lastAppliedSequence.get();
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
        lastAppliedSequence.updateAndGet(curr -> Math.max(curr, walSeq));
        var snapshot = broadcaster.captureCallSnapshot(event);
        pendingBroadcasts.put(walSeq, new PendingBroadcast(event, snapshot));
        drainInOrder();
    }

    @Override
    public synchronized void publishBatchAfterMutation(List<? extends DomainEvent> events, long lastWalSeq) {
        lastAppliedSequence.updateAndGet(curr -> Math.max(curr, lastWalSeq));
        int size = events.size();
        long firstSeq = lastWalSeq - (size - 1);
        for (int i = 0; i < size; i++) {
            var event = events.get(i);
            var snapshot = broadcaster.captureCallSnapshot(event);
            pendingBroadcasts.put(firstSeq + i, new PendingBroadcast(event, snapshot));
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
            final var pending = entry.getValue();
            broadcastExecutor.execute(() -> broadcaster.broadcast(pending.event(), seq, pending.callSnapshot()));
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

    @Override
    public long currentSequenceNumber() {
        return delegate.currentSequenceNumber();
    }
}
