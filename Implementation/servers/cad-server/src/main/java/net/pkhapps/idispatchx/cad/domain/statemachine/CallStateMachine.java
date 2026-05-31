package net.pkhapps.idispatchx.cad.domain.statemachine;

import net.pkhapps.idispatchx.cad.domain.model.call.CallState;

/**
 * Static utility class that validates {@link CallState} transitions.
 * <p>
 * Valid transitions: {@code ACTIVE → ENDED}.
 */
public final class CallStateMachine {

    private CallStateMachine() {
    }

    /**
     * Validates that transitioning from {@code current} to {@code target} is allowed.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public static void validateTransition(CallState current, CallState target) {
        if (current == CallState.ACTIVE && target == CallState.ENDED) {
            return;
        }
        throw new IllegalStateException(
                "Invalid call state transition: " + current + " → " + target);
    }
}
