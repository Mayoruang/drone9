package com.huang.backend.geofence.repository;

import com.huang.backend.geofence.entity.GeofenceViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for GeofenceViolation entity
 */
@Repository
public interface GeofenceViolationRepository extends JpaRepository<GeofenceViolation, UUID> {

    /**
     * Find violations by geofence ID
     */
    Page<GeofenceViolation> findByGeofence_GeofenceIdOrderByViolationTimeDesc(UUID geofenceId, Pageable pageable);

    /**
     * Find violations by drone ID
     */
    Page<GeofenceViolation> findByDrone_DroneIdOrderByViolationTimeDesc(UUID droneId, Pageable pageable);

    /**
     * Find unresolved violations
     */
    @Query("SELECT v FROM GeofenceViolation v WHERE v.resolved = false ORDER BY v.violationTime DESC")
    List<GeofenceViolation> findUnresolvedViolations();

    /**
     * Find recent violations within hours
     */
    @Query("SELECT v FROM GeofenceViolation v WHERE v.violationTime >= :since ORDER BY v.violationTime DESC")
    List<GeofenceViolation> findRecentViolations(@Param("since") ZonedDateTime since);

    /**
     * Count violations by geofence
     */
    @Query("SELECT v.geofence.geofenceId, COUNT(v) FROM GeofenceViolation v GROUP BY v.geofence.geofenceId")
    List<Object[]> countViolationsByGeofence();

    /**
     * Count violations by date
     */
    @Query("SELECT DATE(v.violationTime), COUNT(v) FROM GeofenceViolation v " +
           "WHERE v.violationTime >= :since GROUP BY DATE(v.violationTime) ORDER BY DATE(v.violationTime)")
    List<Object[]> countViolationsByDate(@Param("since") ZonedDateTime since);

    /**
     * Count violations by severity
     */
    @Query("SELECT v.severity, COUNT(v) FROM GeofenceViolation v GROUP BY v.severity")
    List<Object[]> countViolationsBySeverity();

    /**
     * Find violations in time range for specific geofence
     */
    @Query("SELECT v FROM GeofenceViolation v WHERE v.geofence.geofenceId = :geofenceId " +
           "AND v.violationTime BETWEEN :startTime AND :endTime ORDER BY v.violationTime DESC")
    List<GeofenceViolation> findViolationsInTimeRange(
            @Param("geofenceId") UUID geofenceId,
            @Param("startTime") ZonedDateTime startTime,
            @Param("endTime") ZonedDateTime endTime);

    /**
     * Get violation statistics for dashboard
     */
    @Query("SELECT " +
           "COUNT(v) as total, " +
           "COUNT(CASE WHEN v.resolved = false THEN 1 END) as unresolved, " +
           "COUNT(CASE WHEN v.severity = 'CRITICAL' THEN 1 END) as critical, " +
           "COUNT(CASE WHEN v.violationTime >= :since THEN 1 END) as recent " +
           "FROM GeofenceViolation v")
    Object[] getViolationStatistics(@Param("since") ZonedDateTime since);
} 