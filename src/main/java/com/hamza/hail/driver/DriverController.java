package com.hamza.hail.driver;

import com.hamza.hail.driver.dto.UpdateLocationRequest;
import com.hamza.hail.driver.dto.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/drivers")
public class DriverController {

    private final DriverRepository driverRepository;

    public DriverController(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    // Convenience endpoint for setting up test data and mock drivers
    @Operation(summary = "Register a new driver", description = "Creates a driver record with initial coordinates and status.")
    @ApiResponse(responseCode = "201", description = "Driver successfully registered")
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerDriver(@RequestBody @Valid RegisterDriverRequest request) {
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        driverRepository.save(id, request.lat(), request.lng(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", request.status()));
    }

    @Operation(summary = "Update driver availability status", description = "Sets status to OFFLINE, ONLINE, or BUSY.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateStatusRequest request) {
        int updated = driverRepository.updateStatus(id, request.status());
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/location")
    public ResponseEntity<Void> updateLocation(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateLocationRequest request) {
        int updated = driverRepository.updateLocation(id, request.lat(), request.lng());
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    public record RegisterDriverRequest(
            UUID id,
            double lat,
            double lng,
            String status
    ) {}
}
