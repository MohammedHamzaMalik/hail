package com.hamza.hail.driver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class NearestDriverIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    DriverRepository driverRepository;

    @Test
    void findsNearestOnlineDriverWithinRadius() {
        // Pickup location: Helsinki Central Station (60.1719, 24.9414)
        double pickupLat = 60.1719;
        double pickupLng = 24.9414;

        // Driver 1: Kallio (ONLINE, ~1.5 km away)
        UUID driverKallio = UUID.randomUUID();
        driverRepository.save(driverKallio, 60.1842, 24.9515, "ONLINE");

        // Driver 2: Kamppi (ONLINE, ~0.8 km away - closer!)
        UUID driverKamppi = UUID.randomUUID();
        driverRepository.save(driverKamppi, 60.1690, 24.9317, "ONLINE");

        // Driver 3: Offline nearby (OFFLINE, ~0.5 km away - should be ignored)
        UUID driverOffline = UUID.randomUUID();
        driverRepository.save(driverOffline, 60.1730, 24.9400, "OFFLINE");

        // Driver 4: Oulu (ONLINE, way outside 5km radius - should be ignored)
        UUID driverOulu = UUID.randomUUID();
        driverRepository.save(driverOulu, 65.0121, 25.4651, "ONLINE");

        // Query nearest driver within a 5 km radius
        Optional<DriverRepository.DriverRecord> nearest = driverRepository.findNearestAvailableDriver(pickupLat, pickupLng, 5.0);

        assertThat(nearest).isPresent();
        // Kamppi is closer than Kallio
        assertThat(nearest.get().id()).isEqualTo(driverKamppi);
    }

    @Test
    void returnsEmptyWhenNoDriverInRange() {
        double pickupLat = 60.1719;
        double pickupLng = 24.9414;

        // Only driver is in Oulu (outside radius)
        UUID driverOulu = UUID.randomUUID();
        driverRepository.save(driverOulu, 65.0121, 25.4651, "ONLINE");

        Optional<DriverRepository.DriverRecord> nearest = driverRepository.findNearestAvailableDriver(pickupLat, pickupLng, 5.0);

        assertThat(nearest).isEmpty();
    }
}
