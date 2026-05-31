package net.pkhapps.idispatchx.cad.application.handler;

import net.pkhapps.idispatchx.cad.domain.command.CreateCallCommand;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.NanoIdGenerator;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;

import java.util.Objects;

/**
 * Handles {@link CreateCallCommand}: creates a new call, writes {@code CallCreatedEvent} to WAL,
 * adds the call to the repository, and returns the new {@link CallId}.
 */
public class CreateCallCommandHandler extends CommandHandler<CreateCallCommand, CallId> {

    private final CallRepository callRepository;
    private final ClockPort clock;

    public CreateCallCommandHandler(WalPort walPort, EntityLockManager lockManager,
                                    CallRepository callRepository, ClockPort clock) {
        super(walPort, lockManager);
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected LockScope determineLockScope(CreateCallCommand command) {
        // New call — no pre-existing lock needed; use command ID as a unique key
        return LockScope.of("call-create", command.commandId().value());
    }

    @Override
    protected PendingMutation<? extends DomainEvent> prepareExecution(CreateCallCommand command) {
        var callId = new CallId(NanoIdGenerator.generate());
        var callStarted = clock.now();

        var result = Call.create(callId, command.userId(), callStarted,
                command.callerName(), command.callerPhoneNumber(),
                command.location(), command.description());

        // Patch causedBy onto the event
        var event = new net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent(
                result.event().eventId(), result.event().timestamp(),
                command.commandId(), command.userId(),
                callId, callStarted,
                command.callerName(), command.callerPhoneNumber(),
                command.location(), command.description());

        return new PendingMutation<>(event, () -> callRepository.add(result.call()));
    }

    @Override
    protected CallId buildResult(CreateCallCommand command, DomainEvent event) {
        return ((net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent) event).callId();
    }
}
