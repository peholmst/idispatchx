package net.pkhapps.idispatchx.cad.domain.model.incident;

import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;

import java.util.Objects;

/**
 * The result of {@link Incident#create}, containing both the event and the new incident instance.
 *
 * @param event    the {@link IncidentCreatedEvent} to write to the WAL
 * @param incident the newly created {@link Incident} entity
 */
public record IncidentCreationResult(IncidentCreatedEvent event, Incident incident) {

    public IncidentCreationResult {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(incident, "incident must not be null");
    }
}
