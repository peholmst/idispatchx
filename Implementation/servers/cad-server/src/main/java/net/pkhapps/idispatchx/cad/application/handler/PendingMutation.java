package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;

import java.util.Objects;

/**
 * Represents a pending mutation that separates event creation from state mutation.
 * <p>
 * This is the core mechanism for the WAL-before-state pattern: the event is created
 * and can be written to WAL before the actual state mutation is applied.
 *
 * @param event         the domain event to be written to WAL
 * @param applyMutation a runnable that applies the state mutation when executed
 * @param <E>           the type of domain event
 */
public record PendingMutation<E extends DomainEvent>(E event, Runnable applyMutation) {

    public PendingMutation {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(applyMutation, "applyMutation must not be null");
    }
}
