package net.pkhapps.idispatchx.cad.application.replay;

import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.IncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Replays WAL events on startup to reconstruct in-memory state.
 * <p>
 * Events are applied in WAL order. Unknown event types are logged and skipped.
 */
public class WalReplayService {

    private static final Logger log = LoggerFactory.getLogger(WalReplayService.class);

    private final WalPort walPort;
    private final CallRepository callRepository;
    private final IncidentRepository incidentRepository;

    public WalReplayService(WalPort walPort, CallRepository callRepository,
                            IncidentRepository incidentRepository) {
        this.walPort = Objects.requireNonNull(walPort, "walPort must not be null");
        this.callRepository = Objects.requireNonNull(callRepository, "callRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
    }

    /**
     * Replays the full WAL, reconstructing all calls and incidents.
     */
    public void replayAll() {
        log.info("Starting WAL replay");
        walPort.replay(this::handleEvent);
        log.info("WAL replay complete: {} active calls, {} active incidents",
                callRepository.findAll().count(),
                incidentRepository.findAll().count());
    }

    private void handleEvent(DomainEvent event) {
        switch (event) {
            case CallCreatedEvent e -> {
                var call = Call.fromCreatedEvent(e);
                callRepository.add(call);
            }
            case CallUpdatedEvent e -> callRepository.findById(e.callId())
                    .ifPresentOrElse(call -> call.applyEvent(e),
                            () -> log.warn("CallUpdatedEvent: call not found: {}", e.callId()));
            case CallEndedEvent e -> callRepository.findById(e.callId())
                    .ifPresentOrElse(call -> call.applyEvent(e),
                            () -> log.warn("CallEndedEvent: call not found: {}", e.callId()));
            case CallAttachedToIncidentEvent e -> callRepository.findById(e.callId())
                    .ifPresentOrElse(call -> call.applyEvent(e),
                            () -> log.warn("CallAttachedToIncidentEvent: call not found: {}", e.callId()));
            case CallDetachedFromIncidentEvent e -> callRepository.findById(e.callId())
                    .ifPresentOrElse(call -> call.applyEvent(e),
                            () -> log.warn("CallDetachedFromIncidentEvent: call not found: {}", e.callId()));
            case IncidentCreatedEvent e -> {
                var incident = Incident.fromCreatedEvent(e);
                incidentRepository.add(incident);
            }
            case IncidentLogEntryAddedEvent e -> incidentRepository.findById(e.incidentId())
                    .ifPresentOrElse(incident -> incident.applyEvent(e),
                            () -> log.warn("IncidentLogEntryAddedEvent: incident not found: {}", e.incidentId()));
            default -> log.debug("Skipping unrecognized event type: {}", event.getClass().getName());
        }
    }
}
