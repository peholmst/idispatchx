package net.pkhapps.idispatchx.cad.port.secondary.snapshot;

import net.pkhapps.idispatchx.cad.domain.model.call.Call;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Serializable snapshot DTO for {@link Call}.
 * <p>
 * Used in place of the live aggregate in {@link OperationalState} so that snapshot
 * serialization does not require Jackson annotations on domain classes.
 */
public record CallSnapshot(
        CallId id,
        CallState state,
        UserId receivingDispatcher,
        Instant callStarted,
        @Nullable Instant callEnded,
        @Nullable CallerName callerName,
        @Nullable PhoneNumber callerPhoneNumber,
        @Nullable Location location,
        @Nullable Description description,
        @Nullable CallOutcome outcome,
        @Nullable Description outcomeRationale,
        @Nullable IncidentId incidentId
) {

    /** Creates a snapshot DTO from a live {@link Call} aggregate. */
    public static CallSnapshot from(Call call) {
        return new CallSnapshot(
                call.id(),
                call.state(),
                call.receivingDispatcher(),
                call.callStarted(),
                call.callEnded(),
                call.callerName(),
                call.callerPhoneNumber(),
                call.location(),
                call.description(),
                call.outcome(),
                call.outcomeRationale(),
                call.incidentId()
        );
    }

    /** Reconstructs a live {@link Call} aggregate from this snapshot DTO. */
    public Call toCall() {
        return Call.restore(id, state, receivingDispatcher, callStarted, callEnded,
                callerName, callerPhoneNumber, location, description,
                outcome, outcomeRationale, incidentId);
    }
}
