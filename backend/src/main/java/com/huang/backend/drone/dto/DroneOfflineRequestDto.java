package com.huang.backend.drone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for drone offline requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneOfflineRequestDto {
    
    /**
     * The ID of the drone to take offline
     */
    @NotNull(message = "Drone ID is required")
    private UUID droneId;
    
    /**
     * The reason for taking the drone offline
     */
    @NotBlank(message = "Reason is required")
    @Size(min = 5, max = 255, message = "Reason must be between 5 and 255 characters")
    private String reason;
    
    /**
     * Grace period in seconds before the drone should disconnect (optional)
     * Default is 10 seconds
     */
    private int gracePeriodSeconds = 10;
} 