package com.hamza.hail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {

    // Spring Boot 3.1+ feature: auto-wires DB credentials and URL from the container
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void containerStartsAndFlywayMigrates() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void canInsertAndReadDriver() {
        UUID driverId = UUID.randomUUID();

        int inserted = jdbcClient.sql("""
            INSERT INTO drivers (id, lat, lng, status)
            VALUES (:id, :lat, :lng, :status)
            """)
                .param("id", driverId)
                .param("lat", 40.7128)
                .param("lng", -74.0060)
                .param("status", "OFFLINE")
                .update();

        assertThat(inserted).isEqualTo(1);

        String status = jdbcClient.sql("SELECT status FROM drivers WHERE id = :id")
                .param("id", driverId)
                .query(String.class)
                .single();

        assertThat(status).isEqualTo("OFFLINE");
    }
}