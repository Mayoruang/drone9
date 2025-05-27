package com.huang.backend.drone.websocket;

import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.drone.service.DroneInfluxDBService;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * WebSocket handler for broadcasting drone position updates to clients
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final DroneStatusService droneStatusService;
    private final DroneInfluxDBService droneInfluxDBService;
    private final DroneRepository droneRepository;
    
    // Topic for drone position updates
    private static final String TOPIC_DRONE_POSITIONS = "/topic/drones/positions";
    
    // Topic for individual drone updates
    private static final String TOPIC_DRONE_UPDATE = "/topic/drones/";
    
    /**
     * Broadcasts all drone positions periodically
     * This is useful for clients that just connected and need the initial state
     */
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void broadcastAllDronePositions() {
        try {
            // Get all drones from status service
            List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
            
            if (drones.isEmpty()) {
                log.debug("No drones found to broadcast positions");
                return;
            }
            
            // Collect telemetry data for each drone
            List<Map<String, Object>> dronePositions = drones.stream()
                .map(drone -> {
                    // 使用UUID而不是序列号来查询InfluxDB，因为存储时使用的是UUID
                    DroneTelemetryDto telemetry = droneInfluxDBService.getLatestTelemetry(drone.getDroneId().toString());
                    if (telemetry != null) {
                        Map<String, Object> positionData = new HashMap<>();
                        positionData.put("droneId", drone.getDroneId());
                        positionData.put("serialNumber", drone.getSerialNumber());
                        positionData.put("model", drone.getModel());
                        positionData.put("status", drone.getStatus());
                        positionData.put("latitude", telemetry.getLatitude());
                        positionData.put("longitude", telemetry.getLongitude());
                        positionData.put("altitude", telemetry.getAltitude());
                        positionData.put("batteryLevel", telemetry.getBatteryLevel());
                        positionData.put("speed", telemetry.getSpeed());
                        positionData.put("heading", telemetry.getHeading());
                        positionData.put("lastUpdated", telemetry.getTimestamp());
                        return positionData;
                    }
                    return null;
                })
                .filter(data -> data != null)
                .collect(Collectors.toList());
            
            if (!dronePositions.isEmpty()) {
                // Send to all subscribers
                messagingTemplate.convertAndSend(TOPIC_DRONE_POSITIONS, dronePositions);
                log.debug("Broadcast positions for {} drones", dronePositions.size());
            }
        } catch (Exception e) {
            log.error("Error broadcasting drone positions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send update for a specific drone
     * This is useful when a drone's telemetry is updated via MQTT
     * 
     * @param droneId the drone ID
     * @param telemetry the updated telemetry data
     */
    public void sendDroneUpdate(UUID droneId, DroneTelemetryDto telemetry) {
        try {
            if (telemetry == null) {
                log.warn("Cannot send update for drone {}: telemetry is null", droneId);
                return;
            }
            
            // 查找无人机记录以获取最新状态
            Optional<Drone> droneOpt = null;
            try {
                droneOpt = droneRepository.findById(droneId);
            } catch (Exception e) {
                log.warn("Error finding drone with ID {}: {}", droneId, e.getMessage());
            }
            
            // Create position data
            Map<String, Object> positionData = new HashMap<>();
            positionData.put("droneId", droneId.toString());
            positionData.put("latitude", telemetry.getLatitude());
            positionData.put("longitude", telemetry.getLongitude());
            positionData.put("altitude", telemetry.getAltitude());
            positionData.put("batteryLevel", telemetry.getBatteryLevel());
            positionData.put("speed", telemetry.getSpeed());
            positionData.put("heading", telemetry.getHeading());
            positionData.put("lastUpdated", telemetry.getTimestamp());
            positionData.put("flightMode", telemetry.getFlightMode());
            
            // 添加status字段，优先使用遥测数据中的status
            if (telemetry.getStatus() != null) {
                positionData.put("status", telemetry.getStatus());
            } else if (droneOpt != null && droneOpt.isPresent()) {
                // 如果遥测中没有status字段，使用数据库中的状态
                positionData.put("status", droneOpt.get().getCurrentStatus().name());
            }
            
            // Send to topic for this specific drone
            messagingTemplate.convertAndSend(TOPIC_DRONE_UPDATE + droneId, positionData);
            log.debug("Sent position update for drone {}", droneId);
        } catch (Exception e) {
            log.error("Error sending drone update: {}", e.getMessage(), e);
        }
    }
} 