package com.huang.backend.drone.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO for drone deletion response
 */
@Data
@Builder
public class DroneDeleteResponseDto {
    private UUID droneId;
    private String serialNumber;
    private boolean success;
    private String message;
} 