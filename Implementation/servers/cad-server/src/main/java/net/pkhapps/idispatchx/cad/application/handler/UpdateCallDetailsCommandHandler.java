package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.UpdateCallDetailsCommand;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Handles {@link UpdateCallDetailsCommand}: partially updates call detail fields.
 * <p>
 * Only non-null fields in the command are applied; existing values are preserved for nulls.
 */
public class UpdateCallDetailsCommandHandler extends CommandHandler<UpdateCallDetailsCommand, Void> {

    private final CallRepository callRepository;
    private final ClockPort clock;

    public UpdateCallDetailsCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                           CallRepository callRepository, ClockPort clock) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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

        var now = clock.now();
        var pending = call.prepareUpdate(
                now,
                command.callerName(), command.callerPhoneNumber(),
                command.location(), command.description());

        // Rebuild event with causedBy and causedByUser from the command
        var orig = pending.event();
        var event = new CallUpdatedEvent(
                EventId.generate(), now, command.commandId(), command.userId(),
                orig.callId(), orig.callerName(), orig.callerPhoneNumber(),
                orig.location(), orig.description(), orig.outcome(),
                orig.outcomeRationale(), orig.incidentId());

        return new PendingMutation<>(event, pending.applyMutation());
    }

    @Override
    protected Void buildResult(UpdateCallDetailsCommand command, DomainEvent event) {
        return null;
    }
}
