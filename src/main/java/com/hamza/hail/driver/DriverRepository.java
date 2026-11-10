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

    public Optional<DriverRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT id, lat, lng, status, created_at, updated_at FROM drivers WHERE id = :id")
                .param("id", id)
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
