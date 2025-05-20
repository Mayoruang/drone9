package com.huang.backend.registration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entity representing a drone registration request in the system.
 * This entity captures the initial request details and tracks the approval status.
 */
@Entity
@Table(name = "drone_registration_requests")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DroneRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "request_id", updatable = false, nullable = false)
    private UUID requestId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RegistrationStatus status;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @Column(name = "drone_id")
    private UUID droneId;
    
    /**
     * Enum representing the possible states of a drone registration request.
     */
    public enum RegistrationStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        DELETED
    }
    
    /**
     * Pre-persist hook to set default values before initial save
     */
    @PrePersist
    public void prePersist() {
        if (this.requestedAt == null) {
            this.requestedAt = ZonedDateTime.now();
        }
        if (this.status == null) {
            this.status = RegistrationStatus.PENDING_APPROVAL;
        }
    }
} 