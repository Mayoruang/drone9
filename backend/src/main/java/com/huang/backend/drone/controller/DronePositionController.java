package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.service.DroneInfluxDBService;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for drone position data and WebSocket endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/positions")
@RequiredArgsConstructor
public class DronePositionController {

    private final DroneStatusService droneStatusService;
    private final DroneInfluxDBService droneInfluxDBService;

    /**
     * Get positions of all drones - REST API endpoint
     * Useful for initial loading of map data
     *
     * @return a list of drone positions
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<Map<String, Object>>> getAllDronePositions() {
        log.info("Received request to get all drone positions");
        
        List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
        
        // Extract position data for each drone
        List<Map<String, Object>> positions = drones.stream()
            .map(drone -> {
                DroneTelemetryDto telemetry = droneInfluxDBService.getLatestTelemetry(drone.getSerialNumber());
                if (telemetry != null) {
                    Map<String, Object> position = new HashMap<>();
                    position.put("droneId", drone.getDroneId());
                    position.put("serialNumber", drone.getSerialNumber());
                    position.put("model", drone.getModel());
                    position.put("status", drone.getStatus());
                    position.put("lastHeartbeat", drone.getLastHeartbeat());
                    
                    // Position data from telemetry
                    position.put("latitude", telemetry.getLatitude());
                    position.put("longitude", telemetry.getLongitude());
                    position.put("altitude", telemetry.getAltitude());
                    position.put("batteryLevel", telemetry.getBatteryLevel());
                    position.put("speed", telemetry.getSpeed());
                    position.put("heading", telemetry.getHeading());
                    return position;
                }
                return null;
            })
            .filter(position -> position != null)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(positions);
    }
    
    /**
     * WebSocket endpoint for clients to request drone positions
     * Clients can send a message to this endpoint to get the current positions
     * 
     * @return the current drone positions
     */
    @MessageMapping("/getDronePositions")
    @SendTo("/topic/drones/positions")
    public List<Map<String, Object>> getDronePositions() {
        log.debug("WebSocket request received for drone positions");
        List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
        
        return drones.stream()
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
                    position.put("lastUpdated", telemetry.getTimestamp());
                    return position;
                }
                return null;
            })
            .filter(position -> position != null)
            .collect(Collectors.toList());
    }
} 