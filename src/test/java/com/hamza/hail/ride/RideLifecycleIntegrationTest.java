package com.hamza.hail.ride;

import com.hamza.hail.driver.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RideLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    RideService rideService;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    RideRepository rideRepository;

    @Test
    void fullLifecycleAssignCompleteFreesDriver() {
        // 1. Setup Driver
        UUID driverId = UUID.randomUUID();
        driverRepository.save(driverId, 60.1719, 24.9414, "ONLINE");

        // 2. Setup Ride
        UUID rideId = UUID.randomUUID();
        rideRepository.save(rideId, 60.1720, 24.9415, "REQUESTED", "idem-lifecycle-1", "APP");

        // 3. Assign Driver
        rideService.assignDriverLocked(rideId, 60.1720, 24.9415, 5.0);
        assertThat(driverRepository.findById(driverId).get().status()).isEqualTo("BUSY");
        assertThat(rideRepository.findById(rideId).get().status()).isEqualTo(RideStatus.ASSIGNED);

        // 4. Complete Ride
        rideService.completeRide(rideId);

        // 5. Verify Ride is COMPLETED and Driver is back to ONLINE
        assertThat(rideRepository.findById(rideId).get().status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(driverRepository.findById(driverId).get().status()).isEqualTo("ONLINE");
    }

    @Test
    void rejectsInvalidStateTransitionsWithException() {
        UUID rideId = UUID.randomUUID();
        rideRepository.save(rideId, 60.1720, 24.9415, "REQUESTED", "idem-lifecycle-2", "APP");

        // Trying to complete a REQUESTED ride directly without assigning should throw InvalidRideTransitionException (mapped to 409)
        assertThatThrownBy(() -> rideService.completeRide(rideId))
                .isInstanceOf(InvalidRideTransitionException.class);
    }
}
