package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.DetachCallFromIncidentCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
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
 * Handles {@link DetachCallFromIncidentCommand}: detaches a call from its incident.
 * <p>
 * Cross-aggregate: writes {@code CallDetachedFromIncidentEvent} and
 * {@code IncidentLogEntryAddedEvent} as a single WAL batch.
 * <p>
 * Locking strategy: {@code determineLockScope} uses a best-effort pre-read to include the
 * incident lock when an {@code incidentId} is already visible. If a concurrent attach races
 * between scope determination and actual execution, {@code prepareBatchExecution} acquires
 * the incident lock explicitly (re-entrant if already held) and holds it through
 * {@code applyMutation} to ensure the incident's log-entry list is never modified without
 * the lock being held.
 */
public class DetachCallFromIncidentCommandHandler
        extends CommandHandler<DetachCallFromIncidentCommand, Void> {

    private final CallRepository callRepository;
    private final IncidentRepository incidentRepository;
    private final ClockPort clock;

    public DetachCallFromIncidentCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                                CallRepository callRepository,
                                                IncidentRepository incidentRepository,
                                                ClockPort clock) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected LockScope determineLockScope(DetachCallFromIncidentCommand command) {
        // Best-effort pre-read: include the incident lock if already visible.
        // If the incidentId changes between this read and prepareBatchExecution(),
        // the incident lock is acquired explicitly inside prepareBatchExecution().
        var callOpt = callRepository.findById(command.callId());
        if (callOpt.isEmpty()) {
            return LockScope.of("call", command.callId().value());
        }
        var incidentId = callOpt.get().incidentId();
        if (incidentId == null) {
            return LockScope.of("call", command.callId().value());
        }
        return LockScope.of(
                new LockKey("call", command.callId().value()),
                new LockKey("incident", incidentId.value()));
    }

    @Override
    protected PendingBatchMutation<? extends DomainEvent> prepareBatchExecution(
            DetachCallFromIncidentCommand command) {
        var call = callRepository.findById(command.callId())
                .orElseThrow(() -> new NoSuchElementException("call not found: " + command.callId()));

        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("call is ENDED: " + command.callId());
        }

        var formerIncidentId = call.incidentId();
        if (formerIncidentId == null) {
            // Let the domain model produce the proper error message
            call.prepareDetachFromIncident(clock.now()); // always throws
            throw new IllegalStateException("unreachable");
        }

        // Acquire the incident lock explicitly. If determineLockScope() already included
        // it (the common case), this is re-entrant and a no-op. If a concurrent attach
        // raced between scope determination and now, this ensures we hold the lock before
        // touching the incident's log entries.
        var incidentLockHandle = lockManager.acquire(
                LockScope.of("incident", formerIncidentId.value()));
        try {
            var incident = incidentRepository.findById(formerIncidentId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "incident not found: " + formerIncidentId));

            var now = clock.now();
            var callPending = call.prepareDetachFromIncident(now);
            var logEntryId = new IncidentLogEntryId(NanoIdGenerator.generate());
            var logPending = incident.prepareAddCallDetachedLogEntry(
                    logEntryId, now, command.userId(), command.callId());

            var callEvent = new CallDetachedFromIncidentEvent(
                    EventId.generate(), now, command.commandId(), command.userId(),
                    command.callId(), formerIncidentId);
            var logEvent = new IncidentLogEntryAddedEvent(
                    EventId.generate(), now, command.commandId(), command.userId(),
                    formerIncidentId, logPending.event().logEntry());

            return new PendingBatchMutation<>(List.of(callEvent, logEvent), () -> {
                try (incidentLockHandle) {
                    callPending.applyMutation().run();
                    logPending.applyMutation().run();
                }
            });
        } catch (Exception e) {
            incidentLockHandle.close();
            throw e;
        }
    }

    @Override
    protected Void buildBatchResult(DetachCallFromIncidentCommand command,
                                    java.util.List<? extends DomainEvent> events) {
        return null;
    }
}
