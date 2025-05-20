package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for retrieving drone status information
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/status")
@RequiredArgsConstructor
public class DroneStatusController {

    private final DroneStatusService droneStatusService;

    /**
     * Get all drones with their current status
     *
     * @return a list of all drones with status information
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<DroneStatusDto>> getAllDronesStatus() {
        log.info("Received request to get all drones status");
        List<DroneStatusDto> drones = droneStatusService.getAllDronesStatus();
        return ResponseEntity.ok(drones);
    }

    /**
     * Get status information for a specific drone
     *
     * @param droneId the ID of the drone
     * @return status information for the specified drone
     */
    @GetMapping("/{droneId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<DroneStatusDto> getDroneStatus(@PathVariable UUID droneId) {
        log.info("Received request to get status for drone: {}", droneId);
        DroneStatusDto drone = droneStatusService.getDroneStatus(droneId);
        return ResponseEntity.ok(drone);
    }
    
    /**
     * Get status by drone status type
     *
     * @param status the status to filter by
     * @return a list of drones with the specified status
     */
    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<DroneStatusDto>> getDronesByStatus(
            @RequestParam(required = false) Drone.DroneStatus status) {
        log.info("Received request to get drones with status: {}", status);
        List<DroneStatusDto> drones = droneStatusService.getDronesByStatus(status);
        return ResponseEntity.ok(drones);
    }
    
    /**
     * Get latest telemetry data for a specific drone
     *
     * @param droneId the ID of the drone
     * @return the latest telemetry data for the specified drone
     */
    @GetMapping("/{droneId}/telemetry/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<DroneTelemetryDto> getLatestTelemetry(@PathVariable UUID droneId) {
        log.info("Received request to get latest telemetry for drone: {}", droneId);
        DroneTelemetryDto telemetry = droneStatusService.getLatestTelemetry(droneId);
        return ResponseEntity.ok(telemetry);
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
    @GetMapping("/{droneId}/telemetry/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<DroneTelemetryDto>> getTelemetryHistory(
            @PathVariable UUID droneId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("Received request to get telemetry history for drone: {}, from {} to {}, limit: {}", 
                droneId, start, end, limit);
        List<DroneTelemetryDto> telemetry = droneStatusService.getTelemetryHistory(droneId, start, end, limit);
        return ResponseEntity.ok(telemetry);
    }
} 