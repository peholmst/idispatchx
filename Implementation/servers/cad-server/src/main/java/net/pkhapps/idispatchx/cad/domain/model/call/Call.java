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
                id, callStarted, callerName, callerPhoneNumber, location, description);
        return new CallCreationResult(event, call);
    }

    /**
     * Prepares an update of call detail fields.
     * <p>
     * Only non-null fields in the arguments are applied; existing values are preserved for nulls.
     * Outcomes {@code INCIDENT_CREATED} and {@code ATTACHED_TO_INCIDENT} may not be set here;
     * use the specific attach/create commands instead.
     */
    public PendingMutation<CallUpdatedEvent> prepareUpdate(
            @Nullable CallerName callerName,
            @Nullable PhoneNumber callerPhoneNumber,
            @Nullable Location location,
            @Nullable Description description,
            @Nullable CallOutcome outcome,
            @Nullable Description outcomeRationale) {
        if (outcome == CallOutcome.INCIDENT_CREATED || outcome == CallOutcome.ATTACHED_TO_INCIDENT) {
            throw new IllegalArgumentException(
                    "outcome " + outcome + " must be set via specific commands, not UpdateCallDetails");
        }

        var newCallerName = callerName != null ? callerName : this.callerName;
        var newCallerPhoneNumber = callerPhoneNumber != null ? callerPhoneNumber : this.callerPhoneNumber;
        var newLocation = location != null ? location : this.location;
        var newDescription = description != null ? description : this.description;
        var newOutcome = outcome != null ? outcome : this.outcome;
        var newOutcomeRationale = outcomeRationale != null ? outcomeRationale : this.outcomeRationale;

        var event = new CallUpdatedEvent(
                EventId.generate(), Instant.now(), null, receivingDispatcher,
                id(), newCallerName, newCallerPhoneNumber, newLocation, newDescription,
                newOutcome, newOutcomeRationale, null);
        return new PendingMutation<>(event, () -> applyUpdate(event));
    }

    /**
     * Prepares ending the call.
     * <p>
     * The effective outcome and rationale are resolved: command arguments take precedence over
     * existing values on the call.
     */
    public PendingMutation<CallEndedEvent> prepareEnd(
            @Nullable CallOutcome outcome,
            @Nullable Description outcomeRationale) {
        CallStateMachine.validateTransition(state, CallState.ENDED);

        var effectiveOutcome = outcome != null ? outcome : this.outcome;
        if (effectiveOutcome == null) {
            throw new IllegalStateException("outcome must be set before ending a call");
        }
        if ((effectiveOutcome == CallOutcome.INCIDENT_CREATED || effectiveOutcome == CallOutcome.ATTACHED_TO_INCIDENT)
                && incidentId == null) {
            throw new IllegalStateException(
                    "outcome " + effectiveOutcome + " requires incidentId to already be set; "
                    + "use attach/create commands to link the call before ending it");
        }
        var effectiveRationale = outcomeRationale != null ? outcomeRationale : this.outcomeRationale;
        if (effectiveOutcome.requiresRationale() && effectiveRationale == null) {
            throw new IllegalStateException("outcome " + effectiveOutcome + " requires an outcomeRationale");
        }

        var callEnded = Instant.now();
        var event = new CallEndedEvent(
                EventId.generate(), callEnded, null, receivingDispatcher,
                id(), callEnded, effectiveOutcome, effectiveRationale, incidentId);
        return new PendingMutation<>(event, () -> applyEnd(event));
    }

    /**
     * Prepares attaching this call to an existing incident.
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
     */
    public PendingMutation<CallDetachedFromIncidentEvent> prepareDetachFromIncident() {
        if (state != CallState.ACTIVE) {
            throw new IllegalStateException("call must be ACTIVE to detach from an incident");
        }
        if (outcome != CallOutcome.ATTACHED_TO_INCIDENT) {
            throw new IllegalStateException("call must have outcome ATTACHED_TO_INCIDENT to be detached");
        }
        Objects.requireNonNull(incidentId, "incidentId must be set when detaching");

        var formerIncidentId = incidentId;
        var event = new CallDetachedFromIncidentEvent(
                EventId.generate(), Instant.now(), null, receivingDispatcher, id(), formerIncidentId);
        return new PendingMutation<>(event, () -> applyDetach());
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
        this.callEnded = e.callEnded();
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
        return new Call(e.callId(), e.causedByUser(), e.callStarted(),
                e.callerName(), e.callerPhoneNumber(), e.location(), e.description());
    }
}
