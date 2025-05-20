package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for drone monitoring features
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class DroneMonitoringController {

    private final DroneStatusService droneStatusService;

    /**
     * Get all drones with their current status
     * 
     * @return a list of all drones with status information
     */
    @GetMapping("/drones")
    public ResponseEntity<List<DroneStatusDto>> getAllDrones() {
        log.debug("REST request to get all drones with status");
        return ResponseEntity.ok(droneStatusService.getAllDronesStatus());
    }
    
    /**
     * Get drones filtered by status
     * 
     * @param status the status to filter by, if null returns all drones
     * @return a list of drones with the specified status
     */
    @GetMapping("/drones/status/{status}")
    public ResponseEntity<List<DroneStatusDto>> getDronesByStatus(
            @PathVariable Drone.DroneStatus status) {
        log.debug("REST request to get drones with status: {}", status);
        return ResponseEntity.ok(droneStatusService.getDronesByStatus(status));
    }
    
    /**
     * Get status information for a specific drone
     * 
     * @param droneId the ID of the drone
     * @return status information for the specified drone
     */
    @GetMapping("/drones/{droneId}")
    public ResponseEntity<DroneStatusDto> getDroneStatus(
            @PathVariable UUID droneId) {
        log.debug("REST request to get status for drone: {}", droneId);
        return ResponseEntity.ok(droneStatusService.getDroneStatus(droneId));
    }
    
    /**
     * Get latest telemetry data for a specific drone
     * 
     * @param droneId the ID of the drone
     * @return the latest telemetry data for the specified drone
     */
    @GetMapping("/drones/{droneId}/telemetry/latest")
    public ResponseEntity<DroneTelemetryDto> getLatestTelemetry(
            @PathVariable UUID droneId) {
        log.debug("REST request to get latest telemetry for drone: {}", droneId);
        return ResponseEntity.ok(droneStatusService.getLatestTelemetry(droneId));
    }
    
    /**
     * Get historical telemetry data for a specific drone within a time range
     * 
     * @param droneId the ID of the drone
     * @param start the start time (ISO format)
     * @param end the end time (ISO format)
     * @param limit the maximum number of records to return
     * @return a list of telemetry data points within the specified time range
     */
    @GetMapping("/drones/{droneId}/telemetry/history")
    public ResponseEntity<List<DroneTelemetryDto>> getTelemetryHistory(
            @PathVariable UUID droneId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "100") int limit) {
        log.debug("REST request to get telemetry history for drone: {}, from {} to {}, limit: {}", 
                droneId, start, end, limit);
        return ResponseEntity.ok(droneStatusService.getTelemetryHistory(droneId, start, end, limit));
    }
    
    /**
     * Get online drones with their latest positions
     * 
     * @return a list of online drones with their positions
     */
    @GetMapping("/drones/map")
    public ResponseEntity<List<DroneStatusDto>> getDronesForMap() {
        log.debug("REST request to get online drones for map display");
        return ResponseEntity.ok(droneStatusService.getDronesByStatus(Drone.DroneStatus.ONLINE));
    }
} 