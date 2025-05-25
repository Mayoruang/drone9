package com.huang.backend.geofence.service;

import com.huang.backend.geofence.dto.*;
import com.huang.backend.geofence.entity.Geofence;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing geofences
 */
public interface GeofenceService {
    
    /**
     * Create a new geofence
     * 
     * @param createDto the geofence data
     * @param username the creating user
     * @return response with created geofence ID
     */
    GeofenceResponseDto createGeofence(GeofenceCreateDto createDto, String username);
    
    /**
     * Get a paginated list of geofences
     * 
     * @param pageable pagination information
     * @return page of geofence items
     */
    Page<GeofenceListItemDto> getGeofences(Pageable pageable);
    
    /**
     * Search geofences with filters
     * 
     * @param type geofence type filter
     * @param active active status filter
     * @param search search term
     * @param pageable pagination information
     * @return page of geofence items
     */
    Page<GeofenceListItemDto> searchGeofences(Geofence.GeofenceType type, Boolean active, String search, Pageable pageable);
    
    /**
     * Get detailed information about a specific geofence
     * 
     * @param geofenceId the geofence ID
     * @return detailed geofence information
     */
    GeofenceDetailDto getGeofenceDetail(UUID geofenceId);
    
    /**
     * Update an existing geofence
     * 
     * @param geofenceId the geofence ID
     * @param updateDto the updated geofence data
     * @param username the updating user
     * @return response with updated status
     */
    GeofenceResponseDto updateGeofence(UUID geofenceId, GeofenceCreateDto updateDto, String username);
    
    /**
     * Delete a geofence
     * 
     * @param geofenceId the geofence ID
     * @return response with deletion status
     */
    GeofenceResponseDto deleteGeofence(UUID geofenceId);
    
    /**
     * Bind drones to a geofence
     * 
     * @param geofenceId the geofence ID
     * @param bindDto the drone IDs to bind
     * @return response with binding status
     */
    GeofenceResponseDto bindDrones(UUID geofenceId, GeofenceDroneBindDto bindDto);
    
    /**
     * Unbind a drone from a geofence
     * 
     * @param geofenceId the geofence ID
     * @param droneId the drone ID to unbind
     * @return response with unbinding status
     */
    GeofenceResponseDto unbindDrone(UUID geofenceId, UUID droneId);
    
    /**
     * Find all geofences that contain a specific point
     * 
     * @param point the geographic point
     * @return list of geofences containing the point
     */
    List<GeofenceListItemDto> findGeofencesContainingPoint(Point point);
    
    /**
     * Find all geofences that contain a specific point (by coordinates)
     * 
     * @param longitude longitude
     * @param latitude latitude
     * @return list of geofences containing the point
     */
    List<GeofenceListItemDto> findGeofencesContainingPoint(Double longitude, Double latitude);
    
    /**
     * Get violations for a specific geofence
     * 
     * @param geofenceId the geofence ID
     * @param pageable pagination information
     * @return page of violation records
     */
    Page<ViolationRecordDto> getGeofenceViolations(UUID geofenceId, Pageable pageable);
    
    /**
     * Resolve a violation
     * 
     * @param violationId the violation ID
     * @param notes resolution notes
     * @param resolvedBy who resolved it
     */
    void resolveViolation(UUID violationId, String notes, String resolvedBy);
    
    /**
     * Generate a thumbnail for a geofence
     * This is typically called asynchronously after creation
     * 
     * @param geofenceId the geofence ID
     */
    void generateThumbnail(UUID geofenceId);
} 