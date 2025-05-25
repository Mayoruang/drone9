package com.huang.backend.geofence.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO for binding drones to a geofence
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceDroneBindDto {
    
    @NotEmpty(message = "无人机ID列表不能为空")
    private List<UUID> droneIds;
} 