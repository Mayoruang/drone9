package com.huang.backend.mqtt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model representing a farewell message sent by a drone before going offline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarewellMessage {
    
    /**
     * The type of message - always "FAREWELL"
     */
    private String type;
    
    /**
     * The ID of the drone
     */
    private String droneId;
    
    /**
     * The serial number of the drone
     */
    private String serialNumber;
    
    /**
     * The farewell message
     */
    private String message;
    
    /**
     * The timestamp when the message was sent
     */
    private Instant timestamp;
    
    /**
     * The user who issued the offline command
     */
    private String issuedBy;
    
    /**
     * The battery level when the drone went offline
     */
    private Double batteryRemaining;
} 