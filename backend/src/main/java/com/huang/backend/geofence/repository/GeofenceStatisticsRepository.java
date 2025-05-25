package com.huang.backend.geofence.repository;

import com.huang.backend.geofence.entity.GeofenceStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for GeofenceStatistics entity
 */
@Repository
public interface GeofenceStatisticsRepository extends JpaRepository<GeofenceStatistics, UUID> {

    /**
     * Find statistics for active geofences
     */
    @Query("SELECT gs FROM GeofenceStatistics gs JOIN gs.geofence g WHERE g.active = true")
    List<GeofenceStatistics> findByActiveGeofences();

    /**
     * Get total statistics
     */
    @Query("SELECT " +
           "SUM(gs.droneCount) as totalDrones, " +
           "SUM(gs.violationCount) as totalViolations, " +
           "AVG(gs.totalFlightTimeMinutes) as avgFlightTime " +
           "FROM GeofenceStatistics gs " +
           "JOIN gs.geofence g WHERE g.active = true")
    Object[] getTotalStatistics();

    /**
     * Update drone count for a specific geofence
     */
    @Modifying
    @Query("UPDATE GeofenceStatistics gs SET gs.droneCount = :count WHERE gs.geofenceId = :geofenceId")
    void updateDroneCount(@Param("geofenceId") UUID geofenceId, @Param("count") Integer count);

    /**
     * Increment violation count
     */
    @Modifying
    @Query("UPDATE GeofenceStatistics gs SET gs.violationCount = gs.violationCount + 1 " +
           "WHERE gs.geofenceId = :geofenceId")
    void incrementViolationCount(@Param("geofenceId") UUID geofenceId);

    /**
     * Find geofences with most violations
     */
    @Query("SELECT gs FROM GeofenceStatistics gs ORDER BY gs.violationCount DESC")
    List<GeofenceStatistics> findTopViolationGeofences();
} 