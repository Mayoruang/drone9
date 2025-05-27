package com.huang.backend.mqtt.service;

import com.huang.backend.mqtt.dto.ConsoleMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageResponseDto;

import java.util.UUID;

/**
 * Service interface for sending MQTT messages to drones
 */
public interface MqttMessageService {

    /**
     * Send a console message to a specific drone
     *
     * @param droneId the ID of the drone
     * @param request the console message request
     * @return response indicating success or failure
     */
    MqttMessageResponseDto sendConsoleMessage(UUID droneId, ConsoleMessageRequestDto request);

    /**
     * Send a custom MQTT message to a specific drone
     *
     * @param droneId the ID of the drone
     * @param request the MQTT message request
     * @return response indicating success or failure
     */
    MqttMessageResponseDto sendMqttMessage(UUID droneId, MqttMessageRequestDto request);
} 