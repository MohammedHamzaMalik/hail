package com.hamza.hail.ride;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class RideRepository {

    private final JdbcClient jdbcClient;

    public RideRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int save(UUID id, double pickupLat, double pickupLng, String status, String idempotencyKey, String origin) {
        return jdbcClient.sql("""
                INSERT INTO rides (id, pickup_lat, pickup_lng, status, idempotency_key, origin, created_at, updated_at)
                VALUES (:id, :pickupLat, :pickupLng, :status, :idempotencyKey, :origin, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .param("id", id)
                .param("pickupLat", pickupLat)
                .param("pickupLng", pickupLng)
                .param("status", status)
                .param("idempotencyKey", idempotencyKey)
                .param("origin", origin)
                .update();
    }

    public Optional<RideRecord> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, driver_id, pickup_lat, pickup_lng, status, idempotency_key, origin, created_at, updated_at
                FROM rides WHERE id = :id
                """)
                .param("id", id)
                .query(RideRecord.class)
                .optional();
    }

    public int assignDriver(UUID rideId, UUID driverId) {
        return jdbcClient.sql("""
                UPDATE rides
                SET driver_id = :driverId, status = 'ASSIGNED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :rideId AND status = 'REQUESTED'
                """)
                .param("driverId", driverId)
                .param("rideId", rideId)
                .update();
    }

    public record RideRecord(
            UUID id,
            UUID driverId,
            double pickupLat,
            double pickupLng,
            RideStatus status,
            String idempotencyKey,
            String origin,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt
    ) {}
}
