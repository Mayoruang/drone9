package com.huang.backend.drone.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;

/**
 * Common model for drone telemetry data used across the system.
 * This is the central definition of telemetry data structure.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryData {
    
    /**
     * Drone identifier (UUID)
     */
    @JsonAlias({"drone_id"})
    private String droneId;
    
    /**
     * Timestamp of when the data was recorded on the drone
     */
    private Instant timestamp;
    
    /**
     * Battery level in percentage (0-100)
     */
    @JsonAlias({"battery_percentage", "battery_level"})
    private Double batteryLevel;
    
    /**
     * Battery voltage in volts
     */
    private Double batteryVoltage;
    
    /**
     * Current latitude position
     */
    private Double latitude;
    
    /**
     * Current longitude position
     */
    private Double longitude;
    
    /**
     * Altitude in meters
     */
    private Double altitude;
    
    /**
     * Speed in meters per second
     */
    private Double speed;
    
    /**
     * Heading/direction in degrees (0-359)
     */
    private Double heading;
    
    /**
     * Satellite count used for GPS fix
     */
    private Integer satellites;
    
    /**
     * Signal strength in percentage (0-100)
     */
    private Double signalStrength;
    
    /**
     * Flight mode (e.g., HOVER, RTL, MISSION)
     */
    private String flightMode;
    
    /**
     * Temperature of the drone in Celsius
     */
    private Double temperature;
    
    /**
     * Drone status (OFFLINE, ONLINE, FLYING, IDLE, ERROR)
     * This is used to directly receive status information from the drone
     */
    @JsonAlias({"flight_status"})
    private String status;
    
    /**
     * Whether the drone is armed (ready for flight)
     */
    private Boolean isArmed;
} 