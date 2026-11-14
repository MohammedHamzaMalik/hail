package com.hamza.hail.ride;

import com.hamza.hail.driver.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RideConcurrencyLockedTest {

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
    void lockedImplementationSucceedsUnderConcurrency() throws InterruptedException {
        // 1. Seed 10 drivers clustered around Helsinki center
        List<UUID> driverIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            driverIds.add(id);
            driverRepository.save(id, 60.1719 + (i * 0.0001), 24.9414 + (i * 0.0001), "ONLINE");
        }

        // 2. Seed 50 rides requesting pickup nearby
        int totalRides = 50;
        List<UUID> rideIds = new ArrayList<>();
        for (int i = 0; i < totalRides; i++) {
            UUID id = UUID.randomUUID();
            rideIds.add(id);
            rideRepository.save(id, 60.1720, 24.9415, "REQUESTED", "idem-key-locked-" + i, "APP");
        }

        // 3. Fire 50 simultaneous threads calling the locked assignment method
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(totalRides);

        for (UUID rideId : rideIds) {
            executor.submit(() -> {
                try {
                    rideService.assignDriverLocked(rideId, 60.1720, 24.9415, 10.0);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Audit results: exactly 10 drivers exist, so exactly 10 rides should be successfully assigned.
        long assignedRidesCount = rideIds.stream()
                .map(rideRepository::findById)
                .filter(opt -> opt.isPresent() && opt.get().status() == RideStatus.ASSIGNED)
                .count();

        // Verify that every assigned ride has a distinct driver (no double-booking)
        long uniqueAssignedDrivers = rideIds.stream()
                .map(rideRepository::findById)
                .filter(opt -> opt.isPresent() && opt.get().driverId() != null)
                .map(opt -> opt.get().driverId())
                .distinct()
                .count();

        System.out.println("=== LOCKED CONCURRENCY TEST RESULTS ===");
        System.out.println("Total rides requested: " + totalRides);
        System.out.println("Total rides successfully assigned: " + assignedRidesCount);
        System.out.println("Unique drivers utilized: " + uniqueAssignedDrivers);

        // Invariants guaranteed by FOR UPDATE SKIP LOCKED:
        // 1. Exactly 10 rides assigned (since only 10 drivers were available).
        // 2. Unique drivers utilized equals total assigned rides (zero double-bookings).
        assertThat(assignedRidesCount).isEqualTo(10);
        assertThat(uniqueAssignedDrivers).isEqualTo(10);
    }
}
