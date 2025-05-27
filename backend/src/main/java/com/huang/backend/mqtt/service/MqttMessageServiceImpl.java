package com.huang.backend.mqtt.service;

import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.mqtt.dto.ConsoleMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageRequestDto;
import com.huang.backend.mqtt.dto.MqttMessageResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of MQTT message service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageServiceImpl implements MqttMessageService {

    private final MqttClient mqttClient;
    private final DroneRepository droneRepository;
    private final MqttPublisherService mqttPublisherService;

    @Override
    public MqttMessageResponseDto sendConsoleMessage(UUID droneId, ConsoleMessageRequestDto request) {
        log.info("Sending console message to drone {}: {}", droneId, request.getMessage());

        // Validate drone exists
        Optional<Drone> droneOpt = droneRepository.findById(droneId);
        if (droneOpt.isEmpty()) {
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Drone not found")
                    .droneId(droneId)
                    .timestamp(ZonedDateTime.now())
                    .errorDetails("No drone found with ID: " + droneId)
                    .build();
        }

        Drone drone = droneOpt.get();
        
        // Check if drone is online
        if (drone.getCurrentStatus() == Drone.DroneStatus.OFFLINE) {
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Drone is offline")
                    .droneId(droneId)
                    .timestamp(ZonedDateTime.now())
                    .errorDetails("Cannot send message to offline drone")
                    .build();
        }

        // Create console message payload
        Map<String, Object> consoleMessage = new HashMap<>();
        consoleMessage.put("type", "CONSOLE_MESSAGE");
        consoleMessage.put("message", request.getMessage());
        consoleMessage.put("priority", request.getPriority());
        consoleMessage.put("requireAck", request.isRequireAck());
        consoleMessage.put("timestamp", ZonedDateTime.now().toString());
        consoleMessage.put("messageId", UUID.randomUUID().toString());

        // Use a dedicated console topic instead of the general command topic
        String topic = drone.getMqttTopicCommands().replace("/commands", "/console");
        
        try {
            // Send message using MQTT publisher service
            boolean success = mqttPublisherService.publishMessage(topic, consoleMessage);
            
            if (success) {
                log.info("Console message sent successfully to drone {}", droneId);
                return MqttMessageResponseDto.builder()
                        .success(true)
                        .message("Console message sent successfully")
                        .droneId(droneId)
                        .topic(topic)
                        .timestamp(ZonedDateTime.now())
                        .messageId(consoleMessage.get("messageId").toString())
                        .build();
            } else {
                log.error("Failed to send console message to drone {}", droneId);
                return MqttMessageResponseDto.builder()
                        .success(false)
                        .message("Failed to send console message")
                        .droneId(droneId)
                        .topic(topic)
                        .timestamp(ZonedDateTime.now())
                        .errorDetails("MQTT publish failed")
                        .build();
            }
        } catch (Exception e) {
            log.error("Error sending console message to drone {}: {}", droneId, e.getMessage(), e);
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Error sending console message")
                    .droneId(droneId)
                    .topic(topic)
                    .timestamp(ZonedDateTime.now())
                    .errorDetails(e.getMessage())
                    .build();
        }
    }

    @Override
    public MqttMessageResponseDto sendMqttMessage(UUID droneId, MqttMessageRequestDto request) {
        log.info("Sending MQTT message to drone {} on topic {}: {}", 
                droneId, request.getTopic(), request.getMessage());

        // Validate drone exists
        Optional<Drone> droneOpt = droneRepository.findById(droneId);
        if (droneOpt.isEmpty()) {
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Drone not found")
                    .droneId(droneId)
                    .timestamp(ZonedDateTime.now())
                    .errorDetails("No drone found with ID: " + droneId)
                    .build();
        }

        Drone drone = droneOpt.get();
        
        // Check if drone is online
        if (drone.getCurrentStatus() == Drone.DroneStatus.OFFLINE) {
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Drone is offline")
                    .droneId(droneId)
                    .timestamp(ZonedDateTime.now())
                    .errorDetails("Cannot send message to offline drone")
                    .build();
        }

        // Create custom message payload
        Map<String, Object> customMessage = new HashMap<>();
        customMessage.put("type", request.getMessageType());
        customMessage.put("message", request.getMessage());
        customMessage.put("timestamp", ZonedDateTime.now().toString());
        customMessage.put("messageId", UUID.randomUUID().toString());

        try {
            // Send message directly using MQTT client for custom topics
            MqttMessage mqttMessage = new MqttMessage(request.getMessage().getBytes());
            mqttMessage.setQos(request.getQos());
            mqttMessage.setRetained(request.isRetained());

            if (!mqttClient.isConnected()) {
                log.warn("MQTT client not connected, attempting to reconnect...");
                mqttClient.reconnect();
            }

            mqttClient.publish(request.getTopic(), mqttMessage);
            
            log.info("Custom MQTT message sent successfully to drone {} on topic {}", 
                    droneId, request.getTopic());
            
            return MqttMessageResponseDto.builder()
                    .success(true)
                    .message("MQTT message sent successfully")
                    .droneId(droneId)
                    .topic(request.getTopic())
                    .timestamp(ZonedDateTime.now())
                    .messageId(customMessage.get("messageId").toString())
                    .build();
                    
        } catch (MqttException e) {
            log.error("MQTT error sending message to drone {}: {}", droneId, e.getMessage(), e);
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("MQTT error sending message")
                    .droneId(droneId)
                    .topic(request.getTopic())
                    .timestamp(ZonedDateTime.now())
                    .errorDetails("MQTT Exception: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error sending MQTT message to drone {}: {}", droneId, e.getMessage(), e);
            return MqttMessageResponseDto.builder()
                    .success(false)
                    .message("Error sending MQTT message")
                    .droneId(droneId)
                    .topic(request.getTopic())
                    .timestamp(ZonedDateTime.now())
                    .errorDetails(e.getMessage())
                    .build();
        }
    }
} 