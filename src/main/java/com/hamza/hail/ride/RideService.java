package com.hamza.hail.ride;

import com.hamza.hail.driver.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;

    public RideService(RideRepository rideRepository, DriverRepository driverRepository) {
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
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
}
