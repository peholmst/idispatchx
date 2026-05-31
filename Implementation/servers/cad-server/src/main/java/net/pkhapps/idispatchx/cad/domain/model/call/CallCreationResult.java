package net.pkhapps.idispatchx.cad.domain.model.call;

import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;

import java.util.Objects;

/**
 * The result of {@link Call#create}, containing both the event and the new call instance.
 *
 * @param event the {@link CallCreatedEvent} to write to the WAL
 * @param call  the newly created {@link Call} entity
 */
public record CallCreationResult(CallCreatedEvent event, Call call) {

    public CallCreationResult {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(call, "call must not be null");
    }
}
