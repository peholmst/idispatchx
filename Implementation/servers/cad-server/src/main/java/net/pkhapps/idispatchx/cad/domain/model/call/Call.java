package net.pkhapps.idispatchx.cad.domain.model.call;

import net.pkhapps.idispatchx.cad.application.handler.PendingMutation;
import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.Entity;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.cad.domain.statemachine.CallStateMachine;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing an emergency call received by the dispatch center.
 * <p>
 * Entities are not internally synchronized. All concurrent access must be protected
 * by the caller via {@link net.pkhapps.idispatchx.cad.application.handler.EntityLockManager}.
 */
public final class Call extends Entity<CallId> {

    private CallState state;
    private final UserId receivingDispatcher;
    private final Instant callStarted;
    private @Nullable Instant callEnded;
    private @Nullable CallerName callerName;
    private @Nullable PhoneNumber callerPhoneNumber;
    private @Nullable Location location;
    private @Nullable Description description;
    private @Nullable CallOutcome outcome;
    private @Nullable Description outcomeRationale;
    private @Nullable IncidentId incidentId;

    private Call(CallId id, UserId receivingDispatcher, Instant callStarted,
                 @Nullable CallerName callerName, @Nullable PhoneNumber callerPhoneNumber,
                 @Nullable Location location, @Nullable Description description) {
        super(id);
        this.state = CallState.ACTIVE;
        this.receivingDispatcher = receivingDispatcher;
        this.callStarted = callStarted;
        this.callerName = callerName;
        this.callerPhoneNumber = callerPhoneNumber;
        this.location = location;
        this.description = description;
    }

    /**
     * Factory method for creating a new call.
     *
     * @return the creation result containing the event and the new call instance
     */
    public static CallCreationResult create(
            CallId id,
            UserId receivingDispatcher,
            Instant callStarted,
            @Nullable CallerName callerName,
            @Nullable PhoneNumber callerPhoneNumber,
            @Nullable Location location,
            @Nullable Description description) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(receivingDispatcher, "receivingDispatcher must not be null");
        Objects.requireNonNull(callStarted, "callStarted must not be null");

        var call = new Call(id, receivingDispatcher, callStarted,
                callerName, callerPhoneNumber, location, description);
        var event = new CallCreatedEvent(
                EventId.generate(), callStarted, null, receivingDispatcher,
                id, callerName, callerPhoneNumber, location, description);
        return new CallCreationResult(event, call);
    }

    /**
     * Prepares an update of call detail fields (caller info, location, description).
     * <p>
     * Only non-null fields in the arguments are applied; existing values are preserved for nulls.
     * Use {@link #prepareSetOutcome(Instant, CallOutcome, Description)} to change outcome or rationale.
     *
     * @param now authoritative timestamp supplied by the clock port
     */
    public PendingMutation<CallUpdatedEvent> prepareUpdate(
            Instant now,
            @Nullable CallerName callerName,
            @Nullable PhoneNumber callerPhoneNumber,
            @Nullable Location location,
            @Nullable Description description) {
        var newCallerName = callerName != null ? callerName : this.callerName;
        var newCallerPhoneNumber = callerPhoneNumber != null ? callerPhoneNumber : this.callerPhoneNumber;
        var newLocation = location != null ? location : this.location;
        var newDescription = description != null ? description : this.description;

        var event = new CallUpdatedEvent(
                EventId.generate(), now, null, receivingDispatcher,
                id(), newCallerName, newCallerPhoneNumber, newLocation, newDescription,
                null, null, null);
        return new PendingMutation<>(event, () -> applyUpdate(event));
    }

    /**
     * Prepares setting the call outcome and rationale.
     * <p>
     * Outcomes {@code INCIDENT_CREATED} and {@code ATTACHED_TO_INCIDENT} may not be set here;
     * use the specific attach/create commands instead.
     *
     * @param now authoritative timestamp supplied by the clock port
     */
    public PendingMutation<CallUpdatedEvent> prepareSetOutcome(
            Instant now,
            CallOutcome outcome,
            @Nullable Description outcomeRationale) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == CallOutcome.INCIDENT_CREATED || outcome == CallOutcome.ATTACHED_TO_INCIDENT) {
            throw new IllegalArgumentException(
                    "outcome " + outcome + " must be set via specific commands, not SetCallOutcome");
        }

        var event = new CallUpdatedEvent(
                EventId.generate(), now, null, receivingDispatcher,
                id(), null, null, null, null,
                outcome, outcomeRationale, null);
        return new PendingMutation<>(event, () -> applyUpdate(event));
    }

    /**
     * Prepares ending the call.
     * <p>
     * The {@code outcome} must already be set on the call before calling this method.
     * If the outcome requires a rationale, it must also be set.
     *
     * @param now authoritative timestamp supplied by the clock port
     */
    public PendingMutation<CallEndedEvent> prepareEnd(Instant now) {
        CallStateMachine.validateTransition(state, CallState.ENDED);

        if (outcome == null) {
            throw new IllegalStateException("outcome must be set before ending a call");
        }
        if ((outcome == CallOutcome.INCIDENT_CREATED || outcome == CallOutcome.ATTACHED_TO_INCIDENT)
                && incidentId == null) {
            throw new IllegalStateException(
                    "outcome " + outcome + " requires incidentId to already be set; "
                    + "use attach/create commands to link the call before ending it");
        }
        if (outcome.requiresRationale() && outcomeRationale == null) {
            throw new IllegalStateException("outcome " + outcome + " requires an outcomeRationale");
        }

        var event = new CallEndedEvent(
                EventId.generate(), now, null, receivingDispatcher,
                id(), outcome, outcomeRationale, incidentId);
        return new PendingMutation<>(event, () -> applyEnd(event));
    }

    /**
     * Prepares attaching this call to an existing incident.
     *
     * @param attachedAt authoritative timestamp supplied by the clock port
     */
    public PendingMutation<CallAttachedToIncidentEvent> prepareAttachToIncident(
            IncidentId incidentId, Instant attachedAt) {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(attachedAt, "attachedAt must not be null");
        if (state != CallState.ACTIVE) {
            throw new IllegalStateException("call must be ACTIVE to attach to an incident");
        }
        if (outcome == CallOutcome.INCIDENT_CREATED) {
            throw new IllegalStateException("call with outcome INCIDENT_CREATED cannot be attached to another incident");
        }

        var event = new CallAttachedToIncidentEvent(
                EventId.generate(), attachedAt, null, receivingDispatcher, id(), incidentId);
        return new PendingMutation<>(event, () -> applyAttach(event));
    }

    /**
     * Prepares detaching this call from its incident.
     *
     * @param now authoritative timestamp supplied by the clock port
     */
    public PendingMutation<CallDetachedFromIncidentEvent> prepareDetachFromIncident(Instant now) {
        if (state != CallState.ACTIVE) {
            throw new IllegalStateException("call must be ACTIVE to detach from an incident");
        }
        if (outcome != CallOutcome.ATTACHED_TO_INCIDENT) {
            throw new IllegalStateException("call must have outcome ATTACHED_TO_INCIDENT to be detached");
        }
        Objects.requireNonNull(incidentId, "incidentId must be set when detaching");

        var formerIncidentId = incidentId;
        var event = new CallDetachedFromIncidentEvent(
                EventId.generate(), now, null, receivingDispatcher, id(), formerIncidentId);
        return new PendingMutation<>(event, this::applyDetach);
    }

    /**
     * Applies a domain event for WAL replay, reconstructing call state without validation.
     */
    public void applyEvent(DomainEvent event) {
        switch (event) {
            case CallCreatedEvent e -> applyCreate(e);
            case CallUpdatedEvent e -> applyUpdate(e);
            case CallEndedEvent e -> applyEnd(e);
            case CallAttachedToIncidentEvent e -> applyAttach(e);
            case CallDetachedFromIncidentEvent e -> applyDetach();
            default -> { /* ignore unrecognized events */ }
        }
    }

    // --- Accessors ---

    public CallState state() { return state; }
    public UserId receivingDispatcher() { return receivingDispatcher; }
    public Instant callStarted() { return callStarted; }
    public @Nullable Instant callEnded() { return callEnded; }
    public @Nullable CallerName callerName() { return callerName; }
    public @Nullable PhoneNumber callerPhoneNumber() { return callerPhoneNumber; }
    public @Nullable Location location() { return location; }
    public @Nullable Description description() { return description; }
    public @Nullable CallOutcome outcome() { return outcome; }
    public @Nullable Description outcomeRationale() { return outcomeRationale; }
    public @Nullable IncidentId incidentId() { return incidentId; }

    // --- Private apply methods ---

    private void applyCreate(CallCreatedEvent e) {
        this.state = CallState.ACTIVE;
        this.callerName = e.callerName();
        this.callerPhoneNumber = e.callerPhoneNumber();
        this.location = e.location();
        this.description = e.description();
    }

    private void applyUpdate(CallUpdatedEvent e) {
        if (e.callerName() != null) this.callerName = e.callerName();
        if (e.callerPhoneNumber() != null) this.callerPhoneNumber = e.callerPhoneNumber();
        if (e.location() != null) this.location = e.location();
        if (e.description() != null) this.description = e.description();
        if (e.outcome() != null) this.outcome = e.outcome();
        if (e.outcomeRationale() != null) this.outcomeRationale = e.outcomeRationale();
        if (e.incidentId() != null) this.incidentId = e.incidentId();
    }

    private void applyEnd(CallEndedEvent e) {
        this.state = CallState.ENDED;
        this.callEnded = e.timestamp();
        this.outcome = e.outcome();
        this.outcomeRationale = e.outcomeRationale();
        this.incidentId = e.incidentId();
    }

    private void applyAttach(CallAttachedToIncidentEvent e) {
        this.outcome = CallOutcome.ATTACHED_TO_INCIDENT;
        this.incidentId = e.incidentId();
    }

    private void applyDetach() {
        this.outcome = null;
        this.incidentId = null;
    }

    /**
     * Reconstructs a Call from its {@link CallCreatedEvent} for WAL replay.
     */
    public static Call fromCreatedEvent(CallCreatedEvent e) {
        return new Call(e.callId(), e.causedByUser(), e.timestamp(),
                e.callerName(), e.callerPhoneNumber(), e.location(), e.description());
    }

    /**
     * Reconstructs a Call from a snapshot, restoring all fields exactly as persisted.
     * For snapshot-based startup recovery only — bypasses the normal creation lifecycle.
     */
    public static Call restore(
            CallId id,
            CallState state,
            UserId receivingDispatcher,
            Instant callStarted,
            @Nullable Instant callEnded,
            @Nullable CallerName callerName,
            @Nullable PhoneNumber callerPhoneNumber,
            @Nullable Location location,
            @Nullable Description description,
            @Nullable CallOutcome outcome,
            @Nullable Description outcomeRationale,
            @Nullable IncidentId incidentId) {
        var call = new Call(id, receivingDispatcher, callStarted,
                callerName, callerPhoneNumber, location, description);
        call.state = state;
        call.callEnded = callEnded;
        call.outcome = outcome;
        call.outcomeRationale = outcomeRationale;
        call.incidentId = incidentId;
        return call;
    }
}
