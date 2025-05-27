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
        
        // 新增：验证地理围栏类型和无人机绑定的合法性
        if (createDto.getDroneIds() != null && !createDto.getDroneIds().isEmpty()) {
            Geofence.GeofenceType geofenceType;
            try {
                geofenceType = Geofence.GeofenceType.valueOf(createDto.getGeofenceType());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("无效的地理围栏类型: " + createDto.getGeofenceType());
            }
            
            if (geofenceType != Geofence.GeofenceType.RESTRICTED_ZONE) {
                String typeName = getGeofenceTypeName(geofenceType);
                throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
            }
        }
        
        // Create new geofence entity from DTO
        Geofence geofence = geofenceMapper.toEntity(createDto);
        geofence.setCreatedBy(username);
        
        // Save the geofence
        Geofence savedGeofence = geofenceRepository.save(geofence);
        
        // Bind drones if provided and geofence type allows it
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
        
        // 新增：验证地理围栏类型和无人机绑定的合法性
        if (updateDto.getDroneIds() != null && !updateDto.getDroneIds().isEmpty()) {
            Geofence.GeofenceType geofenceType;
            try {
                geofenceType = Geofence.GeofenceType.valueOf(updateDto.getGeofenceType());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("无效的地理围栏类型: " + updateDto.getGeofenceType());
            }
            
            if (geofenceType != Geofence.GeofenceType.RESTRICTED_ZONE) {
                String typeName = getGeofenceTypeName(geofenceType);
                throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
            }
        }
        
        // Update fields from DTO
        geofenceMapper.updateEntityFromDto(geofence, updateDto);
        geofence.setUpdatedBy(username);
        
        // Save updated geofence
        Geofence updatedGeofence = geofenceRepository.save(geofence);
        
        // Update drone associations if provided
        if (updateDto.getDroneIds() != null) {
            // 验证更新后的地理围栏类型是否允许关联无人机
            if (!updateDto.getDroneIds().isEmpty() && 
                updatedGeofence.getGeofenceType() != Geofence.GeofenceType.RESTRICTED_ZONE) {
                String typeName = getGeofenceTypeName(updatedGeofence.getGeofenceType());
                throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
            }
            
            // Clear existing associations
            updatedGeofence.getDrones().clear();
            
            // Bind new drones only if the list is not empty
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
        
        // Find geofence with drones
        Geofence geofence = geofenceRepository.findByIdWithDrones(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("未找到指定地理围栏"));
        
        // 新增：验证地理围栏类型，只有限制区可以绑定无人机
        if (geofence.getGeofenceType() != Geofence.GeofenceType.RESTRICTED_ZONE) {
            String typeName = getGeofenceTypeName(geofence.getGeofenceType());
            throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
        }
        
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
        
        // 新增：验证地理围栏类型，只有限制区可以解绑无人机
        if (geofence.getGeofenceType() != Geofence.GeofenceType.RESTRICTED_ZONE) {
            String typeName = getGeofenceTypeName(geofence.getGeofenceType());
            throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
        }
        
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
        try {
            log.info("Starting to bind drones {} to geofence {}", droneIds, geofence.getGeofenceId());
            
            // 新增：检查地理围栏类型，只有限制区可以绑定无人机
            if (geofence.getGeofenceType() != Geofence.GeofenceType.RESTRICTED_ZONE) {
                String typeName = getGeofenceTypeName(geofence.getGeofenceType());
                log.warn("Cannot bind drones to geofence type: {}", geofence.getGeofenceType());
                throw new BusinessException(String.format("%s不需要关联无人机，只有限制区才能关联无人机", typeName));
            }
            
            // 优化：一次性查询所有需要的无人机，避免 N+1 查询问题
            List<Drone> drones = droneRepository.findAllById(droneIds);
            log.info("Found {} drones out of {} requested", drones.size(), droneIds.size());
            
            // Check if all drones were found
            if (drones.size() != droneIds.size()) {
                // 找出缺失的无人机ID
                Set<UUID> foundIds = drones.stream()
                    .map(Drone::getDroneId)
                    .collect(Collectors.toSet());
                Set<UUID> missingIds = droneIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
                
                log.error("Missing drone IDs: {}. Requested: {}, Found: {}", missingIds, droneIds.size(), drones.size());
                throw new BusinessException("部分无人机ID无效: " + missingIds);
            }
            
            // 优化：避免重复查询，直接使用已有的 geofence 对象
            Set<Drone> droneSet = geofence.getDrones();
            log.info("Current drone set size: {}", droneSet.size());
            
            // 批量添加无人机，避免逐个处理
            droneSet.addAll(drones);
            log.info("After adding, drone set size: {}", droneSet.size());
            
            // 优化：使用 @Modifying 查询或批量操作来减少数据库交互
            geofenceRepository.save(geofence);
            log.info("Successfully saved geofence with bound drones");
            
        } catch (Exception e) {
            log.error("Error binding drones to geofence: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取地理围栏类型的中文名称
     */
    private String getGeofenceTypeName(Geofence.GeofenceType type) {
        switch (type) {
            case NO_FLY_ZONE:
                return "禁飞区";
            case FLY_ZONE:
                return "允飞区";
            case RESTRICTED_ZONE:
                return "限制区";
            default:
                return "未知类型";
        }
    }
} 