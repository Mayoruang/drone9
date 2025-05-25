package com.huang.backend.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for geofence overview data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceOverviewDto {
    
    private Map<String, Long> dailyViolations;
    private Map<String, Long> monthlyTrends;
    private List<TopViolationGeofenceDto> topViolationGeofences;
    private List<RecentActivityDto> recentActivities;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopViolationGeofenceDto {
        private UUID geofenceId;
        private String name;
        private String type;
        private Long violationCount;
        private ZonedDateTime lastViolationTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityDto {
        private String activityType;
        private String description;
        private ZonedDateTime timestamp;
        private String operator;
        private UUID relatedId;
    }
} 