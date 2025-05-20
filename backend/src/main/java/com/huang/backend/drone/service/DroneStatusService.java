package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving drone status information
 */
public interface DroneStatusService {

    /**
     * Get all drones with their current status
     *
     * @return a list of all drones with status information
     */
    List<DroneStatusDto> getAllDronesStatus();

    /**
     * Get status information for a specific drone
     *
     * @param droneId the ID of the drone
     * @return status information for the specified drone
     */
    DroneStatusDto getDroneStatus(UUID droneId);
    
    /**
     * Get drones filtered by status
     *
     * @param status the status to filter by, if null returns all drones
     * @return a list of drones with the specified status
     */
    List<DroneStatusDto> getDronesByStatus(Drone.DroneStatus status);
    
    /**
     * Get latest telemetry data for a specific drone
     *
     * @param droneId the ID of the drone
     * @return the latest telemetry data for the specified drone
     */
    DroneTelemetryDto getLatestTelemetry(UUID droneId);
    
    /**
     * Get historical telemetry data for a specific drone within a time range
     *
     * @param droneId the ID of the drone
     * @param start the start time
     * @param end the end time
     * @param limit the maximum number of records to return
     * @return a list of telemetry data points within the specified time range
     */
    List<DroneTelemetryDto> getTelemetryHistory(UUID droneId, Instant start, Instant end, int limit);
} 