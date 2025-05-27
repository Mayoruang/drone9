package com.huang.backend.mqtt.controller;

import com.huang.backend.mqtt.dto.ConsoleMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageResponseDto;
import com.huang.backend.mqtt.service.MqttMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller for sending MQTT messages to drones
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drones")
@RequiredArgsConstructor
public class MqttMessageController {

    private final MqttMessageService mqttMessageService;

    /**
     * Send a console message to a specific drone
     *
     * @param droneId the ID of the drone
     * @param request the console message request
     * @return response indicating success or failure
     */
    @PostMapping("/{droneId}/console-message")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER')")
    public ResponseEntity<MqttMessageResponseDto> sendConsoleMessage(
            @PathVariable UUID droneId,
            @Valid @RequestBody ConsoleMessageRequestDto request) {
        
        log.info("Sending console message to drone {}: {}", droneId, request.getMessage());
        
        MqttMessageResponseDto response = mqttMessageService.sendConsoleMessage(droneId, request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Send a custom MQTT message to a specific drone
     *
     * @param droneId the ID of the drone
     * @param request the MQTT message request
     * @return response indicating success or failure
     */
    @PostMapping("/{droneId}/mqtt-message")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER')")
    public ResponseEntity<MqttMessageResponseDto> sendMqttMessage(
            @PathVariable UUID droneId,
            @Valid @RequestBody MqttMessageRequestDto request) {
        
        log.info("Sending MQTT message to drone {} on topic {}: {}", 
                droneId, request.getTopic(), request.getMessage());
        
        MqttMessageResponseDto response = mqttMessageService.sendMqttMessage(droneId, request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Test endpoint to debug authentication and authorization
     */
    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        if (authentication != null) {
            result.put("authenticated", true);
            result.put("principal", authentication.getPrincipal());
            result.put("authorities", authentication.getAuthorities());
            result.put("name", authentication.getName());
        } else {
            result.put("authenticated", false);
        }
        return ResponseEntity.ok(result);
    }
} 