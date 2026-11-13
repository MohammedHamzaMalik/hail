package com.hamza.hail.ride;

import com.hamza.hail.driver.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RideServiceNaiveTest {

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
    void successfullyAssignsDriverInSingleThread() {
        // Setup Driver
        UUID driverId = UUID.randomUUID();
        driverRepository.save(driverId, 60.1719, 24.9414, "ONLINE");

        // Setup Ride
        UUID rideId = UUID.randomUUID();
        rideRepository.save(rideId, 60.1720, 24.9415, "REQUESTED", "idem-key-1", "APP");

        // Execute naive assignment
        Optional<UUID> assignedDriver = rideService.assignDriverNaive(rideId, 60.1720, 24.9415, 5.0);

        assertThat(assignedDriver).isPresent();
        assertThat(assignedDriver.get()).isEqualTo(driverId);

        // Verify driver is now BUSY
        var driver = driverRepository.findById(driverId).orElseThrow();
        assertThat(driver.status()).isEqualTo("BUSY");

        // Verify ride is now ASSIGNED
        var ride = rideRepository.findById(rideId).orElseThrow();
        assertThat(ride.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(ride.driverId()).isEqualTo(driverId);
    }
}
