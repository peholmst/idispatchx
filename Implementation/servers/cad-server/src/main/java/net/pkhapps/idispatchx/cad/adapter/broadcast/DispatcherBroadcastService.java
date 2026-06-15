package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher.DispatcherSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Sends WebSocket event messages to all connected dispatcher sessions.
 * <p>
 * Each {@code sendXxx()} method serializes the payload into the standard envelope format and
 * delivers it to every session currently registered in the {@link SessionRegistry}.
 * Calls are best-effort — errors on individual sessions are logged and do not abort delivery
 * to other sessions.
 */
public class DispatcherBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(DispatcherBroadcastService.class);

    private final SessionRegistry sessionRegistry;

    public DispatcherBroadcastService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
    }

    public void sendCallCreated(long sequenceNumber, Instant timestamp, Object callPayload) {
        broadcast("call.created", sequenceNumber, timestamp, callPayload);
    }

    public void sendCallUpdated(long sequenceNumber, Instant timestamp, Object callPayload) {
        broadcast("call.updated", sequenceNumber, timestamp, callPayload);
    }

    public void sendCallEnded(long sequenceNumber, Instant timestamp, Object callPayload) {
        broadcast("call.ended", sequenceNumber, timestamp, callPayload);
    }

    public void sendCallAttachedToIncident(long sequenceNumber, Instant timestamp,
                                           String callId, String incidentId) {
        broadcast("call.attached_to_incident", sequenceNumber, timestamp,
                Map.of("callId", callId, "incidentId", incidentId));
    }

    public void sendCallDetachedFromIncident(long sequenceNumber, Instant timestamp,
                                             String callId, String formerIncidentId) {
        broadcast("call.detached_from_incident", sequenceNumber, timestamp,
                Map.of("callId", callId, "formerIncidentId", formerIncidentId));
    }

    public void sendIncidentCreated(long sequenceNumber, Instant timestamp, Object incidentPayload) {
        broadcast("incident.created", sequenceNumber, timestamp, incidentPayload);
    }

    public void sendIncidentLogEntryAdded(long sequenceNumber, Instant timestamp,
                                          Object logEntryPayload) {
        broadcast("incident.log_entry_added", sequenceNumber, timestamp, logEntryPayload);
    }

    private void broadcast(String type, long sequenceNumber, Instant timestamp, Object payload) {
        var envelope = new WsEnvelope(type, sequenceNumber, timestamp, payload);
        var sessions = sessionRegistry.getDispatcherSessions();
        if (sessions.isEmpty()) return;

        log.debug("Broadcasting {} to {} dispatcher session(s)", type, sessions.size());
        for (DispatcherSession session : sessions) {
            try {
                session.send(envelope);
            } catch (Exception e) {
                log.warn("Failed to deliver {} to session {}: {}", type, session.sessionId(), e.getMessage());
            }
        }
    }

    /**
     * Standard WebSocket message envelope.
     */
    public record WsEnvelope(String type, long sequenceNumber, Instant timestamp, Object payload) {}
}
