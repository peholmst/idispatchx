package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.UpdateCallDetailsCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Handles {@link UpdateCallDetailsCommand}: partially updates call detail fields.
 * <p>
 * Only non-null fields in the command are applied; existing values are preserved for nulls.
 */
public class UpdateCallDetailsCommandHandler extends CommandHandler<UpdateCallDetailsCommand, Void> {

    private final CallRepository callRepository;

    public UpdateCallDetailsCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                           CallRepository callRepository) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
    }

    @Override
    protected LockScope determineLockScope(UpdateCallDetailsCommand command) {
        return LockScope.of("call", command.callId().value());
    }

    @Override
    protected PendingMutation<? extends DomainEvent> prepareExecution(UpdateCallDetailsCommand command) {
        var call = callRepository.findById(command.callId())
                .orElseThrow(() -> new NoSuchElementException("call not found: " + command.callId()));

        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("call is already ENDED: " + command.callId());
        }

        var pending = call.prepareUpdate(
                command.callerName(), command.callerPhoneNumber(),
                command.location(), command.description(),
                command.outcome(), command.outcomeRationale());

        // Rebuild event with causedBy
        var origEvent = pending.event();
        var event = new CallUpdatedEvent(
                EventId.generate(), Instant.now(), command.commandId(), command.userId(),
                origEvent.callId(), origEvent.callerName(), origEvent.callerPhoneNumber(),
                origEvent.location(), origEvent.description(), origEvent.outcome(),
                origEvent.outcomeRationale(), origEvent.incidentId());

        return new PendingMutation<>(event, pending.applyMutation());
    }

    @Override
    protected Void buildResult(UpdateCallDetailsCommand command, DomainEvent event) {
        return null;
    }
}
