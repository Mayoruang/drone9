package com.huang.backend.geofence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entity representing geofence statistics
 */
@Entity
@Table(name = "geofence_statistics")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GeofenceStatistics {

    @Id
    @Column(name = "geofence_id")
    private UUID geofenceId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "geofence_id")
    private Geofence geofence;

    @Column(name = "drone_count", nullable = false)
    private Integer droneCount = 0;

    @Column(name = "violation_count", nullable = false)
    private Integer violationCount = 0;

    @Column(name = "last_violation_time")
    private ZonedDateTime lastViolationTime;

    @Column(name = "total_flight_time_minutes", nullable = false)
    private Integer totalFlightTimeMinutes = 0;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
} 