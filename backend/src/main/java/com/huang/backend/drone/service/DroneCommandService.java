package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneCommandDto;
import com.huang.backend.drone.dto.DroneCommandRequestDto;
import com.huang.backend.drone.dto.DroneCommandResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for drone command operations
 */
public interface DroneCommandService {

    /**
     * Send a command to a specific drone
     *
     * @param droneId the ID of the target drone
     * @param request the command request
     * @param username the username of the user issuing the command
     * @return command response
     */
    DroneCommandResponseDto sendCommand(UUID droneId, DroneCommandRequestDto request, String username);

    /**
     * Send a quick action command to a drone
     *
     * @param droneId the ID of the target drone
     * @param action the action to perform
     * @param username the username of the user issuing the command
     * @return command response
     */
    DroneCommandResponseDto sendQuickCommand(UUID droneId, String action, String username);

    /**
     * Cancel a pending command
     *
     * @param commandId the ID of the command to cancel
     * @param username the username of the user cancelling the command
     * @return whether the cancellation was successful
     */
    boolean cancelCommand(String commandId, String username);

    /**
     * Get command execution status
     *
     * @param commandId the ID of the command
     * @return command details
     */
    DroneCommandDto getCommandStatus(String commandId);

    /**
     * Get command history for a specific drone
     *
     * @param droneId the ID of the drone
     * @param limit maximum number of commands to return
     * @return list of recent commands
     */
    List<DroneCommandDto> getCommandHistory(UUID droneId, int limit);

    /**
     * Emergency stop all drones
     *
     * @param username the username of the user issuing the emergency stop
     * @return list of emergency stop responses
     */
    List<DroneCommandResponseDto> emergencyStopAll(String username);

    /**
     * Emergency stop a specific drone
     *
     * @param droneId the ID of the target drone
     * @param username the username of the user issuing the emergency stop
     * @return emergency stop response
     */
    DroneCommandResponseDto emergencyStop(UUID droneId, String username);

    /**
     * Send return to home command to a drone
     *
     * @param droneId the ID of the target drone
     * @param username the username of the user issuing the command
     * @return command response
     */
    DroneCommandResponseDto returnToHome(UUID droneId, String username);

    /**
     * Send land command to a drone
     *
     * @param droneId the ID of the target drone
     * @param username the username of the user issuing the command
     * @return command response
     */
    DroneCommandResponseDto land(UUID droneId, String username);

    /**
     * Send hover command to a drone
     *
     * @param droneId the ID of the target drone
     * @param username the username of the user issuing the command
     * @return command response
     */
    DroneCommandResponseDto hover(UUID droneId, String username);

    /**
     * Check if a drone is available for commands
     *
     * @param droneId the ID of the drone
     * @return whether the drone is available
     */
    boolean isDroneAvailable(UUID droneId);

    /**
     * Validate command parameters for specific action
     *
     * @param action the command action
     * @param parameters the command parameters
     * @return validation result
     */
    boolean validateCommandParameters(String action, Object parameters);
} 