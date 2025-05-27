package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.drone.controller.DroneController.GeofenceAssignmentResponse;
import com.huang.backend.geofence.dto.GeofenceListItemDto;
import com.huang.backend.geofence.entity.Geofence;
import com.huang.backend.geofence.mapper.GeofenceMapper;
import com.huang.backend.geofence.repository.GeofenceRepository;
import com.huang.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the DroneStatusService interface
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneStatusServiceImpl implements DroneStatusService {

    private final DroneRepository droneRepository;
    private final DroneInfluxDBService influxDBService;
    private final GeofenceRepository geofenceRepository;
    private final GeofenceMapper geofenceMapper;
    
    @Override
    public List<DroneStatusDto> getAllDronesStatus() {
        log.debug("Getting status for all drones");
        List<Drone> drones = droneRepository.findAll();
        return drones.stream()
                .map(this::enrichDroneStatusWithTelemetry)
                .collect(Collectors.toList());
    }

    @Override
    public DroneStatusDto getDroneStatus(UUID droneId) {
        log.debug("Getting status for drone: {}", droneId);
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        return enrichDroneStatusWithTelemetry(drone);
    }

    @Override
    public List<DroneStatusDto> getDronesByStatus(Drone.DroneStatus status) {
        log.debug("Getting drones with status: {}", status);
        List<Drone> drones;
        
        if (status == null) {
            drones = droneRepository.findAll();
        } else {
            drones = droneRepository.findByCurrentStatus(status);
        }
        
        return drones.stream()
                .map(this::enrichDroneStatusWithTelemetry)
                .collect(Collectors.toList());
    }

    @Override
    public DroneTelemetryDto getLatestTelemetry(UUID droneId) {
        log.debug("Getting latest telemetry for drone: {}", droneId);
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        // 使用UUID而不是序列号来查询InfluxDB，因为存储时使用的是UUID
        return influxDBService.getLatestTelemetry(drone.getDroneId().toString());
    }

    @Override
    public List<DroneTelemetryDto> getTelemetryHistory(UUID droneId, Instant start, Instant end, int limit) {
        log.debug("Getting telemetry history for drone: {}, from {} to {}, limit: {}", 
                droneId, start, end, limit);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        // 使用UUID而不是序列号来查询InfluxDB，因为存储时使用的是UUID
        return influxDBService.getTelemetryHistory(drone.getDroneId().toString(), start, end, limit);
    }
    
    /**
     * Enrich a drone status DTO with telemetry data
     * 
     * @param drone the drone entity
     * @return the enriched drone status DTO
     */
    private DroneStatusDto enrichDroneStatusWithTelemetry(Drone drone) {
        DroneStatusDto dto = DroneStatusDto.fromEntity(drone);
        
        try {
            // Get latest telemetry to enhance the status
            // 使用UUID而不是序列号来查询InfluxDB，因为存储时使用的是UUID
            DroneTelemetryDto telemetry = influxDBService.getLatestTelemetry(drone.getDroneId().toString());
            
            if (telemetry != null) {
                dto.setBatteryLevel(telemetry.getBatteryLevel());
                dto.setLatitude(telemetry.getLatitude());
                dto.setLongitude(telemetry.getLongitude());
                dto.setAltitude(telemetry.getAltitude());
                log.debug("成功获取无人机{}({})的遥测数据，电量: {}%", 
                        drone.getSerialNumber(), drone.getDroneId(), telemetry.getBatteryLevel());
            } else {
                log.debug("未找到无人机{}({})的遥测数据", 
                        drone.getSerialNumber(), drone.getDroneId());
            }
        } catch (Exception e) {
            log.warn("Error enriching drone status with telemetry for drone {}: {}", 
                    drone.getSerialNumber(), e.getMessage());
            // Continue without telemetry data
        }
        
        return dto;
    }

    // ============================================================================
    // 地理围栏相关方法实现
    // ============================================================================

    @Override
    public List<GeofenceListItemDto> getDroneGeofences(UUID droneId) {
        log.debug("Getting geofences for drone: {}", droneId);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("Drone not found with ID: " + droneId));
        
        List<Geofence> activeGeofences = geofenceRepository.findActiveGeofencesForDrone(droneId);
        
        return activeGeofences.stream()
                .map(geofenceMapper::toListItemDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<GeofenceListItemDto> getAvailableGeofences(String type, Boolean active) {
        log.debug("Getting available geofences with type: {}, active: {}", type, active);
        
        List<Geofence> geofences;
        
        if (type != null && !type.trim().isEmpty()) {
            try {
                Geofence.GeofenceType geofenceType = Geofence.GeofenceType.valueOf(type.toUpperCase());
                if (active != null && active) {
                    geofences = geofenceRepository.findByGeofenceTypeAndActiveTrue(geofenceType);
                } else {
                    geofences = geofenceRepository.findAll().stream()
                            .filter(g -> g.getGeofenceType() == geofenceType)
                            .collect(Collectors.toList());
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid geofence type: {}", type);
                geofences = new ArrayList<>();
            }
        } else {
            if (active != null && active) {
                geofences = geofenceRepository.findByActiveTrue();
            } else {
                geofences = geofenceRepository.findAll();
            }
        }
        
        return geofences.stream()
                .map(geofenceMapper::toListItemDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GeofenceAssignmentResponse assignGeofences(UUID droneId, List<UUID> geofenceIds) {
        log.info("Assigning geofences {} to drone: {}", geofenceIds, droneId);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("Drone not found with ID: " + droneId));
        
        List<UUID> successfulAssignments = new ArrayList<>();
        List<UUID> failedAssignments = new ArrayList<>();
        
        for (UUID geofenceId : geofenceIds) {
            try {
                Geofence geofence = geofenceRepository.findById(geofenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with ID: " + geofenceId));
                
                // 检查是否已经分配
                if (!drone.getGeofences().contains(geofence)) {
                    drone.getGeofences().add(geofence);
                    geofence.getDrones().add(drone);
                    successfulAssignments.add(geofenceId);
                    log.debug("Successfully assigned geofence {} to drone {}", geofenceId, droneId);
                } else {
                    log.debug("Geofence {} already assigned to drone {}", geofenceId, droneId);
                    successfulAssignments.add(geofenceId); // 视为成功
                }
            } catch (Exception e) {
                log.error("Failed to assign geofence {} to drone {}: {}", geofenceId, droneId, e.getMessage(), e);
                failedAssignments.add(geofenceId);
            }
        }
        
        // 改进保存逻辑，添加更好的错误处理
        try {
            droneRepository.save(drone);
            log.info("Successfully saved drone {} with {} geofence assignments", droneId, successfulAssignments.size());
        } catch (Exception e) {
            log.error("Failed to save geofence assignments for drone {}: {}", droneId, e.getMessage(), e);
            // 如果保存失败，所有分配都应该被标记为失败
            failedAssignments.addAll(successfulAssignments);
            successfulAssignments.clear();
        }
        
        return GeofenceAssignmentResponse.builder()
                .success(failedAssignments.isEmpty())
                .message(failedAssignments.isEmpty() ? 
                        "所有地理围栏分配成功" : 
                        String.format("成功分配 %d 个，失败 %d 个地理围栏", 
                                successfulAssignments.size(), failedAssignments.size()))
                .droneId(droneId)
                .assignedGeofenceIds(successfulAssignments)
                .failedGeofenceIds(failedAssignments)
                .build();
    }

    @Override
    @Transactional
    public GeofenceAssignmentResponse unassignGeofence(UUID droneId, UUID geofenceId) {
        log.info("Unassigning geofence {} from drone: {}", geofenceId, droneId);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("Drone not found with ID: " + droneId));
        
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with ID: " + geofenceId));
        
        boolean wasAssigned = drone.getGeofences().remove(geofence);
        geofence.getDrones().remove(drone);
        
        droneRepository.save(drone);
        
        return GeofenceAssignmentResponse.builder()
                .success(true)
                .message(wasAssigned ? "地理围栏权限取消成功" : "该无人机未分配此地理围栏")
                .droneId(droneId)
                .assignedGeofenceIds(List.of())
                .failedGeofenceIds(wasAssigned ? List.of() : List.of(geofenceId))
                .build();
    }

    @Override
    @Transactional
    public GeofenceAssignmentResponse updateGeofenceAssignments(UUID droneId, List<UUID> geofenceIds) {
        log.info("Updating geofence assignments for drone: {}, new assignments: {}", droneId, geofenceIds);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("Drone not found with ID: " + droneId));
        
        // 清除现有关联
        Set<Geofence> oldGeofences = drone.getGeofences();
        for (Geofence oldGeofence : oldGeofences) {
            oldGeofence.getDrones().remove(drone);
        }
        drone.getGeofences().clear();
        
        // 分配新的地理围栏
        List<UUID> successfulAssignments = new ArrayList<>();
        List<UUID> failedAssignments = new ArrayList<>();
        
        for (UUID geofenceId : geofenceIds) {
            try {
                Geofence geofence = geofenceRepository.findById(geofenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with ID: " + geofenceId));
                
                drone.getGeofences().add(geofence);
                geofence.getDrones().add(drone);
                successfulAssignments.add(geofenceId);
                log.debug("Successfully assigned geofence {} to drone {}", geofenceId, droneId);
            } catch (Exception e) {
                log.error("Failed to assign geofence {} to drone {}: {}", geofenceId, droneId, e.getMessage());
                failedAssignments.add(geofenceId);
            }
        }
        
        droneRepository.save(drone);
        
        return GeofenceAssignmentResponse.builder()
                .success(failedAssignments.isEmpty())
                .message(failedAssignments.isEmpty() ? 
                        "地理围栏权限更新成功" : 
                        String.format("成功分配 %d 个，失败 %d 个地理围栏", 
                                successfulAssignments.size(), failedAssignments.size()))
                .droneId(droneId)
                .assignedGeofenceIds(successfulAssignments)
                .failedGeofenceIds(failedAssignments)
                .build();
    }
} 