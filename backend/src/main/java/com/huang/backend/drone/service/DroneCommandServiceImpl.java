package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneCommandDto;
import com.huang.backend.drone.dto.DroneCommandRequestDto;
import com.huang.backend.drone.dto.DroneCommandResponseDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.exception.BusinessException;
import com.huang.backend.exception.ResourceNotFoundException;
import com.huang.backend.mqtt.service.MqttPublisherService;
import com.huang.backend.drone.service.DroneStatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of DroneCommandService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneCommandServiceImpl implements DroneCommandService {

    private final MqttPublisherService mqttPublisherService;
    private final DroneRepository droneRepository;
    private final DroneStatusService droneStatusService;

    // In-memory storage for command tracking (consider using Redis in production)
    private final Map<String, DroneCommandDto> commandTracker = new ConcurrentHashMap<>();

    @Override
    public DroneCommandResponseDto sendCommand(UUID droneId, DroneCommandRequestDto request, String username) {
        log.info("Sending command {} to drone {} by user {}", request.getAction(), droneId, username);

        try {
            // Validate drone exists and is available
            Drone drone = validateDroneAvailability(droneId);

            // Validate command parameters
            if (!validateCommandParameters(request.getAction(), request.getParameters())) {
                return DroneCommandResponseDto.failure(
                    droneId, 
                    request.getAction(), 
                    "无效的命令参数", 
                    "INVALID_PARAMETERS"
                );
            }

            // Generate unique command ID
            String commandId = UUID.randomUUID().toString();

            // Create command DTO for tracking
            DroneCommandDto commandDto = DroneCommandDto.builder()
                    .commandId(commandId)
                    .droneId(droneId)
                    .action(request.getAction())
                    .parameters(request.getParameters())
                    .priority(request.getPriority())
                    .timeoutSeconds(request.getTimeoutSeconds())
                    .cancellable(request.getCancellable())
                    .issuedBy(username)
                    .issuedAt(ZonedDateTime.now())
                    .status(DroneCommandDto.CommandStatus.PENDING)
                    .build();

            // Store command for tracking
            commandTracker.put(commandId, commandDto);

            // Create MQTT command payload
            Map<String, Object> mqttPayload = createMqttCommandPayload(commandDto);
            
            log.info("🚀 准备发送MQTT命令:");
            log.info("   - 命令ID: {}", commandId);
            log.info("   - 无人机ID: {}", droneId);
            log.info("   - 动作: {}", request.getAction());
            log.info("   - MQTT载荷: {}", mqttPayload);

            // Send command via MQTT
            log.info("🔥 调用mqttPublisherService.publishCommand()...");
            boolean mqttSuccess = mqttPublisherService.publishCommand(droneId.toString(), mqttPayload);
            log.info("🎯 MQTT发布结果: {}", mqttSuccess);

            if (mqttSuccess) {
                // Update command status to SENT
                commandDto.setStatus(DroneCommandDto.CommandStatus.SENT);
                commandDto.setExecutedAt(ZonedDateTime.now());
                commandTracker.put(commandId, commandDto);

                log.info("Command {} sent successfully to drone {}", commandId, droneId);

                return DroneCommandResponseDto.builder()
                    .success(true)
                    .commandId(commandId)
                    .droneId(droneId)
                    .action(request.getAction())
                    .message("命令发送成功")
                    .status(DroneCommandDto.CommandStatus.SENT)
                    .issuedAt(ZonedDateTime.now())
                    .timeoutSeconds(request.getTimeoutSeconds())
                    .cancellable(request.getCancellable())
                    .mqttTopic("drones/" + droneId + "/commands")
                    .build();

            } else {
                // MQTT sending failed
                commandDto.setStatus(DroneCommandDto.CommandStatus.FAILED);
                commandDto.setErrorMessage("MQTT消息发送失败");
                commandTracker.put(commandId, commandDto);

                log.error("Failed to send command {} to drone {} via MQTT", commandId, droneId);

                return DroneCommandResponseDto.failure(
                    droneId, 
                    request.getAction(), 
                    "命令发送失败：MQTT连接异常", 
                    "MQTT_SEND_FAILED"
                );
            }

        } catch (ResourceNotFoundException e) {
            log.error("Drone not found: {}", droneId);
            return DroneCommandResponseDto.failure(
                droneId, 
                request.getAction(), 
                "无人机不存在", 
                "DRONE_NOT_FOUND"
            );

        } catch (BusinessException e) {
            log.error("Business error sending command to drone {}: {}", droneId, e.getMessage());
            return DroneCommandResponseDto.failure(
                droneId, 
                request.getAction(), 
                e.getMessage(), 
                "BUSINESS_ERROR"
            );

        } catch (Exception e) {
            log.error("Unexpected error sending command to drone {}: {}", droneId, e.getMessage(), e);
            return DroneCommandResponseDto.failure(
                droneId, 
                request.getAction(), 
                "系统错误：" + e.getMessage(), 
                "SYSTEM_ERROR"
            );
        }
    }

    @Override
    public DroneCommandResponseDto sendQuickCommand(UUID droneId, String action, String username) {
        DroneCommandRequestDto request = DroneCommandRequestDto.builder()
                .action(action)
                .parameters(Collections.emptyMap())
                .priority(8) // High priority for quick commands
                .timeoutSeconds(30)
                .cancellable(true)
                .build();

        return sendCommand(droneId, request, username);
    }

    @Override
    public boolean cancelCommand(String commandId, String username) {
        log.info("Cancelling command {} by user {}", commandId, username);

        DroneCommandDto command = commandTracker.get(commandId);
        if (command == null) {
            log.warn("Command {} not found for cancellation", commandId);
            return false;
        }

        if (!command.getCancellable()) {
            log.warn("Command {} is not cancellable", commandId);
            return false;
        }

        if (command.getStatus() == DroneCommandDto.CommandStatus.COMPLETED ||
            command.getStatus() == DroneCommandDto.CommandStatus.FAILED ||
            command.getStatus() == DroneCommandDto.CommandStatus.CANCELLED) {
            log.warn("Command {} is already in final state: {}", commandId, command.getStatus());
            return false;
        }

        // Send cancellation command via MQTT
        Map<String, Object> cancelPayload = Map.of(
            "action", "CANCEL_COMMAND",
            "parameters", Map.of("commandId", commandId),
            "timestamp", ZonedDateTime.now().toString()
        );

        boolean mqttSuccess = mqttPublisherService.publishCommand(
            command.getDroneId().toString(), 
            cancelPayload
        );

        if (mqttSuccess) {
            command.setStatus(DroneCommandDto.CommandStatus.CANCELLED);
            command.setCompletedAt(ZonedDateTime.now());
            commandTracker.put(commandId, command);
            log.info("Command {} cancelled successfully", commandId);
            return true;
        } else {
            log.error("Failed to send cancellation for command {} via MQTT", commandId);
            return false;
        }
    }

    @Override
    public DroneCommandDto getCommandStatus(String commandId) {
        return commandTracker.get(commandId);
    }

    @Override
    public List<DroneCommandDto> getCommandHistory(UUID droneId, int limit) {
        return commandTracker.values().stream()
                .filter(cmd -> cmd.getDroneId().equals(droneId))
                .sorted((c1, c2) -> c2.getIssuedAt().compareTo(c1.getIssuedAt())) // Most recent first
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<DroneCommandResponseDto> emergencyStopAll(String username) {
        log.warn("Emergency stop all drones requested by user {}", username);

        List<Drone> allDrones = droneRepository.findAll();
        return allDrones.stream()
                .map(drone -> emergencyStop(drone.getDroneId(), username))
                .collect(Collectors.toList());
    }

    @Override
    public DroneCommandResponseDto emergencyStop(UUID droneId, String username) {
        log.warn("Emergency stop requested for drone {} by user {}", droneId, username);

        DroneCommandRequestDto emergencyRequest = DroneCommandRequestDto.builder()
                .action(DroneCommandDto.Actions.EMERGENCY_STOP)
                .parameters(Map.of("reason", "User emergency stop"))
                .priority(10) // Maximum priority
                .timeoutSeconds(10) // Short timeout for emergency
                .cancellable(false) // Cannot cancel emergency stop
                .build();

        return sendCommand(droneId, emergencyRequest, username);
    }

    @Override
    public DroneCommandResponseDto returnToHome(UUID droneId, String username) {
        return sendQuickCommand(droneId, DroneCommandDto.Actions.RETURN_TO_HOME, username);
    }

    @Override
    public DroneCommandResponseDto land(UUID droneId, String username) {
        return sendQuickCommand(droneId, DroneCommandDto.Actions.LAND, username);
    }

    @Override
    public DroneCommandResponseDto hover(UUID droneId, String username) {
        log.info("Hover command for drone {} by user {}", droneId, username);
        
        // For hover, we need to get the current position and use it as target
        try {
            Drone drone = droneRepository.findById(droneId)
                    .orElseThrow(() -> new ResourceNotFoundException("无人机不存在: " + droneId));
            
            // Get latest telemetry to find current position
            DroneTelemetryDto telemetry = droneStatusService.getLatestTelemetry(droneId);
            
            // Use current position for hover (stay in place), with defaults if telemetry not available
            Map<String, Object> hoverParams = new HashMap<>();
            hoverParams.put("latitude", telemetry != null && telemetry.getLatitude() != null ? telemetry.getLatitude() : 34.0522);
            hoverParams.put("longitude", telemetry != null && telemetry.getLongitude() != null ? telemetry.getLongitude() : -118.2437);
            hoverParams.put("altitude", telemetry != null && telemetry.getAltitude() != null ? telemetry.getAltitude() : 10.0);
            
            DroneCommandRequestDto hoverRequest = DroneCommandRequestDto.builder()
                    .action(DroneCommandDto.Actions.HOVER)
                    .parameters(hoverParams)
                    .priority(8) // High priority for quick commands
                    .timeoutSeconds(30)
                    .cancellable(true)
                    .build();

            return sendCommand(droneId, hoverRequest, username);
        } catch (Exception e) {
            log.error("Error creating hover command for drone {}: {}", droneId, e.getMessage());
            return DroneCommandResponseDto.failure(
                droneId, 
                DroneCommandDto.Actions.HOVER, 
                "获取无人机位置失败：" + e.getMessage(), 
                "POSITION_ERROR"
            );
        }
    }

    @Override
    public boolean isDroneAvailable(UUID droneId) {
        try {
            Drone drone = droneRepository.findById(droneId)
                    .orElse(null);

            if (drone == null) {
                return false;
            }

            // Check if drone is in a state that can accept commands
            // Allow GEOFENCE_VIOLATION to accept emergency commands (RTH, LAND, EMERGENCY_STOP)
            return drone.getCurrentStatus() == Drone.DroneStatus.ONLINE ||
                   drone.getCurrentStatus() == Drone.DroneStatus.FLYING ||
                   drone.getCurrentStatus() == Drone.DroneStatus.IDLE ||
                   drone.getCurrentStatus() == Drone.DroneStatus.GEOFENCE_VIOLATION;

        } catch (Exception e) {
            log.error("Error checking drone availability for {}: {}", droneId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateCommandParameters(String action, Object parameters) {
        // Basic validation logic - extend as needed
        switch (action) {
            case DroneCommandDto.Actions.MOVE_TO:
                if (parameters instanceof Map) {
                    Map<?, ?> params = (Map<?, ?>) parameters;
                    return params.containsKey("latitude") && params.containsKey("longitude");
                }
                return false;

            case DroneCommandDto.Actions.SET_ALTITUDE:
                if (parameters instanceof Map) {
                    Map<?, ?> params = (Map<?, ?>) parameters;
                    if (params.containsKey("altitude")) {
                        try {
                            double altitude = Double.parseDouble(params.get("altitude").toString());
                            return altitude >= 0 && altitude <= 500; // 0-500m range
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
                return false;

            case DroneCommandDto.Actions.SET_SPEED:
                if (parameters instanceof Map) {
                    Map<?, ?> params = (Map<?, ?>) parameters;
                    if (params.containsKey("speed")) {
                        try {
                            double speed = Double.parseDouble(params.get("speed").toString());
                            return speed >= 0 && speed <= 30; // 0-30m/s range
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
                return false;

            // Simple commands without parameters
            case DroneCommandDto.Actions.ARM:
            case DroneCommandDto.Actions.DISARM:
            case DroneCommandDto.Actions.TAKEOFF:
            case DroneCommandDto.Actions.LAND:
            case DroneCommandDto.Actions.RETURN_TO_HOME:
            case DroneCommandDto.Actions.HOVER:
            case DroneCommandDto.Actions.EMERGENCY_STOP:
                return true;

            default:
                log.warn("Unknown command action: {}", action);
                return false;
        }
    }

    /**
     * Validate that the drone exists and is available for commands
     */
    private Drone validateDroneAvailability(UUID droneId) {
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ResourceNotFoundException("无人机不存在: " + droneId));

        if (!isDroneAvailable(droneId)) {
            throw new BusinessException("无人机 " + drone.getSerialNumber() + " 当前不可用，状态：" + drone.getCurrentStatus());
        }

        return drone;
    }

    /**
     * Create MQTT command payload from command DTO
     */
    private Map<String, Object> createMqttCommandPayload(DroneCommandDto command) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.getCommandId());
        
        // Use "type" instead of "action" for compatibility with drone simulator
        String actionType = mapActionToSimulatorType(command.getAction());
        payload.put("type", actionType);
        
        payload.put("parameters", command.getParameters() != null ? command.getParameters() : Collections.emptyMap());
        payload.put("priority", command.getPriority());
        payload.put("timeoutSeconds", command.getTimeoutSeconds());
        payload.put("cancellable", command.getCancellable());
        payload.put("issuedBy", command.getIssuedBy());
        payload.put("issuedAt", command.getIssuedAt().toString());
        payload.put("timestamp", ZonedDateTime.now().toString());

        return payload;
    }
    
    /**
     * Map command actions to simulator-compatible types
     */
    private String mapActionToSimulatorType(String action) {
        switch (action) {
            case DroneCommandDto.Actions.ARM:
                return "ARM";
            case DroneCommandDto.Actions.DISARM:
                return "DISARM";
            case DroneCommandDto.Actions.HOVER:
                return "GOTO"; // HOVER can be implemented as GOTO current position
            case DroneCommandDto.Actions.LAND:
                return "LAND";
            case DroneCommandDto.Actions.TAKEOFF:
                return "TAKEOFF";
            case DroneCommandDto.Actions.RETURN_TO_HOME:
                return "RTL";
            case DroneCommandDto.Actions.MOVE_TO:
                return "GOTO";
            case DroneCommandDto.Actions.EMERGENCY_STOP:
                return "LAND"; // Map emergency stop to immediate land
            default:
                return action; // Keep original action if no mapping needed
        }
    }
} 