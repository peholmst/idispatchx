package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.LocationDto;
import net.pkhapps.idispatchx.cad.domain.model.incident.Incident;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentPriority;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request and response DTOs for incident management REST endpoints.
 */
public final class IncidentDtos {

    private IncidentDtos() {}

    public record CreateIncidentRequest(
            @Nullable String sourceCallId,
            @Nullable String incidentType,
            @Nullable String incidentPriority,
            @Nullable LocationDto location,
            @Nullable String description
    ) {}

    public record CreateIncidentResponse(String incidentId) {}

    /** Summary view returned by list endpoint — no units or log entries. */
    public record IncidentSummaryResponse(
            String incidentId,
            String state,
            Instant incidentCreated,
            @Nullable Instant incidentEnded,
            @Nullable String incidentType,
            @Nullable String incidentPriority,
            @Nullable LocationDto location,
            @Nullable String description,
            List<String> callIds
    ) {
        public IncidentSummaryResponse {
            callIds = List.copyOf(callIds);
        }
    }

    /** Full detail view returned by single-incident endpoint. */
    public record IncidentDetailResponse(
            String incidentId,
            String state,
            Instant incidentCreated,
            @Nullable Instant incidentEnded,
            @Nullable String incidentType,
            @Nullable String incidentPriority,
            @Nullable LocationDto location,
            @Nullable String description,
            List<String> callIds,
            List<LogEntryDto> logEntries
    ) {
        public IncidentDetailResponse {
            callIds = List.copyOf(callIds);
            logEntries = List.copyOf(logEntries);
        }
    }

    public record IncidentListResponse(List<IncidentSummaryResponse> incidents) {
        public IncidentListResponse {
            incidents = List.copyOf(incidents);
        }
    }

    public record LogEntryDto(
            String logEntryId,
            Instant logTimestamp,
            @Nullable String dispatcher,
            String entryType,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Map<String, Object> changeData,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description
    ) {
        public LogEntryDto {
            changeData = changeData == null ? null : Map.copyOf(changeData);
        }
    }

    // --- Conversion helpers ---

    static @Nullable IncidentPriority parsePriority(String value) {
        try {
            return IncidentPriority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown incidentPriority: " + value);
        }
    }

    static IncidentSummaryResponse toSummary(Incident incident, CallRepository callRepository) {
        var callIds = callRepository.findByIncidentId(incident.id())
                .map(c -> c.id().value())
                .toList();
        var incidentType = incident.incidentType();
        var incidentPriority = incident.incidentPriority();
        var description = incident.description();
        return new IncidentSummaryResponse(
                incident.id().value(),
                incident.state().name().toLowerCase(),
                incident.incidentCreated(),
                null, // incidentEnded not yet implemented in domain
                incidentType != null ? incidentType.code() : null,
                incidentPriority != null ? incidentPriority.name() : null,
                LocationDto.fromDomain(incident.location()),
                description != null ? description.value() : null,
                callIds
        );
    }

    static IncidentDetailResponse toDetail(Incident incident, CallRepository callRepository) {
        var callIds = callRepository.findByIncidentId(incident.id())
                .map(c -> c.id().value())
                .toList();
        var logEntries = incident.logEntries().stream()
                .map(IncidentDtos::toLogEntryDto)
                .toList();
        var incidentType = incident.incidentType();
        var incidentPriority = incident.incidentPriority();
        var description = incident.description();
        return new IncidentDetailResponse(
                incident.id().value(),
                incident.state().name().toLowerCase(),
                incident.incidentCreated(),
                null, // incidentEnded not yet implemented in domain
                incidentType != null ? incidentType.code() : null,
                incidentPriority != null ? incidentPriority.name() : null,
                LocationDto.fromDomain(incident.location()),
                description != null ? description.value() : null,
                callIds,
                logEntries
        );
    }

    @SuppressWarnings("unchecked")
    private static LogEntryDto toLogEntryDto(IncidentLogEntry entry) {
        return switch (entry) {
            case IncidentLogEntry.AutomaticEntry ae -> new LogEntryDto(
                    ae.id().value(),
                    ae.logTimestamp(),
                    ae.dispatcher().value(),
                    "automatic",
                    // Convert JsonNode to Map for serialization
                    ae.changeData().isObject()
                            ? (Map<String, Object>) JsonMapper.builder().build()
                            .convertValue(ae.changeData(), Map.class)
                            : null,
                    null
            );
            case IncidentLogEntry.ManualEntry me -> new LogEntryDto(
                    me.id().value(),
                    me.logTimestamp(),
                    me.dispatcher().value(),
                    "manual",
                    null,
                    me.description().value()
            );
        };
    }
}
