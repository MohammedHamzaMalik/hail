package com.hamza.hail.ride;

import com.hamza.hail.ride.dto.RequestRideRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/rides")
public class RideController {

    private final RideRepository rideRepository;

    public RideController(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    @PostMapping
    public ResponseEntity<RideRepository.RideRecord> requestRide(@RequestBody @Valid RequestRideRequest request) {
        UUID rideId = UUID.randomUUID();
        // Request creates a ride in REQUESTED status without matching yet
        rideRepository.save(
                rideId,
                request.pickupLat(),
                request.pickupLng(),
                RideStatus.REQUESTED.name(),
                request.idempotencyKey(),
                request.origin()
        );

        RideRepository.RideRecord createdRide = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve created ride"));

        return ResponseEntity.status(HttpStatus.CREATED).body(createdRide);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideRepository.RideRecord> getRide(@PathVariable UUID id) {
        return rideRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
