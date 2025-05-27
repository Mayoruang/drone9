package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneCommandDto;
import com.huang.backend.drone.dto.DroneCommandRequestDto;
import com.huang.backend.drone.dto.DroneCommandResponseDto;
import com.huang.backend.drone.service.DroneCommandService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for drone command operations (Remote Control functionality)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/{droneId}/commands")
@RequiredArgsConstructor
public class DroneCommandController {

    private final DroneCommandService droneCommandService;

    /**
     * Send a custom command to a drone
     * 
     * @param droneId the ID of the target drone
     * @param request the command request
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping
    public ResponseEntity<DroneCommandResponseDto> sendCommand(
            @PathVariable UUID droneId,
            @Valid @RequestBody DroneCommandRequestDto request,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("🎯 控制器收到命令请求:");
        log.info("   - 无人机ID: {}", droneId);
        log.info("   - 动作: {}", request.getAction());
        log.info("   - 参数: {}", request.getParameters());
        log.info("   - 用户: {}", username);
        log.info("Sending command {} to drone {} by user {}", 
                request.getAction(), droneId, username);
        
        log.info("🔥 控制器即将调用 droneCommandService.sendCommand()...");
        DroneCommandResponseDto response = droneCommandService.sendCommand(
                droneId, request, username);
        log.info("✅ 控制器收到服务响应: success={}, message={}", response.isSuccess(), response.getMessage());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send a raw JSON command to a drone (for advanced users)
     * 
     * @param droneId the ID of the target drone
     * @param rawCommand the raw command as JSON
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping("/raw")
    public ResponseEntity<DroneCommandResponseDto> sendRawCommand(
            @PathVariable UUID droneId,
            @RequestBody Map<String, Object> rawCommand,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Sending raw command to drone {} by user {}", droneId, username);
        
        // Convert raw command to DroneCommandRequestDto
        DroneCommandRequestDto request = DroneCommandRequestDto.builder()
                .action((String) rawCommand.get("action"))
                .parameters((Map<String, Object>) rawCommand.get("parameters"))
                .priority((Integer) rawCommand.getOrDefault("priority", 5))
                .timeoutSeconds((Integer) rawCommand.getOrDefault("timeoutSeconds", 30))
                .cancellable((Boolean) rawCommand.getOrDefault("cancellable", true))
                .build();
        
        DroneCommandResponseDto response = droneCommandService.sendCommand(
                droneId, request, username);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Emergency stop command
     * 
     * @param droneId the ID of the target drone
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping("/emergency-stop")
    public ResponseEntity<DroneCommandResponseDto> emergencyStop(
            @PathVariable UUID droneId,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.warn("Emergency stop command for drone {} by user {}", droneId, username);
        
        DroneCommandResponseDto response = droneCommandService.emergencyStop(droneId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Return to home command
     * 
     * @param droneId the ID of the target drone
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping("/return-to-home")
    public ResponseEntity<DroneCommandResponseDto> returnToHome(
            @PathVariable UUID droneId,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Return to home command for drone {} by user {}", droneId, username);
        
        DroneCommandResponseDto response = droneCommandService.returnToHome(droneId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Land command
     * 
     * @param droneId the ID of the target drone
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping("/land")
    public ResponseEntity<DroneCommandResponseDto> land(
            @PathVariable UUID droneId,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Land command for drone {} by user {}", droneId, username);
        
        DroneCommandResponseDto response = droneCommandService.land(droneId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Hover command
     * 
     * @param droneId the ID of the target drone
     * @param authentication the authenticated user (optional)
     * @return command response
     */
    @PostMapping("/hover")
    public ResponseEntity<DroneCommandResponseDto> hover(
            @PathVariable UUID droneId,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Hover command for drone {} by user {}", droneId, username);
        
        DroneCommandResponseDto response = droneCommandService.hover(droneId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending command
     * 
     * @param droneId the ID of the target drone (for path consistency)
     * @param commandId the ID of the command to cancel
     * @param authentication the authenticated user (optional)
     * @return operation result
     */
    @DeleteMapping("/{commandId}")
    public ResponseEntity<Map<String, Object>> cancelCommand(
            @PathVariable UUID droneId,
            @PathVariable String commandId,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Cancelling command {} for drone {} by user {}", 
                commandId, droneId, username);
        
        boolean success = droneCommandService.cancelCommand(commandId, username);
        
        Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "命令取消成功" : "命令取消失败",
                "commandId", commandId,
                "droneId", droneId
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get command status
     * 
     * @param droneId the ID of the target drone (for path consistency)
     * @param commandId the ID of the command
     * @return command status
     */
    @GetMapping("/{commandId}")
    public ResponseEntity<DroneCommandDto> getCommandStatus(
            @PathVariable UUID droneId,
            @PathVariable String commandId) {
        
        log.debug("Getting status for command {} on drone {}", commandId, droneId);
        
        DroneCommandDto command = droneCommandService.getCommandStatus(commandId);
        
        if (command == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(command);
    }

    /**
     * Get command history for a drone
     * 
     * @param droneId the ID of the target drone
     * @param limit maximum number of commands to return
     * @return command history
     */
    @GetMapping("/history")
    public ResponseEntity<List<DroneCommandDto>> getCommandHistory(
            @PathVariable UUID droneId,
            @RequestParam(defaultValue = "20") int limit) {
        
        log.debug("Getting command history for drone {} with limit {}", droneId, limit);
        
        List<DroneCommandDto> history = droneCommandService.getCommandHistory(droneId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * Get available command actions and their descriptions
     * 
     * @return available commands
     */
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableCommands() {
        Map<String, Object> commands = Map.of(
                "basic", Map.of(
                        DroneCommandDto.Actions.TAKEOFF, "起飞",
                        DroneCommandDto.Actions.LAND, "降落",
                        DroneCommandDto.Actions.RETURN_TO_HOME, "返航",
                        DroneCommandDto.Actions.HOVER, "悬停",
                        DroneCommandDto.Actions.EMERGENCY_STOP, "紧急停止"
                ),
                "movement", Map.of(
                        DroneCommandDto.Actions.MOVE_TO, "移动到指定位置 (需要 latitude, longitude 参数)",
                        DroneCommandDto.Actions.SET_ALTITUDE, "设置高度 (需要 altitude 参数)",
                        DroneCommandDto.Actions.SET_SPEED, "设置速度 (需要 speed 参数)",
                        DroneCommandDto.Actions.ROTATE, "旋转 (需要 angle 参数)"
                ),
                "mission", Map.of(
                        DroneCommandDto.Actions.START_MISSION, "开始任务",
                        DroneCommandDto.Actions.PAUSE_MISSION, "暂停任务",
                        DroneCommandDto.Actions.RESUME_MISSION, "恢复任务",
                        DroneCommandDto.Actions.ABORT_MISSION, "中止任务"
                ),
                "configuration", Map.of(
                        DroneCommandDto.Actions.CALIBRATE, "校准传感器",
                        DroneCommandDto.Actions.SET_HOME, "设置起始点",
                        DroneCommandDto.Actions.ENABLE_FAILSAFE, "启用失效保护",
                        DroneCommandDto.Actions.DISABLE_FAILSAFE, "禁用失效保护"
                )
        );
        
        return ResponseEntity.ok(commands);
    }

    /**
     * Check if a drone is available for commands
     * 
     * @param droneId the ID of the target drone
     * @return availability status
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(@PathVariable UUID droneId) {
        boolean available = droneCommandService.isDroneAvailable(droneId);
        
        Map<String, Object> response = Map.of(
                "droneId", droneId,
                "available", available,
                "message", available ? "无人机可用" : "无人机不可用或离线"
        );
        
        return ResponseEntity.ok(response);
    }
} 