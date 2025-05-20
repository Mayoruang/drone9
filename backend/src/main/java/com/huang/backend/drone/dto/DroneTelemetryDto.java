package com.huang.backend.drone.dto;

import com.huang.backend.drone.model.TelemetryData;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * DTO for drone telemetry data
 * Extends the common TelemetryData model.
 */
@Data
@SuperBuilder
public class DroneTelemetryDto extends TelemetryData {
    
    /**
     * Any DTO-specific fields can be added here
     */
} 