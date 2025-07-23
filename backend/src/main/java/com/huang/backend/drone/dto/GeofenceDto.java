package com.huang.backend.drone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for transferring geofence data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceDto {
    
    private UUID geofenceId;
    private String name;
    private String description;
    
    /**
     * List of coordinates forming the polygon boundary
     * Format: [[lng1, lat1], [lng2, lat2], ...]
     */
    private List<List<Double>> coordinates;
    
    private boolean active;
    private Double altitudeMin;
    private Double altitudeMax;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String createdBy;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    
    /**
     * List of drone IDs assigned to this geofence
     */
    private List<UUID> droneIds;
} 