package com.hamza.hail.driver;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DriverRepository {

    private final JdbcClient jdbcClient;

    public DriverRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int save(UUID id, double lat, double lng, String status) {
        return jdbcClient.sql("""
                INSERT INTO drivers (id, lat, lng, status, created_at, updated_at)
                VALUES (:id, :lat, :lng, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .param("id", id)
                .param("lat", lat)
                .param("lng", lng)
                .param("status", status)
                .update();
    }

    public int updateStatus(UUID id, String status) {
        return jdbcClient.sql("""
                UPDATE drivers
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("status", status)
                .param("id", id)
                .update();
    }

    public int updateLocation(UUID id, double lat, double lng) {
        return jdbcClient.sql("""
                UPDATE drivers
                SET lat = :lat, lng = :lng, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("lat", lat)
                .param("lng", lng)
                .param("id", id)
                .update();
    }

    public int markBusy(UUID id) {
        return jdbcClient.sql("""
                UPDATE drivers
                SET status = 'BUSY', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'ONLINE'
                """)
                .param("id", id)
                .update();
    }

    public Optional<DriverRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT id, lat, lng, status, created_at, updated_at FROM drivers WHERE id = :id")
                .param("id", id)
                .query(DriverRecord.class)
                .optional();
    }

    public Optional<DriverRecord> findNearestAvailableDriver(double pickupLat, double pickupLng, double radiusKm) {
        // 1 degree of latitude is approximately 111.32 km
        double latDelta = radiusKm / 111.32;
        // Longitude degree distance depends on latitude
        double lngDelta = radiusKm / (111.32 * Math.cos(Math.toRadians(pickupLat)));

        double minLat = pickupLat - latDelta;
        double maxLat = pickupLat + latDelta;
        double minLng = pickupLng - lngDelta;
        double maxLng = pickupLng + lngDelta;

        String sql = """
            SELECT id, lat, lng, status, created_at, updated_at
            FROM drivers
            WHERE status = 'ONLINE'
              AND lat BETWEEN :minLat AND :maxLat
              AND lng BETWEEN :minLng AND :maxLng
              AND (6371 * acos(
                  cos(radians(:pickupLat)) * cos(radians(lat)) * cos(radians(lng) - radians(:pickupLng)) +
                  sin(radians(:pickupLat)) * sin(radians(lat))
              )) <= :radiusKm
            ORDER BY (
                6371 * acos(
                    cos(radians(:pickupLat)) * cos(radians(lat)) * cos(radians(lng) - radians(:pickupLng)) +
                    sin(radians(:pickupLat)) * sin(radians(lat))
                )
            ) ASC
            LIMIT 1
            """;

        return jdbcClient.sql(sql)
                .param("minLat", minLat)
                .param("maxLat", maxLat)
                .param("minLng", minLng)
                .param("maxLng", maxLng)
                .param("pickupLat", pickupLat)
                .param("pickupLng", pickupLng)
                .param("radiusKm", radiusKm)
                .query(DriverRecord.class)
                .optional();
    }

    public record DriverRecord(
            UUID id,
            double lat,
            double lng,
            String status,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt
    ) {}
}
