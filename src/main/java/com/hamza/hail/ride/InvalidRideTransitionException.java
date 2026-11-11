package com.hamza.hail.ride;

public class InvalidRideTransitionException extends RuntimeException {
    public InvalidRideTransitionException(String message) {
        super(message);
    }
}
