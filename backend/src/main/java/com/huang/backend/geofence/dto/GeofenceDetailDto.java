package com.huang.backend.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for transferring complete geofence data including associated drones
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceDetailDto {
    
    private UUID geofenceId;
    private String name;
    private String description;
    
    /**
     * GeoJSON-formatted geometry
     * Format: {"type": "Polygon", "coordinates": [[[lng1, lat1], [lng2, lat2], ...]]}
     */
    private Object geometry;
    
    /**
     * Geofence type
     */
    private String geofenceType;
    
    private String thumbnailUrl;
    private boolean active;
    private Double altitudeMin;
    private Double altitudeMax;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String createdBy;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    
    /**
     * Associated drone information
     */
    private List<DroneMiniDto> drones;
    
    /**
     * Minimal drone information for association with geofences
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DroneMiniDto {
        private UUID droneId;
        private String serialNumber;
        private String model;
    }
} 