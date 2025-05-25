package com.huang.backend.geofence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating or updating a geofence
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceCreateDto {
    
    @NotBlank(message = "名称不能为空")
    private String name;
    
    private String description;
    
    /**
     * GeoJSON-formatted polygon geometry
     * Format: {"type": "Polygon", "coordinates": [[[lng1, lat1], [lng2, lat2], ...]]}
     */
    @NotNull(message = "地理围栏边界不能为空")
    private Object geometry;
    
    /**
     * Type of the geofence
     */
    @NotNull(message = "地理围栏类型不能为空")
    private String geofenceType = "NO_FLY_ZONE";
    
    /**
     * Whether this geofence is active and should be enforced
     */
    private boolean active = true;
    
    /**
     * Minimum altitude boundary in meters (optional)
     */
    private Double altitudeMin;
    
    /**
     * Maximum altitude boundary in meters (optional)
     */
    private Double altitudeMax;
    
    /**
     * When this geofence becomes active (optional)
     */
    private ZonedDateTime startTime;
    
    /**
     * When this geofence expires (optional)
     */
    private ZonedDateTime endTime;
    
    /**
     * List of drone IDs to associate with this geofence
     */
    private List<UUID> droneIds;
} 