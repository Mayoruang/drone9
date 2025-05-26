package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneDeleteResponseDto;
import com.huang.backend.drone.dto.DroneOfflineRequestDto;
import com.huang.backend.drone.dto.DroneOfflineResponseDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.mqtt.model.DroneOfflineCommand;
import com.huang.backend.mqtt.service.MqttPublisherService;
import com.huang.backend.registration.entity.DroneRegistrationRequest;
import com.huang.backend.registration.repository.DroneRegistrationRequestRepository;
import com.huang.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Implementation of the DroneManagementService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneManagementServiceImpl implements DroneManagementService {

    private final DroneRepository droneRepository;
    private final DroneRegistrationRequestRepository registrationRepository;
    private final MqttPublisherService mqttPublisherService;
    private final SecurityUtils securityUtils;
    private final SimpMessagingTemplate messagingTemplate;
    private final DroneInfluxDBService droneInfluxDBService;

    @Override
    @Transactional
    public DroneOfflineResponseDto setDroneOffline(DroneOfflineRequestDto request) {
        log.info("Setting drone offline: {}", request.getDroneId());
        
        // Find the drone
        Drone drone = droneRepository.findById(request.getDroneId())
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + request.getDroneId()));
        
        // Validate drone status
        if (drone.getCurrentStatus() == Drone.DroneStatus.OFFLINE) {
            return DroneOfflineResponseDto.builder()
                    .droneId(drone.getDroneId())
                    .serialNumber(drone.getSerialNumber())
                    .success(false)
                    .message("Drone is already offline")
                    .build();
        }
        
        // Validate drone is not flying
        if (drone.getCurrentStatus() == Drone.DroneStatus.FLYING) {
            return DroneOfflineResponseDto.builder()
                    .droneId(drone.getDroneId())
                    .serialNumber(drone.getSerialNumber())
                    .success(false)
                    .message("Cannot take drone offline while it is flying")
                    .build();
        }
        
        // Get current user
        String currentUser = securityUtils.getCurrentUsername().orElse("system");
        
        // Create timestamp for offline operation
        ZonedDateTime now = ZonedDateTime.now();
        
        // Send MQTT offline command to the drone
        boolean commandSent = false;
        try {
            // Create the offline command
            DroneOfflineCommand command = DroneOfflineCommand.builder()
                    .reason(request.getReason())
                    .timestamp(now)
                    .issuedBy(currentUser)
                    .gracePeriodSeconds(request.getGracePeriodSeconds())
                    .build();
            
            // Publish the command to the drone's command topic
            commandSent = mqttPublisherService.publishCommand(drone.getDroneId().toString(), command);
            
            log.info("Sent offline command to drone {} ({}): {}", drone.getSerialNumber(), drone.getDroneId(), commandSent);
        } catch (Exception e) {
            log.error("Error sending offline command to drone {}: {}", drone.getSerialNumber(), e.getMessage(), e);
        }
        
        // Update drone state in database
        drone.setCurrentStatus(Drone.DroneStatus.OFFLINE);
        drone.setOfflineReason(request.getReason());
        drone.setOfflineAt(now);
        drone.setOfflineBy(currentUser);
        
        // Save the updated drone
        droneRepository.save(drone);
        
        log.info("Drone {} set to OFFLINE status", drone.getSerialNumber());
        
        // Send WebSocket notification about drone offline status
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "DRONE_OFFLINE");
        notification.put("droneId", drone.getDroneId().toString());
        notification.put("serialNumber", drone.getSerialNumber());
        notification.put("status", "OFFLINE");
        notification.put("offlineReason", request.getReason());
        notification.put("offlineAt", now.toString());
        notification.put("offlineBy", currentUser);
        messagingTemplate.convertAndSend("/topic/drones/positions", notification);
        
        // Return response
        return DroneOfflineResponseDto.builder()
                .droneId(drone.getDroneId())
                .serialNumber(drone.getSerialNumber())
                .success(true)
                .message("Drone set offline successfully" + (commandSent ? " and notification sent" : ""))
                .reason(request.getReason())
                .offlineAt(now)
                .commandSent(commandSent)
                .build();
    }
    
    @Override
    @Transactional
    public DroneDeleteResponseDto deleteDrone(UUID droneId) {
        log.info("Deleting drone with ID: {}", droneId);
        
        try {
            // Find the drone
            Drone drone = droneRepository.findById(droneId)
                    .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
            
            String serialNumber = drone.getSerialNumber();
            
            // Check if drone is flying - should not delete flying drones
            // if (drone.getCurrentStatus() == Drone.DroneStatus.FLYING) {
            //     log.warn("Cannot delete drone while it is flying: {}", serialNumber);
            //     return DroneDeleteResponseDto.builder()
            //             .droneId(droneId)
            //             .serialNumber(serialNumber)
            //             .success(false)
            //             .message("Cannot delete drone while it is flying")
            //             .build();
            // }
            
            // Try to send an offline command if drone is not already offline
            // This attempt is best-effort, deletion proceeds regardless of command success.
            if (drone.getCurrentStatus() != Drone.DroneStatus.OFFLINE) {
                log.info("Attempting to send offline command before deletion for drone: {}", serialNumber);
                try {
                    // Get current user
                    String currentUser = securityUtils.getCurrentUsername().orElse("system");
                    
                    // Create offline command
                    DroneOfflineCommand command = DroneOfflineCommand.builder()
                            .reason("Drone is being deleted from the system")
                            .timestamp(ZonedDateTime.now())
                            .issuedBy(currentUser)
                            .gracePeriodSeconds(5)
                            .build();
                    
                    // Publish the command
                    mqttPublisherService.publishCommand(drone.getDroneId().toString(), command);
                } catch (Exception e) {
                    log.warn("Failed to send offline command before deletion: {}", e.getMessage());
                    // Continue with deletion even if command fails
                }
            }
            
            // Delete the drone (PostgreSQL)
            droneRepository.delete(drone);

            // Delete telemetry from InfluxDB
            try {
                droneInfluxDBService.deleteAllTelemetry(serialNumber);
            } catch (Exception ex) {
                log.warn("Failed to delete telemetry for drone {}: {}", serialNumber, ex.getMessage());
            }
            
            // Find and update registration request if exists
            if (drone.getRegistrationRequestId() != null) {
                registrationRepository.findById(drone.getRegistrationRequestId())
                        .ifPresent(request -> {
                            // Don't actually delete the registration record, just update its fields
                            request.setDroneId(null);
                            request.setStatus(DroneRegistrationRequest.RegistrationStatus.DELETED);
                            request.setAdminNotes(request.getAdminNotes() + "\nDrone deleted at " + 
                                               ZonedDateTime.now() + " by " + 
                                               securityUtils.getCurrentUsername().orElse("system"));
                            registrationRepository.save(request);
                        });
            }
            
            // Send WebSocket notification about drone deletion
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "DRONE_DELETED");
            notification.put("droneId", droneId.toString());
            notification.put("serialNumber", serialNumber);
            notification.put("timestamp", ZonedDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/drones/deleted", notification);
            
            log.info("Drone successfully deleted: {}", serialNumber);
            
            return DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .serialNumber(serialNumber)
                    .success(true)
                    .message("Drone successfully deleted")
                    .build();
            
        } catch (NoSuchElementException e) {
            log.error("Drone not found for deletion: {}", droneId);
            return DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .success(false)
                    .message("Drone not found: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error deleting drone {}: {}", droneId, e.getMessage(), e);
            return DroneDeleteResponseDto.builder()
                    .droneId(droneId)
                    .success(false)
                    .message("Error deleting drone: " + e.getMessage())
                    .build();
        }
    }
} 