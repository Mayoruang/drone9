package com.huang.backend.geofence.entity;

import com.huang.backend.drone.entity.Drone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a geofence in the system.
 */
@Entity
@Table(name = "geofences")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Geofence {

    @Id
    @Column(name = "geofence_id", updatable = false, nullable = false)
    private UUID geofenceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /**
     * Polygon geometry in WGS-84 (SRID 4326) coordinate system
     */
    @Column(name = "geom", columnDefinition = "geometry(POLYGON, 4326)")
    private Polygon geom;

    /**
     * URL to a static map thumbnail of the geofence
     */
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    /**
     * Whether this geofence is currently active and enforced
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Type of the geofence
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_type", nullable = false)
    private GeofenceType geofenceType = GeofenceType.NO_FLY_ZONE;

    /**
     * Priority of the geofence (higher number = higher priority)
     */
    @Column(name = "priority", nullable = false)
    private Integer priority = 1;

    /**
     * Area of the geofence in square meters
     */
    @Column(name = "area_square_meters")
    private Double areaSquareMeters;

    /**
     * Perimeter of the geofence in meters
     */
    @Column(name = "perimeter_meters")
    private Double perimeterMeters;

    /**
     * Center point of the geofence
     */
    @Column(name = "center_point", columnDefinition = "geometry(POINT, 4326)")
    private Point centerPoint;

    /**
     * Minimum altitude boundary in meters (optional)
     */
    @Column(name = "altitude_min")
    private Double altitudeMin;

    /**
     * Maximum altitude boundary in meters (optional)
     */
    @Column(name = "altitude_max")
    private Double altitudeMax;

    /**
     * When this geofence becomes active (optional)
     */
    @Column(name = "start_time")
    private ZonedDateTime startTime;

    /**
     * When this geofence expires (optional)
     */
    @Column(name = "end_time")
    private ZonedDateTime endTime;

    /**
     * Username who created this geofence
     */
    @Column(name = "created_by")
    private String createdBy;

    /**
     * Username who last updated this geofence
     */
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Set of drones assigned to this geofence
     */
    @ManyToMany
    @JoinTable(
            name = "drone_geofence",
            joinColumns = @JoinColumn(name = "geofence_id"),
            inverseJoinColumns = @JoinColumn(name = "drone_id")
    )
    private Set<Drone> drones = new HashSet<>();

    /**
     * Pre-persist hook to set default values before initial save
     */
    @PrePersist
    public void prePersist() {
        if (this.geofenceId == null) {
            this.geofenceId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = ZonedDateTime.now();
        }
    }

    /**
     * Pre-update hook to update the updatedAt timestamp
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * Enum for geofence types
     */
    public enum GeofenceType {
        NO_FLY_ZONE,     // 禁飞区
        FLY_ZONE,        // 允飞区
        RESTRICTED_ZONE  // 限飞区
    }
} 