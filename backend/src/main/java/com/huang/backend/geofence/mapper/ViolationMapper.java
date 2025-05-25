package com.huang.backend.geofence.mapper;

import com.huang.backend.geofence.dto.ViolationRecordDto;
import com.huang.backend.geofence.entity.GeofenceViolation;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

/**
 * Mapper for GeofenceViolation entity and ViolationRecordDto
 */
@Component
public class ViolationMapper {

    public ViolationRecordDto toDto(GeofenceViolation violation) {
        if (violation == null) {
            return null;
        }

        return ViolationRecordDto.builder()
                .violationId(violation.getViolationId())
                .geofenceId(violation.getGeofence() != null ? violation.getGeofence().getGeofenceId() : null)
                .geofenceName(violation.getGeofence() != null ? violation.getGeofence().getName() : null)
                .droneId(violation.getDrone() != null ? violation.getDrone().getDroneId() : null)
                .droneSerialNumber(violation.getDrone() != null ? violation.getDrone().getSerialNumber() : null)
                .droneModel(violation.getDrone() != null ? violation.getDrone().getModel() : null)
                .violationType(violation.getViolationType())
                .severity(violation.getSeverity())
                .violationTime(violation.getViolationTime())
                .violationPoint(pointToGeoPointDto(violation.getViolationPoint()))
                .altitude(violation.getAltitude())
                .resolved(violation.getResolved())
                .resolvedAt(violation.getResolvedAt())
                .resolvedBy(violation.getResolvedBy())
                .notes(violation.getNotes())
                .build();
    }

    private ViolationRecordDto.GeoPointDto pointToGeoPointDto(Point point) {
        if (point == null) {
            return null;
        }
        return ViolationRecordDto.GeoPointDto.builder()
                .longitude(point.getX())
                .latitude(point.getY())
                .build();
    }
} 