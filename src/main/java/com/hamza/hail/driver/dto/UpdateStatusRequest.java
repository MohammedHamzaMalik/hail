package com.hamza.hail.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateStatusRequest (
        @NotBlank(message = "Status cannot be blank")
        @Pattern(regexp = "OFFLINE|ONLINE|BUSY", message = "Status must be OFFLINE, ONLINE, or BUSY")
        String status
) {}
