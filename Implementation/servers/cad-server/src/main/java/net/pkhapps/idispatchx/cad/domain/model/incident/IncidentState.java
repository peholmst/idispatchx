package net.pkhapps.idispatchx.cad.domain.model.incident;

/**
 * The lifecycle state of an {@link Incident}.
 */
public enum IncidentState {
    NEW,
    QUEUED,
    ACTIVE,
    MONITORED,
    ENDED
}
