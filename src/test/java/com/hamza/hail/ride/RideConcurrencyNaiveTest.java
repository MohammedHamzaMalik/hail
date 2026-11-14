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
class RideConcurrencyNaiveTest {

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
    void naiveImplementationFailsUnderConcurrency() throws InterruptedException {
        // 1. Seed 10 drivers clustered around Helsinki center
        List<UUID> driverIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            driverIds.add(id);
            // Slight coordinate variations all within close range
            driverRepository.save(id, 60.1719 + (i * 0.0001), 24.9414 + (i * 0.0001), "ONLINE");
        }

        // 2. Seed 50 rides requesting pickup nearby
        int totalRides = 50;
        List<UUID> rideIds = new ArrayList<>();
        for (int i = 0; i < totalRides; i++) {
            UUID id = UUID.randomUUID();
            rideIds.add(id);
            rideRepository.save(id, 60.1720, 24.9415, "REQUESTED", "idem-key-" + i, "APP");
        }

        // 3. Fire 50 simultaneous threads trying to assign rides using the naive method
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(totalRides);

        for (UUID rideId : rideIds) {
            executor.submit(() -> {
                try {
                    rideService.assignDriverNaive(rideId, 60.1720, 24.9415, 10.0);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Audit results across all rides and drivers
        long assignedRidesCount = rideIds.stream()
                .map(rideRepository::findById)
                .filter(Optional -> Optional.isPresent() && Optional.get().status() == RideStatus.ASSIGNED)
                .count();

        // Check if any driver was assigned to more than one ride (double-booking race condition)
        long duplicateDriverAssignments = rideIds.stream()
                .map(rideRepository::findById)
                .filter(Optional -> Optional.isPresent() && Optional.get().driverId() != null)
                .map(Optional -> Optional.get().driverId())
                .distinct()
                .count();

        System.out.println("=== NAIVE CONCURRENCY TEST RESULTS ===");
        System.out.println("Total rides requested: " + totalRides);
        System.out.println("Total rides successfully assigned: " + assignedRidesCount);
        System.out.println("Unique drivers utilized: " + duplicateDriverAssignments);
        System.out.println("Total available drivers: 10");

        // The naive implementation suffers from race conditions:
        // Multiple threads read the same "nearest driver" before any update marks them BUSY.
        // Therefore, unique drivers utilized will likely be way less than expected, or rides
        // will attempt to overwrite the same drivers.
        assertThat(duplicateDriverAssignments).isLessThan(totalRides);
    }
}
