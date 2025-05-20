package com.huang.backend.controller;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.service.DroneStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处理无人机WebSocket消息的控制器
 */
@Controller
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final DroneStatusService droneStatusService;

    public WebSocketController(SimpMessagingTemplate messagingTemplate, DroneStatusService droneStatusService) {
        this.messagingTemplate = messagingTemplate;
        this.droneStatusService = droneStatusService;
    }

    /**
     * 回显消息 - 用于测试WebSocket连接
     */
    @MessageMapping("/main/echo")
    @SendTo("/topic/main/echo")
    public String echo(String message) {
        log.info("收到echo消息: {}", message);
        return "服务器回应: " + message;
    }

    /**
     * 无人机位置获取请求
     */
    @MessageMapping("/requestDronesData")
    public void getDronePositions() {
        log.info("收到无人机位置数据请求");
        
        try {
            // 获取所有无人机的状态
            List<DroneStatusDto> allDrones = droneStatusService.getAllDronesStatus();
            
            // 将无人机状态转换为前端需要的格式
            List<Map<String, Object>> positions = allDrones.stream()
                .map(drone -> {
                    Map<String, Object> position = new HashMap<>();
                    position.put("droneId", drone.getDroneId());
                    position.put("serialNumber", drone.getSerialNumber());
                    position.put("model", drone.getModel());
                    position.put("status", drone.getStatus());
                    position.put("latitude", drone.getLatitude() != null ? drone.getLatitude() : 41.8057);  // 默认沈阳坐标
                    position.put("longitude", drone.getLongitude() != null ? drone.getLongitude() : 123.4315);
                    position.put("altitude", drone.getAltitude() != null ? drone.getAltitude() : 0);
                    position.put("batteryLevel", drone.getBatteryLevel() != null ? drone.getBatteryLevel() : 0);
                    position.put("lastHeartbeat", drone.getLastHeartbeat());
                    return position;
                })
                .collect(Collectors.toList());
            
            log.info("发送{}架无人机的位置数据", positions.size());
            messagingTemplate.convertAndSend("/topic/drones/positions", positions);
        } catch (Exception e) {
            log.error("获取无人机位置数据时出错: {}", e.getMessage(), e);
            // 发生错误时返回空列表
            messagingTemplate.convertAndSend("/topic/drones/positions", Collections.emptyList());
        }
    }
} 