package com.huang.backend.drone.dto;

import com.huang.backend.drone.entity.Drone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for drone status information
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
     * The timestamp of the last received heartbeat
     */
    private ZonedDateTime lastHeartbeat;
    
    /**
     * Location data if available
     */
    private Double latitude;
    private Double longitude;
    private Double altitude;
    
    /**
     * Battery data if available
     */
    private Double batteryLevel;
    
    /**
     * Whether the drone is currently connected
     */
    private boolean connected;
    
    /**
     * Offline information if the drone is offline
     */
    private String offlineReason;
    private ZonedDateTime offlineAt;
    private String offlineBy;
    private String lastFarewellMessage;
    
    /**
     * Factory method to create DTO from entity
     * 
     * @param drone the drone entity
     * @return drone status DTO
     */
    public static DroneStatusDto fromEntity(Drone drone) {
        return DroneStatusDto.builder()
                .droneId(drone.getDroneId())
                .serialNumber(drone.getSerialNumber())
                .model(drone.getModel())
                .status(drone.getCurrentStatus())
                .lastHeartbeat(drone.getLastHeartbeatAt())
                .connected(drone.getCurrentStatus() != Drone.DroneStatus.OFFLINE)
                .offlineReason(drone.getOfflineReason())
                .offlineAt(drone.getOfflineAt())
                .offlineBy(drone.getOfflineBy())
                .lastFarewellMessage(drone.getLastFarewellMessage())
                .build();
    }
} 