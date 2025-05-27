package com.huang.backend.mqtt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for console message requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleMessageRequestDto {

    /**
     * The console message to send to the drone
     */
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;

    /**
     * Optional priority level (LOW, NORMAL, HIGH)
     */
    private String priority = "NORMAL";

    /**
     * Whether to require acknowledgment from the drone
     */
    private boolean requireAck = false;
} 