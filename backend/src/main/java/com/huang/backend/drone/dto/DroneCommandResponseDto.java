package com.huang.backend.drone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for drone command responses
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DroneCommandResponseDto {

    /**
     * Whether the command was successfully processed
     */
    private boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * Unique command ID for tracking
     */
    private String commandId;

    /**
     * Target drone ID
     */
    private UUID droneId;

    /**
     * Command action that was executed
     */
    private String action;

    /**
     * Current command status
     */
    private DroneCommandDto.CommandStatus status;

    /**
     * Timestamp when command was issued
     */
    private ZonedDateTime issuedAt;

    /**
     * Expected timeout duration in seconds
     */
    private Integer timeoutSeconds;

    /**
     * Whether the command can be cancelled
     */
    private Boolean cancellable;

    /**
     * MQTT topic where the command was sent
     */
    private String mqttTopic;

    /**
     * Error code if command failed
     */
    private String errorCode;

    /**
     * Additional error details if command failed
     */
    private String errorDetails;

    /**
     * Helper method to create a successful response
     */
    public static DroneCommandResponseDto success(String commandId, UUID droneId, String action, String message) {
        return DroneCommandResponseDto.builder()
                .success(true)
                .commandId(commandId)
                .droneId(droneId)
                .action(action)
                .message(message)
                .status(DroneCommandDto.CommandStatus.SENT)
                .issuedAt(ZonedDateTime.now())
                .build();
    }

    /**
     * Helper method to create a failed response
     */
    public static DroneCommandResponseDto failure(UUID droneId, String action, String message, String errorCode) {
        return DroneCommandResponseDto.builder()
                .success(false)
                .droneId(droneId)
                .action(action)
                .message(message)
                .errorCode(errorCode)
                .status(DroneCommandDto.CommandStatus.FAILED)
                .issuedAt(ZonedDateTime.now())
                .build();
    }
} 