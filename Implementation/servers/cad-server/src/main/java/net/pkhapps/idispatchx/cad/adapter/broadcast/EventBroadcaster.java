package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.LocationDto;
import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Translates domain events into WebSocket messages and routes them to the appropriate
 * broadcast services.
 * <p>
 * Called by {@link net.pkhapps.idispatchx.cad.adapter.secondary.wal.EventPublishingWalPort}
 * after each WAL write. Broadcasting runs asynchronously with respect to the originating
 * command handler — this class is invoked from a dedicated broadcast thread.
 * <p>
 * For call events that require a complete snapshot ({@code call.created}, {@code call.updated},
 * {@code call.ended}), the broadcaster reads the current aggregate from {@link CallRepository}.
 * By the time broadcasting runs (after WAL write and state mutation), the repository already
 * reflects the post-command state.
 * <p>
 * Each domain event type produces exactly one WebSocket message with the correct type string
 * and payload as specified in section 6.1 of the CAD Server WebSocket/REST API design.
 */
public final class EventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);

    private final DispatcherBroadcastService dispatcherBroadcastService;
    private final CallRepository callRepository;

    public EventBroadcaster(DispatcherBroadcastService dispatcherBroadcastService,
                            CallRepository callRepository) {
        this.dispatcherBroadcastService = Objects.requireNonNull(
                dispatcherBroadcastService, "dispatcherBroadcastService must not be null");
        this.callRepository = Objects.requireNonNull(
                callRepository, "callRepository must not be null");
    }

    /**
     * Broadcasts the given domain event to all connected clients.
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
            case CallCreatedEvent e -> dispatcherBroadcastService.sendCallCreated(
                    seq, ts, buildCallSnapshot(e.callId(), "active", null));
            case CallUpdatedEvent e -> dispatcherBroadcastService.sendCallUpdated(
                    seq, ts, buildCallSnapshot(e.callId(), null, null));
            case CallEndedEvent e -> dispatcherBroadcastService.sendCallEnded(
                    seq, ts, buildCallSnapshot(e.callId(), "ended", null));
            case CallAttachedToIncidentEvent e -> dispatcherBroadcastService.sendCallAttachedToIncident(
                    seq, ts, e.callId().value(), e.incidentId().value());
            case CallDetachedFromIncidentEvent e -> dispatcherBroadcastService.sendCallDetachedFromIncident(
                    seq, ts, e.callId().value(), e.formerIncidentId().value());
            case IncidentCreatedEvent e -> dispatcherBroadcastService.sendIncidentCreated(
                    seq, ts, buildIncidentCreatedPayload(e));
            case IncidentLogEntryAddedEvent e -> dispatcherBroadcastService.sendIncidentLogEntryAdded(
                    seq, ts, buildLogEntryPayload(e));
            default -> log.trace("No broadcast handler for event type: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * Builds a complete call object payload from the current in-memory aggregate state.
     * <p>
     * Per spec section 6.1, {@code call.created}, {@code call.updated}, and {@code call.ended}
     * all carry the full call object so clients can replace their local copy without partial
     * overwrites.
     *
     * @param callId        the call to look up
     * @param fallbackState state to use when the aggregate is not found (should not happen)
     * @param ignored       unused; kept for overload clarity
     */
    private Map<String, @Nullable Object> buildCallSnapshot(
            net.pkhapps.idispatchx.cad.domain.model.shared.CallId callId,
            @Nullable String fallbackState, @Nullable Object ignored) {

        var call = callRepository.findById(callId).orElse(null);
        if (call == null) {
            log.warn("Call {} not found in repository during broadcast — snapshot will be incomplete", callId);
            return buildEmptyCallMap(callId.value(), fallbackState);
        }
        return buildCallMapFromAggregate(call);
    }

    private static Map<String, @Nullable Object> buildCallMapFromAggregate(Call call) {
        var map = new LinkedHashMap<String, Object>();
        map.put("callId", call.id().value());
        map.put("state", call.state().name().toLowerCase());
        map.put("receivingDispatcher", call.receivingDispatcher().value());
        map.put("callStarted", call.callStarted());
        map.put("callerName", call.callerName() != null ? call.callerName().value() : null);
        map.put("callerPhoneNumber", call.callerPhoneNumber() != null ? call.callerPhoneNumber().value() : null);
        map.put("location", LocationDto.fromDomain(call.location()));
        map.put("description", call.description() != null ? call.description().value() : null);
        map.put("outcome", call.outcome() != null ? serializeOutcome(call.outcome()) : null);
        map.put("outcomeRationale", call.outcomeRationale() != null ? call.outcomeRationale().value() : null);
        map.put("incidentId", call.incidentId() != null ? call.incidentId().value() : null);
        return map;
    }

    private static Map<String, @Nullable Object> buildEmptyCallMap(String callId, @Nullable String state) {
        var map = new LinkedHashMap<String, Object>();
        map.put("callId", callId);
        map.put("state", state);
        map.put("receivingDispatcher", null);
        map.put("callStarted", null);
        map.put("callerName", null);
        map.put("callerPhoneNumber", null);
        map.put("location", null);
        map.put("description", null);
        map.put("outcome", null);
        map.put("outcomeRationale", null);
        map.put("incidentId", null);
        return map;
    }

    private static Map<String, @Nullable Object> buildIncidentCreatedPayload(IncidentCreatedEvent e) {
        var map = new LinkedHashMap<String, Object>();
        map.put("incidentId", e.incidentId().value());
        map.put("state", "new");
        map.put("incidentCreated", e.timestamp());
        map.put("incidentType", e.incidentType() != null ? e.incidentType().code() : null);
        map.put("incidentPriority", e.incidentPriority() != null ? e.incidentPriority().name() : null);
        map.put("location", LocationDto.fromDomain(e.location()));
        map.put("description", e.description() != null ? e.description().value() : null);
        return map;
    }

    private static Map<String, Object> buildLogEntryPayload(IncidentLogEntryAddedEvent e) {
        var logEntry = e.logEntry();
        var entryMap = new LinkedHashMap<String, Object>();
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

    private static String serializeOutcome(CallOutcome outcome) {
        return outcome.name().toLowerCase();
    }
}
