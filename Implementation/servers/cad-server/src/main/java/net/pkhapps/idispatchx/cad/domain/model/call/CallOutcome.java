package net.pkhapps.idispatchx.cad.domain.model.call;

/**
 * The outcome of a {@link Call}.
 */
public enum CallOutcome {
    INCIDENT_CREATED,
    ATTACHED_TO_INCIDENT,
    CALLER_ADVISED,
    HOAX,
    ACCIDENTAL,
    OTHER_NO_ACTIONS_TAKEN;

    /**
     * Returns true if this outcome requires a rationale to be set when ending the call.
     */
    public boolean requiresRationale() {
        return switch (this) {
            case CALLER_ADVISED, HOAX, ACCIDENTAL, OTHER_NO_ACTIONS_TAKEN -> true;
            case INCIDENT_CREATED, ATTACHED_TO_INCIDENT -> false;
        };
    }
}
