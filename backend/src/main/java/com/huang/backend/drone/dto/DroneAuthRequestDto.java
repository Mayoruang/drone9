package com.huang.backend.drone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for drone authentication request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneAuthRequestDto {
    
    /**
     * The drone serial number
     */
    @NotBlank(message = "Serial number is required")
    private String serialNumber;
    
    /**
     * The drone password or secret key
     */
    @NotBlank(message = "Password is required")
    private String password;
} 