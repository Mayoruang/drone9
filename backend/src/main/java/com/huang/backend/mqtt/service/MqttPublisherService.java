package com.huang.backend.mqtt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

/**
 * Service for publishing messages to MQTT topics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttPublisherService {

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a command to a specific drone
     *
     * @param droneId the ID of the drone
     * @param command the command object to publish
     * @param <T> the type of the command
     * @return true if the message was published successfully
     */
    public <T> boolean publishCommand(String droneId, T command) {
        String topic = "drones/" + droneId + "/commands";
        return publishMessage(topic, command);
    }

    /**
     * Publishes a message to a specific topic
     *
     * @param topic the topic to publish to
     * @param payload the payload to publish
     * @param <T> the type of the payload
     * @return true if the message was published successfully
     */
    public <T> boolean publishMessage(String topic, T payload) {
        log.info("🔄 开始发布MQTT消息到主题: {}", topic);
        
        try {
            // 详细检查客户端状态
            if (mqttClient == null) {
                log.error("❌ MQTT客户端为null，无法发布消息");
                return false;
            }
            
            log.info("📋 MQTT客户端信息:");
            log.info("   - 客户端ID: {}", mqttClient.getClientId());
            log.info("   - 服务器URI: {}", mqttClient.getServerURI());
            log.info("   - 是否已连接: {}", mqttClient.isConnected());
            
            if (!mqttClient.isConnected()) {
                log.warn("❌ MQTT客户端未连接，无法发布消息到主题: {}", topic);
                log.warn("   尝试检查连接状态和重连...");
                
                // 尝试重连
                try {
                    if (!mqttClient.isConnected()) {
                        log.info("🔄 尝试重新连接MQTT客户端...");
                        mqttClient.reconnect();
                        log.info("✅ MQTT客户端重连成功");
                    }
                } catch (MqttException reconnectEx) {
                    log.error("❌ MQTT客户端重连失败: {}", reconnectEx.getMessage(), reconnectEx);
                    return false;
                }
            }

            // 序列化消息
            String jsonPayload;
            try {
                jsonPayload = objectMapper.writeValueAsString(payload);
                log.info("📝 消息序列化成功，长度: {} 字符", jsonPayload.length());
                log.debug("📄 消息内容: {}", jsonPayload);
            } catch (Exception serEx) {
                log.error("❌ 消息序列化失败: {}", serEx.getMessage(), serEx);
                return false;
            }

            // 创建MQTT消息
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);  // 至少一次传递
            message.setRetained(false);  // 不保留消息
            
            log.info("📦 MQTT消息属性:");
            log.info("   - QoS: {}", message.getQos());
            log.info("   - Retained: {}", message.isRetained());
            log.info("   - Payload大小: {} 字节", message.getPayload().length);

            // 发布消息
            log.info("📤 正在发布消息到主题: {}", topic);
            long startTime = System.currentTimeMillis();
            
            mqttClient.publish(topic, message);
            
            long publishTime = System.currentTimeMillis() - startTime;
            log.info("✅ 已发布消息到主题: {} (耗时: {}ms)", topic, publishTime);
            return true;
            
        } catch (MqttException e) {
            log.error("❌ MQTT消息发布失败 - MQTT异常: {}", e.getMessage(), e);
            log.error("   错误码: {}", e.getReasonCode());
            log.error("   错误原因: {}", e.getCause() != null ? e.getCause().getMessage() : "未知");
            return false;
        } catch (Exception e) {
            log.error("❌ 消息序列化或发布过程中发生错误: {}", e.getMessage(), e);
            return false;
        }
    }
} 