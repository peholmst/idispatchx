package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.AttachCallToIncidentCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentState;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.IncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Handles {@link AttachCallToIncidentCommand}: attaches a call to an existing incident.
 * <p>
 * This is a cross-aggregate operation: both a {@code CallAttachedToIncidentEvent} and an
 * {@code IncidentLogEntryAddedEvent} are written as a single WAL batch before any mutation.
 * Locks on both aggregates are acquired in deterministic (sorted) order to prevent deadlocks.
 */
public class AttachCallToIncidentCommandHandler
        extends CommandHandler<AttachCallToIncidentCommand, Void> {

    private final CallRepository callRepository;
    private final IncidentRepository incidentRepository;
    private final ClockPort clock;

    public AttachCallToIncidentCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                              CallRepository callRepository,
                                              IncidentRepository incidentRepository,
                                              ClockPort clock) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected LockScope determineLockScope(AttachCallToIncidentCommand command) {
        return LockScope.of(
                new LockKey("call", command.callId().value()),
                new LockKey("incident", command.incidentId().value()));
    }

    @Override
    protected PendingBatchMutation<? extends DomainEvent> prepareBatchExecution(
            AttachCallToIncidentCommand command) {
        var call = callRepository.findById(command.callId())
                .orElseThrow(() -> new NoSuchElementException("call not found: " + command.callId()));
        var incident = incidentRepository.findById(command.incidentId())
                .orElseThrow(() -> new NoSuchElementException("incident not found: " + command.incidentId()));

        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("call is ENDED: " + command.callId());
        }
        if (incident.state() == IncidentState.ENDED) {
            throw new IllegalStateException("incident is ENDED: " + command.incidentId());
        }
        if (call.outcome() == CallOutcome.INCIDENT_CREATED
                || call.outcome() == CallOutcome.ATTACHED_TO_INCIDENT
                || call.incidentId() != null) {
            throw new IllegalStateException("call is already linked to an incident: " + command.callId());
        }

        var now = clock.now();
        var callPending = call.prepareAttachToIncident(command.incidentId(), now);
        var logEntryId = new IncidentLogEntryId(NanoIdGenerator.generate());
        var logPending = incident.prepareAddCallLinkedLogEntry(
                logEntryId, now, command.userId(), command.callId());

        // Rebuild events with causedBy
        var callEvent = new CallAttachedToIncidentEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                command.callId(), command.incidentId());
        var logEvent = new IncidentLogEntryAddedEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                command.incidentId(), logPending.event().logEntry());

        return new PendingBatchMutation<>(List.of(callEvent, logEvent), () -> {
            callPending.applyMutation().run();
            logPending.applyMutation().run();
        });
    }

    @Override
    protected Void buildBatchResult(AttachCallToIncidentCommand command,
                                    java.util.List<? extends DomainEvent> events) {
        return null;
    }
}
