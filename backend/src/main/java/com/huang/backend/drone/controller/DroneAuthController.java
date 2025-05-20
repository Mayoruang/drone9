package com.huang.backend.drone.controller;

import com.huang.backend.drone.dto.DroneAuthRequestDto;
import com.huang.backend.drone.dto.DroneAuthResponseDto;
import com.huang.backend.drone.service.DroneAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Controller for drone authentication
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones/auth")
@RequiredArgsConstructor
public class DroneAuthController {

    private final DroneAuthService droneAuthService;
    
    /**
     * Authenticate a drone client
     * 
     * @param authRequest the authentication request
     * @return authentication response with access token
     */
    @PostMapping
    public ResponseEntity<DroneAuthResponseDto> authenticateDrone(
            @Valid @RequestBody DroneAuthRequestDto authRequest) {
        
        log.info("Received drone authentication request for serial number: {}", 
                authRequest.getSerialNumber());
        
        DroneAuthResponseDto response = droneAuthService.authenticateDrone(authRequest);
        return ResponseEntity.ok(response);
    }
} 