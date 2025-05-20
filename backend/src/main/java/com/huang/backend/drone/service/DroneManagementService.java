package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneDeleteResponseDto;
import com.huang.backend.drone.dto.DroneOfflineRequestDto;
import com.huang.backend.drone.dto.DroneOfflineResponseDto;

import java.util.UUID;

/**
 * Service for drone management operations, including setting drones offline
 */
public interface DroneManagementService {
    
    /**
     * Set a drone offline
     * 
     * @param request the offline request
     * @return the offline response
     */
    DroneOfflineResponseDto setDroneOffline(DroneOfflineRequestDto request);
    
    /**
     * Delete a drone from the system
     * 
     * @param droneId the ID of the drone to delete
     * @return the delete response
     */
    DroneDeleteResponseDto deleteDrone(UUID droneId);
} 