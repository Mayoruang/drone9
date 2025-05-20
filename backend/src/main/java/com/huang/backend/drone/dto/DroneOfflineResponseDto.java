package com.huang.backend.drone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for drone offline responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneOfflineResponseDto {
    
    /**
     * The ID of the drone that was taken offline
     */
    private UUID droneId;
    
    /**
     * The serial number of the drone
     */
    private String serialNumber;
    
    /**
     * The result of the operation
     */
    private boolean success;
    
    /**
     * Message describing the result
     */
    private String message;
    
    /**
     * The reason for taking the drone offline
     */
    private String reason;
    
    /**
     * The time when the drone was taken offline
     */
    private ZonedDateTime offlineAt;
    
    /**
     * Whether the command was sent to the drone via MQTT
     */
    private boolean commandSent;
} 