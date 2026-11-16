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
    private final RideService rideService;

    public RideController(RideRepository rideRepository, RideService rideService) {
        this.rideRepository = rideRepository;
        this.rideService = rideService;
    }

    @PostMapping
    public ResponseEntity<RideRepository.RideRecord> requestRide(@RequestBody @Valid RequestRideRequest request) {
        RideRepository.RideRecord ride = rideService.requestRideIdempotent(
                request.pickupLat(),
                request.pickupLng(),
                request.idempotencyKey(),
                request.origin()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ride);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideRepository.RideRecord> getRide(@PathVariable UUID id) {
        return rideRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> completeRide(@PathVariable UUID id) {
        rideService.completeRide(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelRide(@PathVariable UUID id) {
        rideService.cancelRide(id);
        return ResponseEntity.noContent().build();
    }
}
