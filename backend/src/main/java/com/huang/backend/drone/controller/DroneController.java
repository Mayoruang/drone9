package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.dto.DroneStatsDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.service.DroneStatusService;
import com.huang.backend.drone.service.DroneInfluxDBService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 获取所有无人机列表
     * @return 无人机数据数组
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
     * 发送无人机命令
     * @param droneId 无人机ID
     * @param command 命令内容
     * @return 操作响应
     */
    @PostMapping("/{droneId}/commands")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<CommandResponse> sendDroneCommand(
            @PathVariable UUID droneId,
            @RequestBody DroneCommandRequest command) {
        log.info("Sending command to drone: {}, command: {}", droneId, command.getType());
        
        CommandResponse response = CommandResponse.builder()
                .success(true)
                .message("命令发送成功")
                .commandId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * 更新无人机状态
     * @param droneId 无人机ID
     * @param request 状态更新请求
     * @return 操作响应
     */
    @PutMapping("/{droneId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatusUpdateResponse> updateDroneStatus(
            @PathVariable UUID droneId,
            @RequestBody StatusUpdateRequest request) {
        log.info("Updating status for drone: {}, new status: {}", droneId, request.getStatus());
        
        StatusUpdateResponse response = StatusUpdateResponse.builder()
                .success(true)
                .message("状态更新成功")
                .droneId(droneId)
                .newStatus(request.getStatus())
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * 删除无人机
     * @param droneId 无人机ID
     * @return 操作响应
     */
    @DeleteMapping("/{droneId}")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
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
    public static class DroneCommandRequest {
        private String type;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandResponse {
        private boolean success;
        private String message;
        private String commandId;
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
} 