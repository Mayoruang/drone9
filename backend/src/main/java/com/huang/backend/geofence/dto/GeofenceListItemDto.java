package com.huang.backend.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for returning basic geofence information in list views
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceListItemDto {
    
    private UUID geofenceId;
    private String name;
    private String description;
    
    /**
     * Center point coordinates [longitude, latitude]
     */
    private double[] center;
    
    /**
     * Simplified geometry for thumbnail display
     * Format: {"type": "Polygon", "coordinates": [[[lng1, lat1], [lng2, lat2], ...]]}
     */
    private Object geometry;
    
    /**
     * Geofence type
     */
    private String geofenceType;
    
    /**
     * URL to static map thumbnail image
     */
    private String thumbnailUrl;
    
    /**
     * Whether geofence is currently active
     */
    private boolean active;
    
    /**
     * Altitude restrictions
     */
    private Double altitudeMin;
    private Double altitudeMax;
    
    /**
     * Priority level
     */
    private Integer priority;
    
    /**
     * Area in square meters
     */
    private Double areaSquareMeters;
    
    /**
     * Number of associated drones (only applicable for RESTRICTED_ZONE)
     */
    private int droneCount;
    
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
} 