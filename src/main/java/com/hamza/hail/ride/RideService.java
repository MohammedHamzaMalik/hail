package com.hamza.hail.ride;

import com.hamza.hail.driver.DriverRepository;
import com.hamza.hail.ride.dto.RequestRideRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final RideStateMachine rideStateMachine;

    public RideService(RideRepository rideRepository, DriverRepository driverRepository,  RideStateMachine rideStateMachine) {
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.rideStateMachine = rideStateMachine;
    }

    public Optional<UUID> assignDriverNaive(UUID rideId, double pickupLat, double pickupLng, double radiusKm) {
        // Step 1: SELECT the nearest available driver (no lock held across steps)
        Optional<DriverRepository.DriverRecord> nearestDriver = driverRepository.findNearestAvailableDriver(pickupLat, pickupLng, radiusKm);

        if (nearestDriver.isEmpty()) {
            return Optional.empty();
        }

        UUID driverId = nearestDriver.get().id();

        // Step 2: Plain UPDATE to mark driver BUSY
        driverRepository.markBusy(driverId);

        // Step 3: Plain UPDATE to assign ride
        rideRepository.assignDriver(rideId, driverId);

        return Optional.of(driverId);
    }

    @Transactional
    public Optional<UUID> assignDriverLocked(UUID rideId, double pickupLat, double pickupLng, double radiusKm) {
        // Step 1: Find and lock the nearest available driver atomically
        Optional<DriverRepository.DriverRecord> nearestDriver = driverRepository.findAndLockNearestAvailableDriver(pickupLat, pickupLng, radiusKm);

        if (nearestDriver.isEmpty()) {
            return Optional.empty();
        }

        UUID driverId = nearestDriver.get().id();

        // Step 2: Mark driver BUSY within the active transaction lock
        driverRepository.markBusy(driverId);

        // Step 3: Assign ride
        rideRepository.assignDriver(rideId, driverId);

        return Optional.of(driverId);
    }

    public RideRepository.RideRecord requestRide(RequestRideRequest request) {
        UUID rideId = UUID.randomUUID();
        rideRepository.save(
                rideId,
                request.pickupLat(),
                request.pickupLng(),
                RideStatus.REQUESTED.name(),
                request.idempotencyKey(),
                request.origin()
        );
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve created ride"));
    }

    @Transactional
    public void completeRide(UUID rideId) {
        RideRepository.RideRecord ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));

        // Validate transition through state machine
        rideStateMachine.validateTransition(ride.status(), RideStatus.COMPLETED);

        // Update ride status
        rideRepository.updateStatus(rideId, RideStatus.COMPLETED);

        // Free the driver back to ONLINE
        if (ride.driverId() != null) {
            driverRepository.markOnline(ride.driverId());
        }
    }

    @Transactional
    public void cancelRide(UUID rideId) {
        RideRepository.RideRecord ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));

        // Validate transition through state machine
        rideStateMachine.validateTransition(ride.status(), RideStatus.CANCELLED);

        // Update ride status
        rideRepository.updateStatus(rideId, RideStatus.CANCELLED);

        // Free the driver back to ONLINE only if a driver was assigned
        if (ride.driverId() != null) {
            driverRepository.markOnline(ride.driverId());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public RideRepository.RideRecord requestRideIdempotent(double pickupLat, double pickupLng, String idempotencyKey, String origin) {
        // Check if a ride with this idempotency key already exists
        Optional<RideRepository.RideRecord> existing = rideRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Otherwise, create a new ride
        UUID rideId = UUID.randomUUID();
        rideRepository.save(
                rideId,
                pickupLat,
                pickupLng,
                RideStatus.REQUESTED.name(),
                idempotencyKey,
                origin
        );

        return rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve created ride"));
    }
}
