package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.LocationDto;
import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Request and response DTOs for call management REST endpoints.
 */
public final class CallDtos {

    private CallDtos() {}

    public record CreateCallRequest(
            @Nullable String callerName,
            @Nullable String callerPhoneNumber,
            @Nullable LocationDto location,
            @Nullable String description
    ) {}

    public record CreateCallResponse(
            String callId,
            String state,
            String receivingDispatcher,
            Instant callStarted
    ) {}

    public record UpdateCallDetailsRequest(
            @Nullable String callerName,
            @Nullable String callerPhoneNumber,
            @Nullable LocationDto location,
            @Nullable String description,
            @Nullable String outcome,
            @Nullable String outcomeRationale
    ) {}

    public record EndCallRequest(
            @Nullable String outcome,
            @Nullable String outcomeRationale
    ) {}

    public record AttachCallToIncidentRequest(String incidentId) {}

    public record CallResponse(
            String callId,
            String state,
            String receivingDispatcher,
            Instant callStarted,
            @Nullable String callerName,
            @Nullable String callerPhoneNumber,
            @Nullable LocationDto location,
            @Nullable String description,
            @Nullable String outcome,
            @Nullable String outcomeRationale,
            @Nullable String incidentId
    ) {}

    public record CallListResponse(List<CallResponse> calls) {}

    // --- Conversion helpers ---

    static CallResponse fromDomain(Call call) {
        return new CallResponse(
                call.id().value(),
                call.state().name().toLowerCase(),
                call.receivingDispatcher().value(),
                call.callStarted(),
                call.callerName() != null ? call.callerName().value() : null,
                call.callerPhoneNumber() != null ? call.callerPhoneNumber().value() : null,
                LocationDto.fromDomain(call.location()),
                call.description() != null ? call.description().value() : null,
                call.outcome() != null ? serializeOutcome(call.outcome()) : null,
                call.outcomeRationale() != null ? call.outcomeRationale().value() : null,
                call.incidentId() != null ? call.incidentId().value() : null
        );
    }

    static @Nullable CallOutcome parseOutcome(String value) {
        return switch (value) {
            case "incident_created" -> CallOutcome.INCIDENT_CREATED;
            case "attached_to_incident" -> CallOutcome.ATTACHED_TO_INCIDENT;
            case "caller_advised" -> CallOutcome.CALLER_ADVISED;
            case "hoax" -> CallOutcome.HOAX;
            case "accidental" -> CallOutcome.ACCIDENTAL;
            case "other_no_actions_taken" -> CallOutcome.OTHER_NO_ACTIONS_TAKEN;
            default -> throw new IllegalArgumentException("Unknown outcome: " + value);
        };
    }

    static String serializeOutcome(CallOutcome outcome) {
        return switch (outcome) {
            case INCIDENT_CREATED -> "incident_created";
            case ATTACHED_TO_INCIDENT -> "attached_to_incident";
            case CALLER_ADVISED -> "caller_advised";
            case HOAX -> "hoax";
            case ACCIDENTAL -> "accidental";
            case OTHER_NO_ACTIONS_TAKEN -> "other_no_actions_taken";
        };
    }
}
