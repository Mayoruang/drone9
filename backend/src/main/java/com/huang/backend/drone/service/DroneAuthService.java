package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneAuthRequestDto;
import com.huang.backend.drone.dto.DroneAuthResponseDto;

/**
 * Service for drone authentication
 */
public interface DroneAuthService {
    
    /**
     * Authenticate a drone client
     * 
     * @param authRequest the authentication request
     * @return authentication response with access token
     */
    DroneAuthResponseDto authenticateDrone(DroneAuthRequestDto authRequest);
} 