package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;

import java.util.List;
import java.util.Objects;

/**
 * Represents a pending batch mutation, separating multi-event creation from state mutation.
 * <p>
 * Used by cross-aggregate command handlers (Tasks 6.4–6.6) that must write multiple events
 * atomically to the WAL before applying any state change.
 *
 * @param events        the domain events to write as a batch to the WAL
 * @param applyMutation a runnable that applies all state mutations after WAL confirms
 * @param <E>           the domain event type
 */
public record PendingBatchMutation<E extends DomainEvent>(List<E> events, Runnable applyMutation) {

    public PendingBatchMutation {
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        Objects.requireNonNull(applyMutation, "applyMutation must not be null");
        events = List.copyOf(events);
    }
}
