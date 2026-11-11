package com.hamza.hail.ride.dto;

import jakarta.validation.constraints.*;

public record RequestRideRequest(
        @NotNull(message = "Pickup latitude is required")
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double pickupLat,

        @NotNull(message = "Pickup longitude is required")
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double pickupLng,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        @NotBlank(message = "Origin is required")
        @Pattern(regexp = "APP|AGENT|API", message = "Origin must be APP, AGENT, or API")
        String origin
) {}
