package com.huang.backend.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for geofence statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceStatisticsDto {
    
    private Long totalCount;
    private Long noFlyZoneCount;
    private Long flyZoneCount;
    private Long restrictedZoneCount;
    private Long activeCount;
    private Long violationCount;
    private Double totalArea;
    private List<GeofenceTypeStatDto> typeStatistics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeofenceTypeStatDto {
        private String type;
        private Long count;
        private Double totalArea;
        private Long violationCount;
    }
} 