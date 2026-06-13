package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.adapter.broadcast.EventBroadcaster;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Decorator around a {@link WalPort} delegate that publishes domain events to the
 * {@link EventBroadcaster} after each successful WAL write.
 * <p>
 * Broadcasting runs on the provided {@link Executor} so it is non-blocking with
 * respect to the originating command handler (per the Performance NFR).
 */
public final class EventPublishingWalPort implements WalPort {

    private final WalPort delegate;
    private final EventBroadcaster broadcaster;
    private final Executor broadcastExecutor;

    public EventPublishingWalPort(WalPort delegate, EventBroadcaster broadcaster,
                                  Executor broadcastExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
        this.broadcastExecutor = Objects.requireNonNull(broadcastExecutor, "broadcastExecutor must not be null");
    }

    @Override
    public SequenceNumber write(DomainEvent event) {
        var seq = delegate.write(event);
        var seqValue = seq.value();
        broadcastExecutor.execute(() -> broadcaster.broadcast(event, seqValue));
        return seq;
    }

    @Override
    public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
        var seq = delegate.writeBatch(events);
        var seqValue = seq.value();
        // Batch events all share the same sequence number (the last one in the batch)
        broadcastExecutor.execute(() -> {
            for (var event : events) {
                broadcaster.broadcast(event, seqValue);
            }
        });
        return seq;
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
