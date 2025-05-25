package com.huang.backend.geofence.service;

import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.exception.BusinessException;
import com.huang.backend.exception.ResourceNotFoundException;
import com.huang.backend.geofence.dto.*;
import com.huang.backend.geofence.entity.Geofence;
import com.huang.backend.geofence.entity.GeofenceViolation;
import com.huang.backend.geofence.mapper.GeofenceMapper;
import com.huang.backend.geofence.mapper.ViolationMapper;
import com.huang.backend.geofence.repository.GeofenceRepository;
import com.huang.backend.geofence.repository.GeofenceViolationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of GeofenceService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceServiceImpl implements GeofenceService {

    private final GeofenceRepository geofenceRepository;
    private final DroneRepository droneRepository;
    private final GeofenceViolationRepository violationRepository;
    private final GeofenceMapper geofenceMapper;
    private final ViolationMapper violationMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    @Override
    @Transactional
    public GeofenceResponseDto createGeofence(GeofenceCreateDto createDto, String username) {
        log.info("Creating new geofence with name: {}", createDto.getName());
        
        // Create new geofence entity from DTO
        Geofence geofence = geofenceMapper.toEntity(createDto);
        geofence.setCreatedBy(username);
        
        // Save the geofence
        Geofence savedGeofence = geofenceRepository.save(geofence);
        
        // Bind drones if provided
        if (createDto.getDroneIds() != null && !createDto.getDroneIds().isEmpty()) {
            bindDronesToGeofence(savedGeofence, createDto.getDroneIds());
        }
        
        // Trigger thumbnail generation asynchronously
        generateThumbnail(savedGeofence.getGeofenceId());
        
        return GeofenceResponseDto.builder()
                .success(true)
                .message("地理围栏创建成功")
                .geofenceId(savedGeofence.getGeofenceId())
                .build();
    }

    @Override
    public Page<GeofenceListItemDto> getGeofences(Pageable pageable) {
        log.debug("Fetching geofences page: {}", pageable);
        return geofenceRepository.findAll(pageable)
                .map(geofenceMapper::toListItemDto);
    }

    @Override
    public Page<GeofenceListItemDto> searchGeofences(Geofence.GeofenceType type, Boolean active, String search, Pageable pageable) {
        log.debug("Searching geofences with filters - type: {}, active: {}, search: {}", type, active, search);
        return geofenceRepository.searchGeofences(type, active, search, pageable)
                .map(geofenceMapper::toListItemDto);
    }

    @Override
    public GeofenceDetailDto getGeofenceDetail(UUID geofenceId) {
        log.debug("Fetching details for geofence: {}", geofenceId);
        Geofence geofence = geofenceRepository.findByIdWithDrones(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
        
        return geofenceMapper.toDetailDto(geofence);
    }

    @Override
    @Transactional
    public GeofenceResponseDto updateGeofence(UUID geofenceId, GeofenceCreateDto updateDto, String username) {
        log.info("Updating geofence: {}", geofenceId);
        
        // Find existing geofence
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
        
        // Update fields from DTO
        geofenceMapper.updateEntityFromDto(geofence, updateDto);
        geofence.setUpdatedBy(username);
        
        // Save updated geofence
        Geofence updatedGeofence = geofenceRepository.save(geofence);
        
        // Update drone associations if provided
        if (updateDto.getDroneIds() != null) {
            // Clear existing associations
            updatedGeofence.getDrones().clear();
            
            // Bind new drones
            if (!updateDto.getDroneIds().isEmpty()) {
                bindDronesToGeofence(updatedGeofence, updateDto.getDroneIds());
            }
        }
        
        // Regenerate thumbnail if geometry changed
        generateThumbnail(updatedGeofence.getGeofenceId());
        
        return GeofenceResponseDto.builder()
                .success(true)
                .message("地理围栏更新成功")
                .geofenceId(updatedGeofence.getGeofenceId())
                .build();
    }

    @Override
    @Transactional
    public GeofenceResponseDto deleteGeofence(UUID geofenceId) {
        log.info("Deleting geofence: {}", geofenceId);
        
        // Check if geofence exists
        if (!geofenceRepository.existsById(geofenceId)) {
            throw new ResourceNotFoundException("未找到指定地理围栏");
        }
        
        // Delete the geofence
        geofenceRepository.deleteById(geofenceId);
        
        return GeofenceResponseDto.builder()
                .success(true)
                .message("地理围栏删除成功")
                .geofenceId(geofenceId)
                .build();
    }

    @Override
    @Transactional
    public GeofenceResponseDto bindDrones(UUID geofenceId, GeofenceDroneBindDto bindDto) {
        log.info("Binding {} drones to geofence: {}", bindDto.getDroneIds().size(), geofenceId);
        
        // Find geofence
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
        
        // Bind drones
        bindDronesToGeofence(geofence, bindDto.getDroneIds());
        
        return GeofenceResponseDto.builder()
                .success(true)
                .message("无人机绑定成功")
                .geofenceId(geofenceId)
                .build();
    }

    @Override
    @Transactional
    public GeofenceResponseDto unbindDrone(UUID geofenceId, UUID droneId) {
        log.info("Unbinding drone {} from geofence: {}", droneId, geofenceId);
        
        // Find geofence with drones
        Geofence geofence = geofenceRepository.findByIdWithDrones(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
        
        // Find drone
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定无人机"));
        
        // Remove the association
        geofence.getDrones().remove(drone);
        geofenceRepository.save(geofence);
        
        return GeofenceResponseDto.builder()
                .success(true)
                .message("无人机解绑成功")
                .geofenceId(geofenceId)
                .build();
    }

    @Override
    public List<GeofenceListItemDto> findGeofencesContainingPoint(Point point) {
        log.debug("Finding geofences containing point: {}", point);
        return geofenceRepository.findGeofencesContainingPoint(point).stream()
                .map(geofenceMapper::toListItemDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<GeofenceListItemDto> findGeofencesContainingPoint(Double longitude, Double latitude) {
        log.debug("Finding geofences containing point: {}, {}", longitude, latitude);
        
        // Create point geometry
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        
        return findGeofencesContainingPoint(point);
    }

    @Override
    public Page<ViolationRecordDto> getGeofenceViolations(UUID geofenceId, Pageable pageable) {
        log.debug("Fetching violations for geofence: {}", geofenceId);
        
        // Verify geofence exists
        if (!geofenceRepository.existsById(geofenceId)) {
            throw new ResourceNotFoundException("未找到指定地理围栏");
        }
        
        return violationRepository.findByGeofence_GeofenceIdOrderByViolationTimeDesc(geofenceId, pageable)
                .map(violationMapper::toDto);
    }

    @Override
    @Transactional
    public void resolveViolation(UUID violationId, String notes, String resolvedBy) {
        log.info("Resolving violation: {}", violationId);
        
        GeofenceViolation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定违规记录"));
        
        if (violation.getResolved()) {
            throw new BusinessException("违规记录已处理");
        }
        
        violation.setResolved(true);
        violation.setResolvedAt(ZonedDateTime.now());
        violation.setResolvedBy(resolvedBy);
        violation.setNotes(notes);
        
        violationRepository.save(violation);
        
        log.info("Violation {} resolved by {}", violationId, resolvedBy);
    }

    @Override
    @Async
    public void generateThumbnail(UUID geofenceId) {
        log.info("Generating thumbnail for geofence: {}", geofenceId);
        
        try {
            // Find geofence
            Geofence geofence = geofenceRepository.findById(geofenceId)
                    .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
            
            // Extract center point from geometry
            Point centerPoint = geofence.getCenterPoint();
            if (centerPoint == null && geofence.getGeom() != null) {
                // Calculate center if not stored
                centerPoint = geofence.getGeom().getCentroid();
            }
            
            if (centerPoint != null) {
                double lat = centerPoint.getY();
                double lng = centerPoint.getX();
                
                // Calculate appropriate zoom level based on geofence area
                int zoom = calculateOptimalZoom(geofence);
                
                // Generate Baidu Maps Static API URL with actual geofence overlay
                String thumbnailUrl = String.format(
                    "https://api.map.baidu.com/staticimage/v2?" +
                    "ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&" +
                    "center=%.6f,%.6f&" +
                    "width=300&height=200&zoom=%d&scale=2&" +
                    "coordtype=wgs84ll&markers=%.6f,%.6f",
                    lng, lat, zoom, lng, lat
                );
                
                // Update the thumbnail URL
                geofence.setThumbnailUrl(thumbnailUrl);
                geofenceRepository.save(geofence);
                
                log.info("Thumbnail generated for geofence: {} with URL: {}", geofenceId, thumbnailUrl);
            } else {
                log.warn("Could not generate thumbnail for geofence {} - no center point available", geofenceId);
            }
        } catch (Exception e) {
            log.error("Error generating thumbnail for geofence: {}", geofenceId, e);
        }
    }
    
    /**
     * Calculate optimal zoom level based on geofence area
     */
    private int calculateOptimalZoom(Geofence geofence) {
        if (geofence.getAreaSquareMeters() != null) {
            double area = geofence.getAreaSquareMeters();
            
            // Area-based zoom calculation
            if (area > 10_000_000) return 10;      // > 10 km²
            if (area > 1_000_000) return 12;       // > 1 km²
            if (area > 100_000) return 14;         // > 0.1 km²
            if (area > 10_000) return 16;          // > 0.01 km²
            return 18;                             // smaller areas
        }
        
        // Default zoom for unknown area
        return 15;
    }
    
    /**
     * Helper method to bind drones to a geofence
     */
    private void bindDronesToGeofence(Geofence geofence, List<UUID> droneIds) {
        // Find all drones by IDs
        List<Drone> drones = droneRepository.findAllById(droneIds);
        
        // Check if all drones were found
        if (drones.size() != droneIds.size()) {
            throw new BusinessException("部分无人机ID无效");
        }
        
        // Add drones to geofence
        Set<Drone> droneSet = geofence.getDrones();
        droneSet.addAll(drones);
        
        // Save the updated geofence
        geofenceRepository.save(geofence);
    }
} 