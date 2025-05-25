package com.huang.backend.geofence.controller;

import com.huang.backend.geofence.dto.*;
import com.huang.backend.geofence.entity.Geofence;
import com.huang.backend.geofence.service.GeofenceAnalyticsService;
import com.huang.backend.geofence.service.GeofenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for geofence operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/geofences")
@RequiredArgsConstructor
public class GeofenceController {

    private final GeofenceService geofenceService;
    private final GeofenceAnalyticsService analyticsService;
    
    /**
     * Create a new geofence
     *
     * @param createDto the geofence data
     * @param authentication the authenticated user (optional for testing)
     * @return 201 CREATED with the result of the operation
     */
    @PostMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<GeofenceResponseDto> createGeofence(
            @Valid @RequestBody GeofenceCreateDto createDto,
            Authentication authentication) {
        
        log.info("Received request to create geofence: {}", createDto.getName());
        
        // 处理认证为null的情况（用于测试）
        String createdBy = (authentication != null && authentication.getName() != null) 
                ? authentication.getName() 
                : "system";
        
        GeofenceResponseDto response = geofenceService.createGeofence(
                createDto, 
                createdBy
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get a paginated list of geofences with filters
     *
     * @param page page number (0-based)
     * @param size page size
     * @param type geofence type filter
     * @param active active status filter
     * @param search search term
     * @return 200 OK with the page of geofences
     */
    @GetMapping
    public ResponseEntity<Page<GeofenceListItemDto>> getGeofences(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        
        log.info("Getting geofences with filters - page: {}, size: {}, type: {}, active: {}, search: {}", 
                page, size, type, active, search);
        
        try {
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by("priority").descending()
                    .and(Sort.by("createdAt").descending()));
            
            Page<GeofenceListItemDto> geofences;
            
            // If no filters are provided, use simple findAll
            if ((type == null || type.trim().isEmpty()) && 
                active == null && 
                (search == null || search.trim().isEmpty())) {
                
                log.debug("No filters provided, using simple findAll");
                geofences = geofenceService.getGeofences(pageRequest);
            } else {
                // Use search method with filters
                Geofence.GeofenceType geofenceType = null;
                if (type != null && !type.trim().isEmpty()) {
                    try {
                        geofenceType = Geofence.GeofenceType.valueOf(type.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid geofence type: {}", type);
                    }
                }
                
                geofences = geofenceService.searchGeofences(
                        geofenceType, active, search, pageRequest);
            }
            
            log.info("Successfully retrieved {} geofences", geofences.getTotalElements());
            return ResponseEntity.ok(geofences);
        } catch (Exception e) {
            log.error("Error retrieving geofences: ", e);
            throw e;
        }
    }
    
    /**
     * Get detailed information about a specific geofence
     *
     * @param geofenceId the geofence ID
     * @return 200 OK with the geofence details
     */
    @GetMapping("/{geofenceId}")
    public ResponseEntity<GeofenceDetailDto> getGeofenceDetail(
            @PathVariable UUID geofenceId) {
        
        log.debug("Fetching details for geofence: {}", geofenceId);
        GeofenceDetailDto geofence = geofenceService.getGeofenceDetail(geofenceId);
        
        return ResponseEntity.ok(geofence);
    }
    
    /**
     * Update an existing geofence
     *
     * @param geofenceId the geofence ID
     * @param updateDto the updated geofence data
     * @param authentication the authenticated user
     * @return 200 OK with the result of the operation
     */
    @PutMapping("/{geofenceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GeofenceResponseDto> updateGeofence(
            @PathVariable UUID geofenceId,
            @Valid @RequestBody GeofenceCreateDto updateDto,
            Authentication authentication) {
        
        log.info("Received request to update geofence: {}", geofenceId);
        GeofenceResponseDto response = geofenceService.updateGeofence(
                geofenceId,
                updateDto,
                authentication.getName()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete a geofence
     *
     * @param geofenceId the geofence ID
     * @return 200 OK with the result of the operation
     */
    @DeleteMapping("/{geofenceId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GeofenceResponseDto> deleteGeofence(
            @PathVariable UUID geofenceId) {
        
        log.info("Received request to delete geofence: {}", geofenceId);
        GeofenceResponseDto response = geofenceService.deleteGeofence(geofenceId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Bind drones to a geofence
     *
     * @param geofenceId the geofence ID
     * @param bindDto the drone IDs to bind
     * @return 200 OK with the result of the operation
     */
    @PostMapping("/{geofenceId}/drones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GeofenceResponseDto> bindDrones(
            @PathVariable UUID geofenceId,
            @Valid @RequestBody GeofenceDroneBindDto bindDto) {
        
        log.info("Binding drones to geofence: {}", geofenceId);
        GeofenceResponseDto response = geofenceService.bindDrones(geofenceId, bindDto);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Unbind a drone from a geofence
     *
     * @param geofenceId the geofence ID
     * @param droneId the drone ID to unbind
     * @return 200 OK with the result of the operation
     */
    @DeleteMapping("/{geofenceId}/drones/{droneId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GeofenceResponseDto> unbindDrone(
            @PathVariable UUID geofenceId,
            @PathVariable UUID droneId) {
        
        log.info("Unbinding drone {} from geofence: {}", droneId, geofenceId);
        GeofenceResponseDto response = geofenceService.unbindDrone(geofenceId, droneId);
        
        return ResponseEntity.ok(response);
    }

    // ============================================================================
    // 统计分析接口
    // ============================================================================

    /**
     * Get comprehensive geofence statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GeofenceStatisticsDto> getGeofenceStatistics() {
        log.debug("Fetching geofence statistics");
        GeofenceStatisticsDto statistics = analyticsService.getGeofenceStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get geofence overview data for dashboard
     */
    @GetMapping("/statistics/overview")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GeofenceOverviewDto> getGeofenceOverview() {
        log.debug("Fetching geofence overview");
        GeofenceOverviewDto overview = analyticsService.getGeofenceOverview();
        return ResponseEntity.ok(overview);
    }

    /**
     * Get statistics for a specific geofence
     */
    @GetMapping("/{geofenceId}/statistics")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Object> getGeofenceDetailStatistics(@PathVariable UUID geofenceId) {
        log.debug("Fetching statistics for geofence: {}", geofenceId);
        var statistics = analyticsService.getGeofenceDetailStatistics(geofenceId);
        return ResponseEntity.ok(statistics);
    }

    // ============================================================================
    // 违规管理接口
    // ============================================================================

    /**
     * Get violations for a specific geofence
     */
    @GetMapping("/{geofenceId}/violations")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Page<ViolationRecordDto>> getGeofenceViolations(
            @PathVariable UUID geofenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.debug("Fetching violations for geofence: {}", geofenceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("violationTime").descending());
        Page<ViolationRecordDto> violations = geofenceService.getGeofenceViolations(geofenceId, pageRequest);
        return ResponseEntity.ok(violations);
    }

    /**
     * Resolve a violation
     */
    @PostMapping("/{geofenceId}/violations/{violationId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resolveViolation(
            @PathVariable UUID geofenceId,
            @PathVariable UUID violationId,
            @RequestBody(required = false) String notes,
            Authentication authentication) {
        
        log.info("Resolving violation {} for geofence {}", violationId, geofenceId);
        geofenceService.resolveViolation(violationId, notes, authentication.getName());
        return ResponseEntity.ok().build();
    }

    // ============================================================================
    // 地理查询接口
    // ============================================================================

    /**
     * Query geofences containing a point
     */
    @PostMapping("/query/point-in-polygon")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<GeofenceListItemDto>> queryGeofencesByPoint(
            @RequestBody PointQueryDto queryDto) {
        
        log.debug("Querying geofences containing point: {}, {}", queryDto.getLongitude(), queryDto.getLatitude());
        List<GeofenceListItemDto> geofences = geofenceService.findGeofencesContainingPoint(
                queryDto.getLongitude(), queryDto.getLatitude());
        return ResponseEntity.ok(geofences);
    }

    // ============================================================================
    // 测试接口
    // ============================================================================

    /**
     * Simple test endpoint to check if the controller is accessible
     */
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Geofence controller is working!");
    }

    /**
     * Simple test endpoint without query parameters
     */
    @GetMapping("/list")
    public ResponseEntity<String> testListEndpoint() {
        log.info("=== GEOFENCE CONTROLLER: testListEndpoint called ===");
        return ResponseEntity.ok("Geofence list endpoint is working!");
    }
}