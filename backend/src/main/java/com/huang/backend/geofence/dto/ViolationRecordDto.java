package com.huang.backend.geofence.dto;

import com.huang.backend.geofence.entity.GeofenceViolation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for violation records
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationRecordDto {
    
    private UUID violationId;
    private UUID geofenceId;
    private String geofenceName;
    private UUID droneId;
    private String droneSerialNumber;
    private String droneModel;
    private GeofenceViolation.ViolationType violationType;
    private GeofenceViolation.Severity severity;
    private ZonedDateTime violationTime;
    private GeoPointDto violationPoint;
    private Double altitude;
    private Boolean resolved;
    private ZonedDateTime resolvedAt;
    private String resolvedBy;
    private String notes;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoPointDto {
        private Double longitude;
        private Double latitude;
    }
} 