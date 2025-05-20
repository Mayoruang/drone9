package com.huang.backend.drone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for drone authentication response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneAuthResponseDto {
    
    /**
     * The drone ID
     */
    private String droneId;
    
    /**
     * The authentication token
     */
    private String token;
    
    /**
     * Token expiration time in milliseconds
     */
    private long expiresIn;
    
    /**
     * MQTT broker connection information
     */
    private String mqttBrokerUrl;
    private String mqttUsername;
    private String mqttPassword;
    private String mqttTopicTelemetry;
    private String mqttTopicCommands;
    private String mqttTopicResponses;
} 