package com.huang.backend.service;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.service.DroneInfluxDBService;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for broadcasting drone position updates via WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DronePositionService {
    
    private final DroneStatusService droneStatusService;
    private final DroneInfluxDBService droneInfluxDBService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 定期从InfluxDB获取无人机位置并通过WebSocket广播
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastDronePositions() {
        List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
        
        List<Map<String, Object>> positions = drones.stream()
            .map(drone -> {
                DroneTelemetryDto telemetry = droneInfluxDBService.getLatestTelemetry(drone.getSerialNumber());
                if (telemetry != null) {
                    Map<String, Object> position = new HashMap<>();
                    position.put("droneId", drone.getDroneId());
                    position.put("serialNumber", drone.getSerialNumber());
                    position.put("model", drone.getModel());
                    position.put("status", drone.getStatus());
                    
                    position.put("latitude", telemetry.getLatitude());
                    position.put("longitude", telemetry.getLongitude());
                    position.put("altitude", telemetry.getAltitude());
                    position.put("batteryLevel", telemetry.getBatteryLevel());
                    position.put("speed", telemetry.getSpeed());
                    position.put("heading", telemetry.getHeading());
                    position.put("timestamp", telemetry.getTimestamp());
                    return position;
                }
                return null;
            })
            .filter(position -> position != null)
            .collect(Collectors.toList());
        
        if (!positions.isEmpty()) {
            log.debug("Broadcasting positions for {} drones", positions.size());
            messagingTemplate.convertAndSend("/topic/drones/positions", positions);
        }
    }
} 