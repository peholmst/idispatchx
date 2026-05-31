package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.EndCallCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.port.secondary.archive.ArchivePort;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Handles {@link EndCallCommand}: ends an active call and schedules archival if unlinked.
 * <p>
 * Per the Availability NFR, if {@link ArchivePort} is unavailable, the failure is logged
 * but does not prevent the call from being ended.
 */
public class EndCallCommandHandler extends CommandHandler<EndCallCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(EndCallCommandHandler.class);

    private final CallRepository callRepository;
    private final ClockPort clock;
    private final ArchivePort archivePort;

    public EndCallCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                 CallRepository callRepository, ClockPort clock,
                                 ArchivePort archivePort) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.archivePort = Objects.requireNonNull(archivePort, "archivePort must not be null");
    }

    @Override
    protected LockScope determineLockScope(EndCallCommand command) {
        return LockScope.of("call", command.callId().value());
    }

    @Override
    protected PendingMutation<? extends DomainEvent> prepareExecution(EndCallCommand command) {
        var call = callRepository.findById(command.callId())
                .orElseThrow(() -> new NoSuchElementException("call not found: " + command.callId()));

        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("call is already ENDED: " + command.callId());
        }

        var pending = call.prepareEnd(command.outcome(), command.outcomeRationale());

        // Rebuild event with causedBy and authoritative timestamp from ClockPort
        var callEnded = clock.now();
        var origEvent = pending.event();
        var event = new CallEndedEvent(
                EventId.generate(), callEnded, command.commandId(), command.userId(),
                origEvent.callId(), callEnded, origEvent.outcome(),
                origEvent.outcomeRationale(), origEvent.incidentId());

        return new PendingMutation<>(event, () -> {
            pending.applyMutation().run();
            // Schedule archival for unlinked calls; failure must not block the command
            if (call.incidentId() == null) {
                try {
                    archivePort.scheduleUnlinkedCallArchival(call.id());
                } catch (Exception e) {
                    log.warn("Failed to schedule unlinked call archival for callId={}: {}",
                            call.id(), e.getMessage(), e);
                }
            }
        });
    }

    @Override
    protected Void buildResult(EndCallCommand command, DomainEvent event) {
        return null;
    }
}
