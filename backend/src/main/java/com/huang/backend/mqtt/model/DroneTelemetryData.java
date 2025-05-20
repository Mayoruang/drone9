package com.huang.backend.mqtt.model;

import com.huang.backend.drone.model.TelemetryData;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Model class for drone telemetry data received via MQTT.
 * Extends the common TelemetryData model.
 */
@Data
@SuperBuilder
public class DroneTelemetryData extends TelemetryData {
    
    /**
     * Any MQTT-specific telemetry fields can be added here
     */
    
    /**
     * The MQTT topic from which this data was received
     */
    private String sourceTopic;
    
    /**
     * Default constructor needed for Jackson deserialization
     */
    public DroneTelemetryData() {
        super();
    }
} 