package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneDeleteResponseDto;
import com.huang.backend.drone.service.DroneManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Test controller for drone operations without security constraints
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/test")
@RequiredArgsConstructor
public class DroneTestController {

    private final DroneManagementService droneManagementService;
    
    /**
     * Test endpoint to delete a drone without security constraints
     *
     * @param droneId the ID of the drone to delete
     * @return 200 OK with the result of the operation
     */
    @GetMapping("/delete/{droneId}")
    public ResponseEntity<DroneDeleteResponseDto> testDeleteDrone(@PathVariable UUID droneId) {
        log.info("Test endpoint: Received request to delete drone: {}", droneId);
        DroneDeleteResponseDto response = droneManagementService.deleteDrone(droneId);
        return ResponseEntity.ok(response);
    }
} 