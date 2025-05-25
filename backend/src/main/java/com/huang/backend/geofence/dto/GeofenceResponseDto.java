package com.huang.backend.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Standard response DTO for geofence operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceResponseDto {
    
    private boolean success;
    private String message;
    private UUID geofenceId;
} 