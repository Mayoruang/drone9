package com.huang.backend.drone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entity representing an approved drone in the system.
 * This entity is created after a registration request is approved.
 */
@Entity
@Table(name = "drones")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Drone {

    @Id
    @Column(name = "drone_id", updatable = false, nullable = false)
    private UUID droneId;

    @Column(name = "serial_number", unique = true, nullable = false)
    private String serialNumber;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "registration_request_id", nullable = false, unique = true)
    private UUID registrationRequestId;

    @Column(name = "approved_at", nullable = false)
    private ZonedDateTime approvedAt;

    @Column(name = "mqtt_broker_url", nullable = false)
    private String mqttBrokerUrl;

    @Column(name = "mqtt_username", nullable = false, unique = true)
    private String mqttUsername;

    @Column(name = "mqtt_password_hash", nullable = false)
    private String mqttPasswordHash;

    @Column(name = "mqtt_topic_telemetry", nullable = false)
    private String mqttTopicTelemetry;

    @Column(name = "mqtt_topic_commands", nullable = false)
    private String mqttTopicCommands;

    @Column(name = "last_heartbeat_at")
    private ZonedDateTime lastHeartbeatAt;

    @Column(name = "current_status")
    @Enumerated(EnumType.STRING)
    private DroneStatus currentStatus;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * Enum representing the possible statuses of a drone.
     */
    public enum DroneStatus {
        OFFLINE,
        ONLINE,
        FLYING,
        IDLE,
        ERROR
    }

    /**
     * Pre-persist hook to set default values before initial save
     */
    @PrePersist
    public void prePersist() {
        if (this.droneId == null) {
            this.droneId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = ZonedDateTime.now();
        }
        if (this.currentStatus == null) {
            this.currentStatus = DroneStatus.OFFLINE;
        }
    }

    /**
     * Pre-update hook to update the updatedAt timestamp
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
} 