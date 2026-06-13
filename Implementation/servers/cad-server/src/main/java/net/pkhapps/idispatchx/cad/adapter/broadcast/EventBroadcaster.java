package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Translates domain events into WebSocket messages and routes them to the appropriate
 * broadcast services.
 * <p>
 * Called by {@link net.pkhapps.idispatchx.cad.adapter.secondary.wal.EventPublishingWalPort}
 * after each WAL write. Broadcasting runs asynchronously with respect to the originating command
 * handler — this class is invoked from a dedicated broadcast thread.
 * <p>
 * Each domain event type produces exactly one WebSocket message with the correct type string
 * and payload as specified in section 6.1 of the CAD Server WebSocket/REST API design.
 */
public final class EventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);

    private final DispatcherBroadcastService dispatcherBroadcastService;

    public EventBroadcaster(DispatcherBroadcastService dispatcherBroadcastService) {
        this.dispatcherBroadcastService = Objects.requireNonNull(
                dispatcherBroadcastService, "dispatcherBroadcastService must not be null");
    }

    /**
     * Broadcasts the given domain event to all connected clients. The {@code walSequenceNumber}
     * is included in the WebSocket envelope so clients can detect gaps on reconnect.
     *
     * @param event             the domain event to broadcast
     * @param walSequenceNumber the WAL sequence number assigned to the event
     */
    public void broadcast(DomainEvent event, long walSequenceNumber) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            dispatch(event, walSequenceNumber);
        } catch (Exception e) {
            log.error("Unexpected error broadcasting event {} (seq={}): {}",
                    event.getClass().getSimpleName(), walSequenceNumber, e.getMessage(), e);
        }
    }

    private void dispatch(DomainEvent event, long seq) {
        var ts = event.timestamp();
        switch (event) {
            case CallCreatedEvent e ->
                    dispatcherBroadcastService.sendCallCreated(seq, ts, buildCallPayload(e));
            case CallUpdatedEvent e ->
                    dispatcherBroadcastService.sendCallUpdated(seq, ts, buildCallUpdatedPayload(e));
            case CallEndedEvent e ->
                    dispatcherBroadcastService.sendCallEnded(seq, ts, buildCallEndedPayload(e));
            case CallAttachedToIncidentEvent e ->
                    dispatcherBroadcastService.sendCallAttachedToIncident(
                            seq, ts, e.callId().value(), e.incidentId().value());
            case CallDetachedFromIncidentEvent e ->
                    dispatcherBroadcastService.sendCallDetachedFromIncident(
                            seq, ts, e.callId().value(), e.formerIncidentId().value());
            case IncidentCreatedEvent e ->
                    dispatcherBroadcastService.sendIncidentCreated(seq, ts, buildIncidentCreatedPayload(e));
            case IncidentLogEntryAddedEvent e ->
                    dispatcherBroadcastService.sendIncidentLogEntryAdded(
                            seq, ts, buildLogEntryPayload(e));
            default -> log.trace("No broadcast handler for event type: {}", event.getClass().getSimpleName());
        }
    }

    // Per spec section 6.1: call.created payload is the full call object with all fields (nulls explicit)
    private static Map<String, Object> buildCallPayload(CallCreatedEvent e) {
        return buildCallMap(
                e.callId().value(), "active",
                e.causedByUser().value(), e.timestamp(),
                e.callerName() != null ? e.callerName().value() : null,
                e.callerPhoneNumber() != null ? e.callerPhoneNumber().value() : null,
                null, // location — serialization handled by response layer
                e.description() != null ? e.description().value() : null,
                null, null, null);
    }

    private static Map<String, Object> buildCallUpdatedPayload(CallUpdatedEvent e) {
        return buildCallMap(
                e.callId().value(), null, // state unknown here; read from domain on GET
                e.causedByUser().value(), e.timestamp(),
                e.callerName() != null ? e.callerName().value() : null,
                e.callerPhoneNumber() != null ? e.callerPhoneNumber().value() : null,
                null,
                e.description() != null ? e.description().value() : null,
                e.outcome() != null ? e.outcome().name().toLowerCase() : null,
                e.outcomeRationale() != null ? e.outcomeRationale().value() : null,
                e.incidentId() != null ? e.incidentId().value() : null);
    }

    private static Map<String, Object> buildCallEndedPayload(CallEndedEvent e) {
        return buildCallMap(
                e.callId().value(), "ended",
                e.causedByUser().value(), e.timestamp(),
                null, null, null, null,
                e.outcome().name().toLowerCase(),
                e.outcomeRationale() != null ? e.outcomeRationale().value() : null,
                e.incidentId() != null ? e.incidentId().value() : null);
    }

    private static Map<String, @Nullable Object> buildCallMap(
            String callId, @Nullable String state, String receivingDispatcher, Instant callStarted,
            @Nullable String callerName, @Nullable String callerPhoneNumber,
            @Nullable Object location, @Nullable String description,
            @Nullable String outcome, @Nullable String outcomeRationale, @Nullable String incidentId) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("callId", callId);
        map.put("state", state);
        map.put("receivingDispatcher", receivingDispatcher);
        map.put("callStarted", callStarted);
        map.put("callerName", callerName);
        map.put("callerPhoneNumber", callerPhoneNumber);
        map.put("location", location);
        map.put("description", description);
        map.put("outcome", outcome);
        map.put("outcomeRationale", outcomeRationale);
        map.put("incidentId", incidentId);
        return map;
    }

    private static Map<String, @Nullable Object> buildIncidentCreatedPayload(IncidentCreatedEvent e) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("incidentId", e.incidentId().value());
        map.put("state", "new");
        map.put("incidentCreated", e.timestamp());
        map.put("incidentType", e.incidentType() != null ? e.incidentType().code() : null);
        map.put("incidentPriority", e.incidentPriority() != null ? e.incidentPriority().name() : null);
        map.put("location", null); // Location serialization handled by HTTP layer
        map.put("description", e.description() != null ? e.description().value() : null);
        return map;
    }

    private static Map<String, Object> buildLogEntryPayload(IncidentLogEntryAddedEvent e) {
        var logEntry = e.logEntry();
        var entryMap = new java.util.LinkedHashMap<String, Object>();
        switch (logEntry) {
            case IncidentLogEntry.AutomaticEntry ae -> {
                entryMap.put("logEntryId", ae.id().value());
                entryMap.put("logTimestamp", ae.logTimestamp());
                entryMap.put("dispatcher", ae.dispatcher().value());
                entryMap.put("entryType", "automatic");
                entryMap.put("changeData", ae.changeData());
            }
            case IncidentLogEntry.ManualEntry me -> {
                entryMap.put("logEntryId", me.id().value());
                entryMap.put("logTimestamp", me.logTimestamp());
                entryMap.put("dispatcher", me.dispatcher().value());
                entryMap.put("entryType", "manual");
                entryMap.put("description", me.description().value());
            }
        }
        return Map.of("incidentId", e.incidentId().value(), "logEntry", entryMap);
    }
}
