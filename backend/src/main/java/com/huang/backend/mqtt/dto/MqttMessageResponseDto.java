package com.huang.backend.mqtt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for MQTT message responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttMessageResponseDto {

    /**
     * Whether the message was sent successfully
     */
    private boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * The ID of the drone the message was sent to
     */
    private UUID droneId;

    /**
     * The topic the message was published to
     */
    private String topic;

    /**
     * Timestamp when the message was sent
     */
    private ZonedDateTime timestamp;

    /**
     * Message ID for tracking (optional)
     */
    private String messageId;

    /**
     * Error details if the message failed to send
     */
    private String errorDetails;
} 