package net.pkhapps.idispatchx.cad.port.secondary.publisher;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbound port for publishing domain events to external subscribers after state mutation.
 * <p>
 * Implementations are called by {@link net.pkhapps.idispatchx.cad.application.handler.CommandHandler}
 * strictly after {@code applyMutation()} has run, guaranteeing subscribers see post-command state.
 */
public interface DomainEventPublisher {

    /**
     * Schedules the event for broadcast after the mutation that produced it has been applied.
     *
     * @param event  the domain event that was written to WAL
     * @param walSeq the WAL sequence number assigned to this event
     */
    void publishAfterMutation(DomainEvent event, long walSeq);

    /**
     * Schedules a batch of events for broadcast after their mutation has been applied.
     * Events in the batch occupy contiguous WAL sequences ending at {@code lastWalSeq}.
     *
     * @param events     the domain events in write order
     * @param lastWalSeq the WAL sequence assigned to the last event in the batch
     */
    void publishBatchAfterMutation(List<? extends DomainEvent> events, long lastWalSeq);
}
