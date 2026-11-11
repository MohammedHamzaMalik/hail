package com.hamza.hail.ride;

import org.springframework.stereotype.Component;

@Component
public class RideStateMachine {

    public void validateTransition(RideStatus current, RideStatus target) {
        if (current == target) {
            return; // No-op transition is technically safe or can be ignored
        }

        boolean isValid = switch (current) {
            case REQUESTED -> target == RideStatus.ASSIGNED || target == RideStatus.CANCELLED;
            case ASSIGNED -> target == RideStatus.COMPLETED || target == RideStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false; // Terminal states cannot transition further
        };

        if (!isValid) {
            throw new InvalidRideTransitionException(
                    String.format("Invalid state transition from %s to %s", current, target)
            );
        }
    }
}
