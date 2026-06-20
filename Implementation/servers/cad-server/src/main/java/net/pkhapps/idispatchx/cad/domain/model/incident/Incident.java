package net.pkhapps.idispatchx.cad.domain.model.incident;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.pkhapps.idispatchx.cad.application.handler.PendingMutation;
import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.Entity;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing an incident at the dispatch center.
 * <p>
 * For this UC, only creation in state {@code NEW}, call linking, and log entry creation
 * are implemented. Full state transition logic is out of scope.
 * <p>
 * Entities are not internally synchronized. All concurrent access must be protected
 * by the caller via {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 */
public final class Incident extends Entity<IncidentId> {

    private IncidentState state;
    private final Instant incidentCreated;
    private @Nullable IncidentType incidentType;
    private @Nullable IncidentPriority incidentPriority;
    private @Nullable Location location;
    private @Nullable Description description;
    private final List<IncidentLogEntry> logEntries;

    private Incident(IncidentId id, Instant incidentCreated,
                     @Nullable IncidentType incidentType,
                     @Nullable IncidentPriority incidentPriority,
                     @Nullable Location location,
                     @Nullable Description description) {
        super(id);
        this.state = IncidentState.NEW;
        this.incidentCreated = incidentCreated;
        this.incidentType = incidentType;
        this.incidentPriority = incidentPriority;
        this.location = location;
        this.description = description;
        this.logEntries = new ArrayList<>();
    }

    /**
     * Factory method creating a new incident in state {@code NEW}.
     *
     * @return the creation result containing the event and the new incident instance
     */
    public static IncidentCreationResult create(
            IncidentId id,
            Instant incidentCreated,
            @Nullable CallId sourceCallId,
            @Nullable IncidentType incidentType,
            @Nullable IncidentPriority incidentPriority,
            @Nullable Location location,
            @Nullable Description description,
            UserId causedByUser,
            @Nullable CommandId causedBy) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(incidentCreated, "incidentCreated must not be null");
        Objects.requireNonNull(causedByUser, "causedByUser must not be null");

        var incident = new Incident(id, incidentCreated, incidentType, incidentPriority, location, description);
        var event = new IncidentCreatedEvent(
                EventId.generate(), incidentCreated, causedBy, causedByUser,
                id, sourceCallId, incidentType, incidentPriority, location, description);
        return new IncidentCreationResult(event, incident);
    }

    /**
     * Prepares adding an automatic log entry recording that a call was linked to this incident.
     */
    public PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallLinkedLogEntry(
            IncidentLogEntryId logEntryId, Instant logTimestamp, UserId dispatcher, CallId callId) {
        Objects.requireNonNull(logEntryId, "logEntryId must not be null");
        Objects.requireNonNull(logTimestamp, "logTimestamp must not be null");
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
        if (state == IncidentState.ENDED) {
            throw new IllegalStateException("cannot add log entry to an ENDED incident");
        }

        var changeData = JsonNodeFactory.instance.objectNode();
        changeData.put("type", "call_linked");
        changeData.put("callId", callId.value());

        var entry = new IncidentLogEntry.AutomaticEntry(logEntryId, logTimestamp, dispatcher, changeData);
        var event = new IncidentLogEntryAddedEvent(
                EventId.generate(), logTimestamp, null, dispatcher, id(), entry);
        return new PendingMutation<>(event, () -> logEntries.add(entry));
    }

    /**
     * Prepares adding an automatic log entry recording that a call was detached from this incident.
     */
    public PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallDetachedLogEntry(
            IncidentLogEntryId logEntryId, Instant logTimestamp, UserId dispatcher, CallId callId) {
        Objects.requireNonNull(logEntryId, "logEntryId must not be null");
        Objects.requireNonNull(logTimestamp, "logTimestamp must not be null");
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(callId, "callId must not be null");

        var changeData = JsonNodeFactory.instance.objectNode();
        changeData.put("type", "call_detached");
        changeData.put("callId", callId.value());

        var entry = new IncidentLogEntry.AutomaticEntry(logEntryId, logTimestamp, dispatcher, changeData);
        var event = new IncidentLogEntryAddedEvent(
                EventId.generate(), logTimestamp, null, dispatcher, id(), entry);
        return new PendingMutation<>(event, () -> logEntries.add(entry));
    }

    /**
     * Applies a domain event for WAL replay, reconstructing incident state without validation.
     */
    public void applyEvent(DomainEvent event) {
        switch (event) {
            case IncidentCreatedEvent e -> applyCreate(e);
            case IncidentLogEntryAddedEvent e -> logEntries.add(e.logEntry());
            default -> { /* ignore unrecognized events */ }
        }
    }

    // --- Accessors ---

    public IncidentState state() { return state; }
    public Instant incidentCreated() { return incidentCreated; }
    public @Nullable IncidentType incidentType() { return incidentType; }
    public @Nullable IncidentPriority incidentPriority() { return incidentPriority; }
    public @Nullable Location location() { return location; }
    public @Nullable Description description() { return description; }
    public List<IncidentLogEntry> logEntries() { return Collections.unmodifiableList(logEntries); }

    // --- Private apply methods ---

    private void applyCreate(IncidentCreatedEvent e) {
        this.state = IncidentState.NEW;
        this.incidentType = e.incidentType();
        this.incidentPriority = e.incidentPriority();
        this.location = e.location();
        this.description = e.description();
    }

    /**
     * Reconstructs an Incident from its {@link IncidentCreatedEvent} for WAL replay.
     */
    public static Incident fromCreatedEvent(IncidentCreatedEvent e) {
        return new Incident(e.incidentId(), e.timestamp(),
                e.incidentType(), e.incidentPriority(), e.location(), e.description());
    }

    /**
     * Reconstructs an Incident from a snapshot, restoring all fields exactly as persisted.
     * For snapshot-based startup recovery only — bypasses the normal creation lifecycle.
     */
    public static Incident restore(
            IncidentId id,
            IncidentState state,
            Instant incidentCreated,
            @Nullable IncidentType incidentType,
            @Nullable IncidentPriority incidentPriority,
            @Nullable Location location,
            @Nullable Description description,
            List<IncidentLogEntry> logEntries) {
        var incident = new Incident(id, incidentCreated, incidentType, incidentPriority, location, description);
        incident.state = state;
        incident.logEntries.addAll(logEntries);
        return incident;
    }
}
