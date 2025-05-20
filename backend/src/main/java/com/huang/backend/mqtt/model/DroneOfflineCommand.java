package com.huang.backend.mqtt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Command to set a drone offline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneOfflineCommand {
    
    /**
     * The type of command
     */
    private final String type = "OFFLINE_COMMAND";
    
    /**
     * The reason for taking the drone offline
     */
    private String reason;
    
    /**
     * The time when the command was issued
     */
    private ZonedDateTime timestamp;
    
    /**
     * The user who issued the command
     */
    private String issuedBy;
    
    /**
     * Grace period in seconds before the drone should disconnect
     */
    private int gracePeriodSeconds;
} 