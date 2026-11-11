package com.hamza.hail.ride;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideStateMachineTest {

    private final RideStateMachine stateMachine = new RideStateMachine();

    @Test
    void allowsValidTransitions() {
        // REQUESTED -> ASSIGNED -> COMPLETED
        assertThatCode(() -> stateMachine.validateTransition(RideStatus.REQUESTED, RideStatus.ASSIGNED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(RideStatus.ASSIGNED, RideStatus.COMPLETED))
                .doesNotThrowAnyException();

        // REQUESTED -> CANCELLED
        assertThatCode(() -> stateMachine.validateTransition(RideStatus.REQUESTED, RideStatus.CANCELLED))
                .doesNotThrowAnyException();

        // ASSIGNED -> CANCELLED
        assertThatCode(() -> stateMachine.validateTransition(RideStatus.ASSIGNED, RideStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTransitions() {
        // Cannot jump straight from REQUESTED to COMPLETED
        assertThatThrownBy(() -> stateMachine.validateTransition(RideStatus.REQUESTED, RideStatus.COMPLETED))
                .isInstanceOf(InvalidRideTransitionException.class);

        // Terminal states cannot transition
        assertThatThrownBy(() -> stateMachine.validateTransition(RideStatus.COMPLETED, RideStatus.REQUESTED))
                .isInstanceOf(InvalidRideTransitionException.class);
        assertThatThrownBy(() -> stateMachine.validateTransition(RideStatus.CANCELLED, RideStatus.ASSIGNED))
                .isInstanceOf(InvalidRideTransitionException.class);
    }
}
