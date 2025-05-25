package com.huang.backend.geofence.entity;

import com.huang.backend.drone.entity.Drone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entity representing a geofence violation
 */
@Entity
@Table(name = "geofence_violations")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GeofenceViolation {

    @Id
    @Column(name = "violation_id", updatable = false, nullable = false)
    private UUID violationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id", nullable = false)
    private Geofence geofence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drone_id", nullable = false)
    private Drone drone;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false)
    private ViolationType violationType;

    @Column(name = "violation_point", columnDefinition = "geometry(POINT, 4326)")
    private Point violationPoint;

    @Column(name = "altitude")
    private Double altitude;

    @Column(name = "violation_time", nullable = false)
    private ZonedDateTime violationTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity = Severity.MEDIUM;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    public void prePersist() {
        if (this.violationId == null) {
            this.violationId = UUID.randomUUID();
        }
        if (this.violationTime == null) {
            this.violationTime = ZonedDateTime.now();
        }
    }

    /**
     * Enum for violation types
     */
    public enum ViolationType {
        ENTRY,           // 进入违规
        EXIT,            // 离开违规
        ALTITUDE_BREACH, // 高度违规
        TIME_VIOLATION   // 时间违规
    }

    /**
     * Enum for violation severity
     */
    public enum Severity {
        LOW,      // 低
        MEDIUM,   // 中
        HIGH,     // 高
        CRITICAL  // 严重
    }
} 