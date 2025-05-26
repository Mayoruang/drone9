package com.huang.backend.mqtt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import com.huang.backend.drone.websocket.DroneWebSocketHandler;
import com.huang.backend.mqtt.model.CommandResponse;
import com.huang.backend.mqtt.model.DroneTelemetryData;
import com.huang.backend.mqtt.model.FarewellMessage;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * MQTT Subscriber Service that listens for drone telemetry data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSubscriberService implements MqttCallback {

    private final MqttClient mqttClient;
    private final TimeseriesService timeseriesService;
    private final DroneRepository droneRepository;
    private final ObjectMapper objectMapper;
    private final DroneWebSocketHandler droneWebSocketHandler;

    @Value("${mqtt.topics.telemetry:drones/+/telemetry}")
    private String telemetryTopic;
    
    @Value("${mqtt.topics.responses:drones/+/responses}")
    private String responsesTopic;
    
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private static final Pattern DRONE_ID_PATTERN = Pattern.compile("drones/([^/]+)/telemetry");
    private static final Pattern RESPONSE_ID_PATTERN = Pattern.compile("drones/([^/]+)/responses");

    /**
     * Initialize the MQTT subscription after the bean is constructed
     */
    @PostConstruct
    public void init() {
        try {
            setupMqttClient();
            initialized.set(true);
        } catch (MqttException e) {
            // 初始化失败时记录错误并计划重试，但不抛出异常以允许应用继续启动
            log.error("MQTT初始化异常: {}", e.getMessage(), e);
        }
    }
    
    private void setupMqttClient() throws MqttException {
        try {
            // 如果断开连接，尝试重新连接
            if (!mqttClient.isConnected()) {
                log.info("尝试连接到MQTT代理: {}", mqttClient.getServerURI());
                mqttClient.connect();
                log.info("成功连接到MQTT代理");
            }
            
            mqttClient.setCallback(this);
            mqttClient.subscribe(telemetryTopic);
            log.info("已订阅MQTT主题: {}", telemetryTopic);
            
            mqttClient.subscribe(responsesTopic);
            log.info("已订阅MQTT命令响应主题: {}", responsesTopic);
        } catch (MqttException e) {
            log.error("MQTT配置失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 定时检查MQTT连接状态并在必要时重连
     */
    @Scheduled(fixedDelay = 60000) // 每60秒检查一次
    public void checkConnection() {
        if (!initialized.get()) {
            log.debug("MQTT服务尚未初始化，跳过连接检查");
            return;
        }
        
        if (!mqttClient.isConnected() && !reconnecting.get()) {
            reconnecting.set(true);
            try {
                log.info("检测到MQTT连接已断开，尝试重新连接");
                setupMqttClient();
                log.info("MQTT连接重连成功");
            } catch (MqttException e) {
                log.error("MQTT重连失败: {}", e.getMessage(), e);
            } finally {
                reconnecting.set(false);
            }
        }
    }

    /**
     * Clean up resources before the bean is destroyed
     */
    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.unsubscribe(telemetryTopic);
                log.info("已取消订阅MQTT主题: {}", telemetryTopic);
                mqttClient.unsubscribe(responsesTopic);
                log.info("已取消订阅MQTT命令响应主题: {}", responsesTopic);
                mqttClient.disconnect();
                log.info("已断开MQTT连接");
            }
            mqttClient.close();
        } catch (MqttException e) {
            log.error("取消MQTT订阅失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT连接丢失: {}", cause.getMessage(), cause);
        // 连接丢失时尝试重连
        if (!reconnecting.get()) {
            new Thread(() -> {
                reconnecting.set(true);
                try {
                    // 等待一段时间后重试，避免立即重连
                    Thread.sleep(5000);
                    log.info("尝试重连MQTT...");
                    setupMqttClient();
                } catch (Exception e) {
                    log.error("MQTT重连失败: {}", e.getMessage(), e);
                } finally {
                    reconnecting.set(false);
                }
            }).start();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {
            log.debug("收到MQTT消息，主题: {}, 内容: {}", topic, new String(message.getPayload()));
            
            if (topic.matches(telemetryTopic.replace("+", ".*"))) {
                handleTelemetryMessage(topic, message);
            } else if (topic.matches(responsesTopic.replace("+", ".*"))) {
                handleResponseMessage(topic, message);
            } else {
                log.warn("收到未知主题的MQTT消息: {}", topic);
            }
        } catch (Exception e) {
            log.error("处理MQTT消息失败: {}", e.getMessage(), e);
        }
    }

    private void handleTelemetryMessage(String topic, MqttMessage message) throws Exception {
        // Extract drone ID from topic
        String droneId = extractDroneId(topic, DRONE_ID_PATTERN);
        if (droneId == null) {
            log.warn("无法从遥测主题中提取无人机ID: {}", topic);
            return;
        }
        
        // Check for farewell message by inspecting the payload first
        String messageStr = new String(message.getPayload());
        if (messageStr.contains("\"type\":\"FAREWELL\"") || messageStr.contains("\"type\": \"FAREWELL\"")) {
            handleFarewellMessage(droneId, message);
            return;
        }
        
        // Parse JSON payload for normal telemetry
        DroneTelemetryData telemetryData = objectMapper.readValue(message.getPayload(), DroneTelemetryData.class);
        
        // Always use drone ID from topic (UUID) instead of UUID from payload
        telemetryData.setDroneId(droneId);
        
        // Set timestamp if not present in payload
        if (telemetryData.getTimestamp() == null) {
            telemetryData.setTimestamp(Instant.now());
        }
        
        // Store data in InfluxDB
        timeseriesService.writeTelemetryData(telemetryData);
        
        // Update drone's last heartbeat timestamp in PostgreSQL
        updateDroneHeartbeat(droneId);
        
        // 将遥测数据推送到WebSocket
        sendTelemetryToWebSocket(droneId, telemetryData);
    }
    
    private void handleResponseMessage(String topic, MqttMessage message) throws Exception {
        // Extract drone ID from topic
        String droneId = extractDroneId(topic, RESPONSE_ID_PATTERN);
        if (droneId == null) {
            log.warn("无法从响应主题中提取无人机ID: {}", topic);
            return;
        }
        
        // Parse JSON payload
        CommandResponse response = objectMapper.readValue(message.getPayload(), CommandResponse.class);
        
        // Log the command response
        log.info("收到无人机命令响应: 无人机={}, 命令ID={}, 状态={}", 
                droneId, response.getCommandId(), response.getStatus());
        
        // Additional processing of command responses could be added here
        // For example, updating command status in a database, notifying users via WebSocket, etc.
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for subscriber
    }
    
    /**
     * Extract drone ID from topic using regex
     * 
     * @param topic the MQTT topic
     * @param pattern the regex pattern to use
     * @return the extracted drone ID or null if not found
     */
    private String extractDroneId(String topic, Pattern pattern) {
        Matcher matcher = pattern.matcher(topic);
        return matcher.matches() ? matcher.group(1) : null;
    }
    
    /**
     * Update the drone's last heartbeat timestamp in the database
     * 
     * @param droneId the UUID of the drone (extracted from MQTT topic)
     */
    private void updateDroneHeartbeat(String droneId) {
        try {
            // droneId is now UUID from topic, not serial number
            UUID droneUuid = UUID.fromString(droneId);
            Optional<Drone> droneOpt = droneRepository.findById(droneUuid);
            if (droneOpt.isPresent()) {
                Drone drone = droneOpt.get();
                ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
                drone.setLastHeartbeatAt(now);
                droneRepository.save(drone);
                log.debug("已更新无人机{}({})的最后心跳时间", drone.getSerialNumber(), droneId);
            } else {
                log.warn("未找到UUID为{}的无人机", droneId);
            }
        } catch (IllegalArgumentException e) {
            log.error("无效的UUID格式: {}", droneId, e);
        } catch (Exception e) {
            log.error("更新无人机心跳时间失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将遥测数据转换并推送到WebSocket
     * 
     * @param droneId UUID of the drone (extracted from MQTT topic)
     * @param telemetryData 遥测数据
     */
    private void sendTelemetryToWebSocket(String droneId, DroneTelemetryData telemetryData) {
        try {
            // droneId is now UUID from topic, not serial number
            UUID droneUuid = UUID.fromString(droneId);
            Optional<Drone> droneOpt = droneRepository.findById(droneUuid);
            if (droneOpt.isPresent()) {
                Drone drone = droneOpt.get();
                
                // 转换为DroneTelemetryDto - use serial number for compatibility
                DroneTelemetryDto dto = DroneTelemetryDto.builder()
                    .droneId(drone.getSerialNumber()) // Use serial number for frontend compatibility
                    .timestamp(telemetryData.getTimestamp())
                    .batteryLevel(telemetryData.getBatteryLevel())
                    .batteryVoltage(telemetryData.getBatteryVoltage())
                    .latitude(telemetryData.getLatitude())
                    .longitude(telemetryData.getLongitude())
                    .altitude(telemetryData.getAltitude())
                    .speed(telemetryData.getSpeed())
                    .heading(telemetryData.getHeading())
                    .satellites(telemetryData.getSatellites())
                    .signalStrength(telemetryData.getSignalStrength())
                    .flightMode(telemetryData.getFlightMode())
                    .temperature(telemetryData.getTemperature())
                    .status(telemetryData.getStatus())
                    .build();
                
                // 如果遥测中有status字段，更新无人机状态
                if (telemetryData.getStatus() != null) {
                    try {
                        Drone.DroneStatus newStatus = Drone.DroneStatus.valueOf(telemetryData.getStatus());
                        if (drone.getCurrentStatus() != newStatus) {
                            drone.setCurrentStatus(newStatus);
                            droneRepository.save(drone);
                            log.info("根据遥测数据更新无人机{}({})状态为: {}", drone.getSerialNumber(), droneId, newStatus);
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("无人机{}({})发送了无效的状态值: {}", drone.getSerialNumber(), droneId, telemetryData.getStatus());
                    }
                }
                
                // 通过WebSocket处理器发送更新
                droneWebSocketHandler.sendDroneUpdate(drone.getDroneId(), dto);
            } else {
                log.warn("WebSocket推送失败：未找到UUID为{}的无人机", droneId);
            }
        } catch (IllegalArgumentException e) {
            log.error("WebSocket推送失败：无效的UUID格式: {}", droneId, e);
        } catch (Exception e) {
            log.error("发送遥测数据到WebSocket失败: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle a farewell message from a drone before it goes offline
     * 
     * @param droneId the UUID of the drone (extracted from MQTT topic)
     * @param message the MQTT message
     */
    private void handleFarewellMessage(String droneId, MqttMessage message) {
        try {
            // Parse the farewell message
            FarewellMessage farewell = objectMapper.readValue(message.getPayload(), FarewellMessage.class);
            
            log.info("收到无人机{}的告别消息: {}", droneId, farewell.getMessage());
            
            // Find the drone in the database using UUID
            UUID droneUuid = UUID.fromString(droneId);
            Optional<Drone> droneOpt = droneRepository.findById(droneUuid);
            if (droneOpt.isPresent()) {
                Drone drone = droneOpt.get();
                
                // Store the farewell message
                drone.setLastFarewellMessage(farewell.getMessage());
                
                // Update the drone's status to OFFLINE if not already
                if (drone.getCurrentStatus() != Drone.DroneStatus.OFFLINE) {
                    ZonedDateTime now = ZonedDateTime.ofInstant(
                        farewell.getTimestamp() != null ? farewell.getTimestamp() : Instant.now(), 
                        ZoneId.systemDefault());
                    
                    drone.setCurrentStatus(Drone.DroneStatus.OFFLINE);
                    drone.setOfflineAt(now);
                    
                    if (drone.getOfflineReason() == null) {
                        drone.setOfflineReason("Drone initiated shutdown: " + farewell.getMessage());
                    }
                    
                    if (farewell.getIssuedBy() != null && drone.getOfflineBy() == null) {
                        drone.setOfflineBy(farewell.getIssuedBy());
                    }
                    
                    log.info("更新无人机{}({})状态为离线", drone.getSerialNumber(), droneId);
                }
                
                // Save the updated drone
                droneRepository.save(drone);
                
                // Create a telemetry DTO for the farewell message
                DroneTelemetryDto dto = DroneTelemetryDto.builder()
                    .droneId(drone.getSerialNumber()) // Use serial number for frontend compatibility
                    .timestamp(farewell.getTimestamp())
                    .batteryLevel(farewell.getBatteryRemaining())
                    .build();
                
                // Send a WebSocket message to notify clients
                droneWebSocketHandler.sendDroneUpdate(drone.getDroneId(), dto);
            } else {
                log.warn("收到未知无人机UUID={}的告别消息", droneId);
            }
        } catch (IllegalArgumentException e) {
            log.error("处理告别消息失败：无效的UUID格式: {}", droneId, e);
        } catch (Exception e) {
            log.error("处理无人机告别消息失败: {}", e.getMessage(), e);
        }
    }
} 