package com.huang.backend.drone.dto;

import com.huang.backend.drone.entity.Drone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for drone status information
 * 匹配前端期望的DroneData格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneStatusDto {
    
    /**
     * The drone's unique identifier
     */
    private UUID droneId;
    
    /**
     * The drone's serial number
     */
    private String serialNumber;
    
    /**
     * The drone's model
     */
    private String model;
    
    /**
     * The drone's current status
     */
    private Drone.DroneStatus status;
    
    /**
     * 批准时间
     */
    private ZonedDateTime approvedAt;
    
    /**
     * 创建时间
     */
    private ZonedDateTime createdAt;
    
    /**
     * 更新时间
     */
    private ZonedDateTime updatedAt;
    
    /**
     * 电池电量百分比
     */
    private Double batteryPercentage;
    
    /**
     * 当前位置
     */
    private DronePosition position;
    
    /**
     * 当前速度（米/秒）
     */
    private Double speed;
    
    /**
     * 最后心跳时间
     */
    private ZonedDateTime lastHeartbeat;
    
    /**
     * 飞行模式
     */
    private String flightMode;
    
    /**
     * 离线时间
     */
    private ZonedDateTime offlineAt;
    
    /**
     * 离线原因
     */
    private String offlineReason;
    
    /**
     * 操作人员
     */
    private String offlineBy;
    
    /**
     * 最后告别消息
     */
    private String lastFarewellMessage;
    
    /**
     * 是否已发送离线通知
     */
    private Boolean offlineNotificationSent;
    
    /**
     * 关联的地理围栏ID列表
     */
    private List<UUID> geofenceIds;
    
    /**
     * MQTT配置
     */
    private MqttConfig mqtt;
    
    /**
     * Whether the drone is currently connected
     */
    private boolean connected;
    
    // 为了向后兼容，保留原有字段
    /**
     * Location data if available (deprecated, use position instead)
     */
    @Deprecated
    private Double latitude;
    @Deprecated
    private Double longitude;
    @Deprecated
    private Double altitude;
    
    /**
     * Battery data if available (deprecated, use batteryPercentage instead)
     */
    @Deprecated
    private Double batteryLevel;
    
    /**
     * 位置信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DronePosition {
        /** 纬度 */
        private Double latitude;
        /** 经度 */
        private Double longitude;
        /** 海拔高度（米） */
        private Double altitude;
    }
    
    /**
     * MQTT配置内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MqttConfig {
        private String username;
        private String topicTelemetry;
        private String topicCommands;
    }
    
    /**
     * Factory method to create DTO from entity
     * 
     * @param drone the drone entity
     * @return drone status DTO
     */
    public static DroneStatusDto fromEntity(Drone drone) {
        DroneStatusDto dto = DroneStatusDto.builder()
                .droneId(drone.getDroneId())
                .serialNumber(drone.getSerialNumber())
                .model(drone.getModel())
                .status(drone.getCurrentStatus())
                .approvedAt(drone.getApprovedAt())
                .lastHeartbeat(drone.getLastHeartbeatAt())
                .connected(drone.getCurrentStatus() != Drone.DroneStatus.OFFLINE)
                .offlineReason(drone.getOfflineReason())
                .offlineAt(drone.getOfflineAt())
                .offlineBy(drone.getOfflineBy())
                .lastFarewellMessage(drone.getLastFarewellMessage())
                .mqtt(MqttConfig.builder()
                        .username(drone.getMqttUsername())
                        .topicTelemetry(drone.getMqttTopicTelemetry())
                        .topicCommands(drone.getMqttTopicCommands())
                        .build())
                .build();
        
        // 向后兼容字段
        dto.setBatteryLevel(dto.getBatteryPercentage());
        if (dto.getPosition() != null) {
            dto.setLatitude(dto.getPosition().getLatitude());
            dto.setLongitude(dto.getPosition().getLongitude());
            dto.setAltitude(dto.getPosition().getAltitude());
        }
        
        return dto;
    }
} 