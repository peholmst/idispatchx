package net.pkhapps.idispatchx.cad.domain.statemachine;

import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallStateMachineTest {

    @Test
    void activeToEnded_isValid() {
        assertDoesNotThrow(() -> CallStateMachine.validateTransition(CallState.ACTIVE, CallState.ENDED));
    }

    @Test
    void endedToActive_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> CallStateMachine.validateTransition(CallState.ENDED, CallState.ACTIVE));
    }

    @Test
    void activeToActive_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> CallStateMachine.validateTransition(CallState.ACTIVE, CallState.ACTIVE));
    }

    @Test
    void endedToEnded_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> CallStateMachine.validateTransition(CallState.ENDED, CallState.ENDED));
    }
}
