package com.huang.backend.geofence.repository;

import com.huang.backend.geofence.entity.Geofence;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Geofence entity
 */
@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, UUID>, 
                                           JpaSpecificationExecutor<Geofence> {
    
    /**
     * Find a geofence with eagerly loaded drones
     * 
     * @param id the geofence ID
     * @return the geofence with all drone associations
     */
    @Query("SELECT g FROM Geofence g LEFT JOIN FETCH g.drones WHERE g.geofenceId = :id")
    Optional<Geofence> findByIdWithDrones(UUID id);
    
    /**
     * Find all geofences that contain the given point
     * 
     * @param point the geographic point to check
     * @return list of geofences containing the point
     */
    @Query("SELECT g FROM Geofence g WHERE ST_Contains(g.geom, :point) = true AND g.active = true")
    List<Geofence> findContainingPoint(Point point);
    
    /**
     * Find all geofences that are active and associated with a given drone
     * 
     * @param droneId the drone ID
     * @return list of active geofences for the drone
     */
    @Query("SELECT g FROM Geofence g JOIN g.drones d WHERE d.droneId = :droneId AND g.active = true")
    List<Geofence> findActiveGeofencesForDrone(UUID droneId);
    
    /**
     * Find all geofences with pageable support
     * 
     * @param pageable pagination information
     * @return page of geofences
     */
    @Override
    Page<Geofence> findAll(Pageable pageable);
    
    /**
     * Check if any existing geofence intersects with a given geometry
     * 
     * @param geofenceId ID to exclude from check (for updates)
     * @param geometry the geometry to check
     * @return true if there's an intersection
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Geofence g " +
           "WHERE g.geofenceId != :geofenceId AND ST_Intersects(g.geom, :geometry) = true")
    boolean existsIntersectingGeofence(UUID geofenceId, org.locationtech.jts.geom.Geometry geometry);

    /**
     * Find geofences by type
     */
    List<Geofence> findByGeofenceTypeAndActiveTrue(Geofence.GeofenceType type);

    /**
     * Find active geofences
     */
    List<Geofence> findByActiveTrue();

    /**
     * Find geofences containing a point
     */
    @Query(value = "SELECT g.* FROM geofences g WHERE ST_Contains(g.geom, :point) AND g.active = true", 
           nativeQuery = true)
    List<Geofence> findGeofencesContainingPoint(@Param("point") Point point);

    /**
     * Find geofences intersecting with another geometry
     */
    @Query(value = "SELECT g.* FROM geofences g WHERE ST_Intersects(g.geom, ST_GeomFromText(:wkt, 4326)) AND g.active = true", 
           nativeQuery = true)
    List<Geofence> findGeofencesIntersecting(@Param("wkt") String wkt);

    /**
     * Count geofences by type
     */
    @Query("SELECT g.geofenceType, COUNT(g) FROM Geofence g WHERE g.active = true GROUP BY g.geofenceType")
    List<Object[]> countByType();

    /**
     * Get total area by type
     */
    @Query("SELECT g.geofenceType, SUM(g.areaSquareMeters) FROM Geofence g WHERE g.active = true GROUP BY g.geofenceType")
    List<Object[]> sumAreaByType();

    /**
     * Find geofences by name containing
     */
    Page<Geofence> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    /**
     * Find geofences by priority range
     */
    List<Geofence> findByPriorityBetweenAndActiveTrue(Integer minPriority, Integer maxPriority);

    /**
     * Find geofences ordered by priority
     */
    @Query("SELECT g FROM Geofence g WHERE g.active = true ORDER BY g.priority DESC")
    List<Geofence> findAllOrderByPriorityDesc();

    /**
     * Find geofences with area greater than threshold
     */
    @Query("SELECT g FROM Geofence g WHERE g.areaSquareMeters > :minArea AND g.active = true")
    List<Geofence> findGeofencesWithAreaGreaterThan(@Param("minArea") Double minArea);

    /**
     * Get geofence statistics summary
     */
    @Query("SELECT " +
           "COUNT(g) as total, " +
           "COUNT(CASE WHEN g.active = true THEN 1 END) as active, " +
           "SUM(g.areaSquareMeters) as totalArea, " +
           "AVG(g.areaSquareMeters) as avgArea " +
           "FROM Geofence g")
    Object[] getStatisticsSummary();

    /**
     * Find geofences by created by user
     */
    Page<Geofence> findByCreatedByAndActiveTrue(String createdBy, Pageable pageable);

    /**
     * Search geofences with filters
     */
    @Query("SELECT g FROM Geofence g WHERE " +
           "(:type IS NULL OR g.geofenceType = :type) AND " +
           "(:active IS NULL OR g.active = :active) AND " +
           "(:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Geofence> searchGeofences(
            @Param("type") Geofence.GeofenceType type,
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable);
} 