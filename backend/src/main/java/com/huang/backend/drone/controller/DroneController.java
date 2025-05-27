package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.dto.DroneStatsDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.drone.service.DroneStatusService;
import com.huang.backend.drone.service.DroneInfluxDBService;
import com.huang.backend.geofence.dto.GeofenceListItemDto;
import com.huang.backend.geofence.dto.GeofenceResponseDto;
import com.huang.backend.geofence.service.GeofenceService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * 统一的无人机API控制器
 * 提供前端期望的标准化接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones")
@RequiredArgsConstructor
public class DroneController {

    private final DroneStatusService droneStatusService;
    private final DroneInfluxDBService droneInfluxDBService;
    private final GeofenceService geofenceService;
    private final DroneRepository droneRepository;

    /**
     * 获取所有无人机列表
     * @return 无人机数据数组
     */
    @GetMapping
    public ResponseEntity<List<DroneStatusDto>> getAllDrones() {
        log.info("Getting all drones list");
        List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
        return ResponseEntity.ok(drones);
    }

    /**
     * 获取无人机分页列表
     * @param page 页码
     * @param size 每页大小
     * @return 分页响应
     */
    @GetMapping(params = {"page"})
    public ResponseEntity<Page<DroneStatusDto>> getDroneList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting drones with page: {} and size: {}", page, size);
        
        List<DroneStatusDto> allDrones = droneStatusService.getAllDronesStatus();
        
        // 简单的内存分页实现
        int start = page * size;
        int end = Math.min(start + size, allDrones.size());
        List<DroneStatusDto> pageContent = allDrones.subList(start, end);
        
        return ResponseEntity.ok(new PageImpl<>(pageContent, PageRequest.of(page, size), allDrones.size()));
    }

    /**
     * 获取无人机详细信息
     * @param droneId 无人机ID
     * @return 无人机详情
     */
    @GetMapping("/{droneId}")
    public ResponseEntity<DroneStatusDto> getDroneDetail(@PathVariable UUID droneId) {
        log.info("Getting details for drone: {}", droneId);
        DroneStatusDto drone = droneStatusService.getDroneStatus(droneId);
        return ResponseEntity.ok(drone);
    }

    /**
     * 获取无人机遥测数据
     * @param droneId 无人机ID
     * @return 遥测数据
     */
    @GetMapping("/{droneId}/telemetry")
    public ResponseEntity<DroneTelemetryDto> getDroneTelemetry(@PathVariable UUID droneId) {
        log.info("Getting telemetry for drone: {}", droneId);
        DroneTelemetryDto telemetry = droneStatusService.getLatestTelemetry(droneId);
        return ResponseEntity.ok(telemetry);
    }

    /**
     * 批量获取无人机遥测数据
     * @param request 包含无人机ID列表的请求
     * @return 遥测数据数组
     */
    @PostMapping("/telemetry/batch")
    public ResponseEntity<List<DroneTelemetryDto>> getBatchDroneTelemetry(
            @RequestBody BatchTelemetryRequest request) {
        log.info("Getting batch telemetry for {} drones", request.getDroneIds().size());
        
        List<DroneTelemetryDto> telemetryList = request.getDroneIds().stream()
                .map(droneStatusService::getLatestTelemetry)
                .filter(telemetry -> telemetry != null)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(telemetryList);
    }

    /**
     * 更新无人机状态
     * @param droneId 无人机ID
     * @param request 状态更新请求
     * @return 操作响应
     */
    @PutMapping("/{droneId}/status")
    @Transactional
    public ResponseEntity<StatusUpdateResponse> updateDroneStatus(
            @PathVariable UUID droneId,
            @RequestBody StatusUpdateRequest request) {
        log.info("Updating status for drone: {}, new status: {}", droneId, request.getStatus());
        
        try {
            // 查找无人机
            Optional<Drone> droneOpt = droneRepository.findById(droneId);
            if (droneOpt.isEmpty()) {
                StatusUpdateResponse response = StatusUpdateResponse.builder()
                        .success(false)
                        .message("无人机不存在")
                        .droneId(droneId)
                        .newStatus(null)
                        .build();
                return ResponseEntity.notFound().build();
            }
            
            Drone drone = droneOpt.get();
            Drone.DroneStatus oldStatus = drone.getCurrentStatus();
            
            // 更新状态
            drone.setCurrentStatus(request.getStatus());
            droneRepository.save(drone);
            
            log.info("Successfully updated drone {} status from {} to {}", 
                    drone.getSerialNumber(), oldStatus, request.getStatus());
            
            StatusUpdateResponse response = StatusUpdateResponse.builder()
                    .success(true)
                    .message("状态更新成功")
                    .droneId(droneId)
                    .newStatus(request.getStatus())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error updating drone status for {}: {}", droneId, e.getMessage(), e);
            StatusUpdateResponse response = StatusUpdateResponse.builder()
                    .success(false)
                    .message("状态更新失败: " + e.getMessage())
                    .droneId(droneId)
                    .newStatus(null)
                    .build();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 删除无人机
     * @param droneId 无人机ID
     * @return 操作响应
     */
    @DeleteMapping("/{droneId}")
    public ResponseEntity<DeleteResponse> deleteDrone(@PathVariable UUID droneId) {
        log.info("Deleting drone: {}", droneId);
        
        DeleteResponse response = DeleteResponse.builder()
                .success(true)
                .message("无人机删除成功")
                .resourceId(droneId.toString())
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取无人机统计信息
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<DroneStatsDto> getDroneStats() {
        log.info("Getting drone statistics");
        
        List<DroneStatusDto> allDrones = droneStatusService.getAllDronesStatus();
        
        Map<Drone.DroneStatus, Long> statusCounts = allDrones.stream()
                .collect(Collectors.groupingBy(
                        DroneStatusDto::getStatus,
                        Collectors.counting()
                ));
        
        DroneStatsDto stats = DroneStatsDto.builder()
                .total(allDrones.size())
                .online(statusCounts.getOrDefault(Drone.DroneStatus.ONLINE, 0L).intValue())
                .offline(statusCounts.getOrDefault(Drone.DroneStatus.OFFLINE, 0L).intValue())
                .flying(statusCounts.getOrDefault(Drone.DroneStatus.FLYING, 0L).intValue())
                .idle(statusCounts.getOrDefault(Drone.DroneStatus.IDLE, 0L).intValue())
                .error(statusCounts.getOrDefault(Drone.DroneStatus.ERROR, 0L).intValue())
                .lowBattery(0)
                .statusCounts(statusCounts.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey().toString(),
                                entry -> entry.getValue().intValue()
                        )))
                .build();
        
        return ResponseEntity.ok(stats);
    }

    /**
     * 测试无人机API连接
     * @return 连接状态
     */
    @GetMapping("/test")
    public ResponseEntity<String> testDroneAPI() {
        log.info("Testing drone API connection");
        return ResponseEntity.ok("Drone controller is working!");
    }

    /**
     * 获取无人机关联的地理围栏列表
     * @param droneId 无人机ID
     * @return 地理围栏列表
     */
    @GetMapping("/{droneId}/geofences")
    public ResponseEntity<List<GeofenceListItemDto>> getDroneGeofences(@PathVariable UUID droneId) {
        log.info("Getting geofences for drone: {}", droneId);
        List<GeofenceListItemDto> geofences = droneStatusService.getDroneGeofences(droneId);
        return ResponseEntity.ok(geofences);
    }

    /**
     * 获取可分配给无人机的地理围栏列表（所有限制区域）
     * @param droneId 无人机ID
     * @param type 地理围栏类型过滤（可选）
     * @param active 是否仅返回活跃的地理围栏（可选）
     * @return 地理围栏列表
     */
    @GetMapping("/{droneId}/geofences/available")
    public ResponseEntity<List<GeofenceListItemDto>> getAvailableGeofences(
            @PathVariable UUID droneId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "true") Boolean active) {
        log.info("Getting available geofences for drone: {}, type: {}, active: {}", droneId, type, active);
        List<GeofenceListItemDto> geofences = droneStatusService.getAvailableGeofences(type, active);
        return ResponseEntity.ok(geofences);
    }

    /**
     * 为无人机分配地理围栏权限
     * @param droneId 无人机ID
     * @param request 分配请求
     * @return 操作响应
     */
    @PostMapping("/{droneId}/geofences")
    public ResponseEntity<GeofenceAssignmentResponse> assignGeofences(
            @PathVariable UUID droneId,
            @Valid @RequestBody GeofenceAssignmentRequest request) {
        log.info("Assigning geofences to drone: {}, geofences: {}", droneId, request.getGeofenceIds());
        GeofenceAssignmentResponse response = droneStatusService.assignGeofences(droneId, request.getGeofenceIds());
        return ResponseEntity.ok(response);
    }

    /**
     * 取消无人机的地理围栏权限
     * @param droneId 无人机ID
     * @param geofenceId 地理围栏ID
     * @return 操作响应
     */
    @DeleteMapping("/{droneId}/geofences/{geofenceId}")
    public ResponseEntity<GeofenceAssignmentResponse> unassignGeofence(
            @PathVariable UUID droneId,
            @PathVariable UUID geofenceId) {
        log.info("Unassigning geofence {} from drone: {}", geofenceId, droneId);
        GeofenceAssignmentResponse response = droneStatusService.unassignGeofence(droneId, geofenceId);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量更新无人机的地理围栏权限
     * @param droneId 无人机ID
     * @param request 批量更新请求
     * @return 操作响应
     */
    @PutMapping("/{droneId}/geofences")
    public ResponseEntity<GeofenceAssignmentResponse> updateGeofenceAssignments(
            @PathVariable UUID droneId,
            @Valid @RequestBody GeofenceAssignmentRequest request) {
        log.info("Updating geofence assignments for drone: {}, new geofences: {}", droneId, request.getGeofenceIds());
        GeofenceAssignmentResponse response = droneStatusService.updateGeofenceAssignments(droneId, request.getGeofenceIds());
        return ResponseEntity.ok(response);
    }

    // ============================================================================
    // 内部类定义
    // ============================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchTelemetryRequest {
        private List<UUID> droneIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateRequest {
        private Drone.DroneStatus status;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateResponse {
        private boolean success;
        private String message;
        private UUID droneId;
        private Drone.DroneStatus newStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteResponse {
        private boolean success;
        private String message;
        private String resourceId;
    }

    /**
     * 地理围栏分配请求DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeofenceAssignmentRequest {
        private List<UUID> geofenceIds;
    }

    /**
     * 地理围栏分配响应DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeofenceAssignmentResponse {
        private boolean success;
        private String message;
        private UUID droneId;
        private List<UUID> assignedGeofenceIds;
        private List<UUID> failedGeofenceIds;
    }
} 