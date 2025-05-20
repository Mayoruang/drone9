package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneDeleteResponseDto;
import com.huang.backend.drone.dto.DroneOfflineRequestDto;
import com.huang.backend.drone.dto.DroneOfflineResponseDto;
import com.huang.backend.drone.service.DroneManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for drone management operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/management")
@RequiredArgsConstructor
public class DroneManagementController {

    private final DroneManagementService droneManagementService;
    
    /**
     * Set a drone offline
     *
     * @param request the offline request
     * @return 200 OK with the result of the operation
     */
    @PostMapping("/offline")
    public ResponseEntity<DroneOfflineResponseDto> setDroneOffline(@Valid @RequestBody DroneOfflineRequestDto request) {
        log.info("Received request to set drone offline: {}", request.getDroneId());
        DroneOfflineResponseDto response = droneManagementService.setDroneOffline(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete a drone from the system
     *
     * @param droneId the ID of the drone to delete
     * @return 200 OK with the result of the operation
     */
    @DeleteMapping("/{droneId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DroneDeleteResponseDto> deleteDrone(@PathVariable UUID droneId) {
        log.info("Received request to delete drone: {}", droneId);
        DroneDeleteResponseDto response = droneManagementService.deleteDrone(droneId);
        return ResponseEntity.ok(response);
    }
} 