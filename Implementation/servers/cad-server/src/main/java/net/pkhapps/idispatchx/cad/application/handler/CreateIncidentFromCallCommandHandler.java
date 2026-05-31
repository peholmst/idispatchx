package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CreateIncidentFromCallCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.IncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Handles {@link CreateIncidentFromCallCommand}: creates a new incident from a source call.
 * <p>
 * Cross-aggregate: writes {@code IncidentCreatedEvent}, {@code CallUpdatedEvent}, and
 * {@code IncidentLogEntryAddedEvent} as a single WAL batch. Only the source call lock is
 * acquired (the new incident has no pre-existing lock).
 */
public class CreateIncidentFromCallCommandHandler
        extends CommandHandler<CreateIncidentFromCallCommand, IncidentId> {

    private final CallRepository callRepository;
    private final IncidentRepository incidentRepository;
    private final ClockPort clock;

    public CreateIncidentFromCallCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                                CallRepository callRepository,
                                                IncidentRepository incidentRepository,
                                                ClockPort clock) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected LockScope determineLockScope(CreateIncidentFromCallCommand command) {
        return LockScope.of("call", command.sourceCallId().value());
    }

    @Override
    protected PendingBatchMutation<? extends DomainEvent> prepareBatchExecution(
            CreateIncidentFromCallCommand command) {
        var call = callRepository.findById(command.sourceCallId())
                .orElseThrow(() -> new NoSuchElementException("source call not found: " + command.sourceCallId()));

        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("source call is ENDED: " + command.sourceCallId());
        }
        if (call.outcome() == CallOutcome.INCIDENT_CREATED
                || call.outcome() == CallOutcome.ATTACHED_TO_INCIDENT
                || call.incidentId() != null) {
            throw new IllegalStateException("source call is already linked to an incident: " + command.sourceCallId());
        }

        var now = clock.now();
        var incidentId = new IncidentId(NanoIdGenerator.generate());

        // If location absent in command, copy from call (independent copy — same value, no reference sharing)
        var effectiveLocation = command.location() != null ? command.location() : call.location();

        // Create incident (new, not yet in repository)
        var incidentResult = Incident.create(
                incidentId, now, command.sourceCallId(),
                command.incidentType(), command.incidentPriority(),
                effectiveLocation, command.description(),
                command.userId(), command.commandId());

        // Prepare log entry on the new incident
        var logEntryId = new IncidentLogEntryId(NanoIdGenerator.generate());
        var logPending = incidentResult.incident().prepareAddCallLinkedLogEntry(
                logEntryId, now, command.userId(), command.sourceCallId());

        // CallUpdatedEvent sets outcome = INCIDENT_CREATED and incidentId for WAL replay
        var callUpdatedEvent = new CallUpdatedEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                command.sourceCallId(), null, null, null, null,
                CallOutcome.INCIDENT_CREATED, null, incidentId);

        var incidentCreatedEvent = new IncidentCreatedEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                incidentId, now, command.sourceCallId(),
                command.incidentType(), command.incidentPriority(),
                effectiveLocation, command.description());

        var logEvent = new IncidentLogEntryAddedEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                incidentId, logPending.event().logEntry());

        return new PendingBatchMutation<>(
                List.of(incidentCreatedEvent, callUpdatedEvent, logEvent),
                () -> {
                    incidentRepository.add(incidentResult.incident());
                    logPending.applyMutation().run();
                    // Apply call update: set outcome = INCIDENT_CREATED and incidentId
                    call.applyEvent(callUpdatedEvent);
                });
    }

    @Override
    protected IncidentId buildBatchResult(CreateIncidentFromCallCommand command,
                                          java.util.List<? extends DomainEvent> events) {
        return events.stream()
                .filter(e -> e instanceof IncidentCreatedEvent)
                .map(e -> ((IncidentCreatedEvent) e).incidentId())
                .findFirst()
                .orElseThrow();
    }
}
