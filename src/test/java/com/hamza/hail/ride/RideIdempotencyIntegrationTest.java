package com.hamza.hail.ride;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RideIdempotencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    RideService rideService;

    @Autowired
    RideRepository rideRepository;

    @Test
    void duplicateRequestsWithSameIdempotencyKeyReturnSameRide() {
        String key = "test-idem-key-999";

        // First request
        var ride1 = rideService.requestRideIdempotent(60.1720, 24.9415, key, "APP");

        // Second request with same key
        var ride2 = rideService.requestRideIdempotent(60.1720, 24.9415, key, "APP");

        // Must return the exact same ride ID and details
        assertThat(ride1.id()).isEqualTo(ride2.id());
        assertThat(ride1.idempotencyKey()).isEqualTo(key);
    }
}