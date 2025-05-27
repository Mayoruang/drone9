package com.huang.backend.controller;

import com.huang.backend.drone.dto.DroneDeleteResponseDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.registration.entity.DroneRegistrationRequest;
import com.huang.backend.registration.repository.DroneRegistrationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Test controller with unrestricted access for testing purposes
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final DroneRepository droneRepository;
    private final DroneRegistrationRequestRepository registrationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Deletes a drone without any security checks
     * 
     * @param droneId the ID of the drone to delete
     * @return response with the result
     */
    @GetMapping("/delete-drone/{droneId}")
    @Transactional
    public ResponseEntity<DroneDeleteResponseDto> deleteDroneNoAuth(@PathVariable UUID droneId) {
        log.info("Test API - Deleting drone: {}", droneId);
        
        try {
            // Find the drone
            Drone drone = droneRepository.findById(droneId)
                    .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
            
            String serialNumber = drone.getSerialNumber();
            
            // Check if drone is flying
            if (drone.getCurrentStatus() == Drone.DroneStatus.FLYING) {
                return ResponseEntity.ok(DroneDeleteResponseDto.builder()
                        .droneId(droneId)
                        .serialNumber(serialNumber)
                        .success(false)
                        .message("Cannot delete drone while it is flying")
                        .build());
            }
            
            // Clear geofence associations to avoid foreign key constraint violations
            log.info("Test API - Clearing geofence associations for drone: {}", serialNumber);
            if (drone.getGeofences() != null && !drone.getGeofences().isEmpty()) {
                int associationCount = drone.getGeofences().size();
                drone.getGeofences().clear();
                droneRepository.save(drone); // Save to clear the associations
                log.info("Test API - Cleared {} geofence associations for drone: {}", associationCount, serialNumber);
            }
            
            // Delete the drone entity
            droneRepository.delete(drone);
            
            // Update the registration request if it exists
            if (drone.getRegistrationRequestId() != null) {
                registrationRepository.findById(drone.getRegistrationRequestId())
                        .ifPresent(request -> {
                            request.setDroneId(null);
                            request.setStatus(DroneRegistrationRequest.RegistrationStatus.DELETED);
                            request.setAdminNotes((request.getAdminNotes() != null ? request.getAdminNotes() : "") 
                                               + "\nDrone deleted at " + ZonedDateTime.now());
                            registrationRepository.save(request);
                        });
            }
            
            // Send WebSocket notification
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "DRONE_DELETED");
            notification.put("droneId", droneId.toString());
            notification.put("serialNumber", serialNumber);
            notification.put("timestamp", ZonedDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/drones/deleted", notification);
            
            log.info("Test API - Drone successfully deleted: {}", serialNumber);
            
            return ResponseEntity.ok(DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .serialNumber(serialNumber)
                    .success(true)
                    .message("Drone successfully deleted")
                    .build());
            
        } catch (NoSuchElementException e) {
            log.error("Test API - Drone not found for deletion: {}", droneId);
            return ResponseEntity.ok(DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .success(false)
                    .message("Drone not found: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Test API - Error deleting drone {}: {}", droneId, e.getMessage(), e);
            return ResponseEntity.ok(DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .success(false)
                    .message("Error deleting drone: " + e.getMessage())
                    .build());
        }
    }
    
    /**
     * Simple health check endpoint
     */
    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello from TestController");
    }
} 