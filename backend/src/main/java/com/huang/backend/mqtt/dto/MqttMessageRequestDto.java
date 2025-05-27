package com.huang.backend.mqtt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * DTO for custom MQTT message requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttMessageRequestDto {

    /**
     * The MQTT topic to publish to
     */
    @NotBlank(message = "Topic cannot be blank")
    @Size(max = 255, message = "Topic cannot exceed 255 characters")
    private String topic;

    /**
     * The message content
     */
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    /**
     * Message type (CONSOLE, COMMAND, NOTIFICATION, etc.)
     */
    private String messageType = "CUSTOM";

    /**
     * MQTT Quality of Service level (0, 1, or 2)
     */
    @Min(value = 0, message = "QoS must be 0, 1, or 2")
    @Max(value = 2, message = "QoS must be 0, 1, or 2")
    private int qos = 1;

    /**
     * Whether the message should be retained by the broker
     */
    private boolean retained = false;
} 