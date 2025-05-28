package com.huang.backend.geofence.controller;

import com.huang.backend.geofence.dto.ViolationRecordDto;
import com.huang.backend.geofence.repository.GeofenceViolationRepository;
import com.huang.backend.geofence.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for violation operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final GeofenceViolationRepository violationRepository;
    private final ViolationMapper violationMapper;

    /**
     * Get all violations with pagination
     *
     * @param page page number (0-based)
     * @param size page size
     * @return 200 OK with the page of violations
     */
    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<Page<ViolationRecordDto>> getAllViolations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.debug("Getting all violations - page: {}, size: {}", page, size);
        
        PageRequest pageRequest = PageRequest.of(page, size, 
            Sort.by("violationTime").descending());
        
        Page<ViolationRecordDto> violations = violationRepository.findAll(pageRequest)
                .map(violationMapper::toDto);
        
        return ResponseEntity.ok(violations);
    }

    /**
     * Get violations by drone ID
     *
     * @param droneId the drone ID
     * @param page page number (0-based)
     * @param size page size
     * @return 200 OK with the page of violations
     */
    @GetMapping("/drone/{droneId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Page<ViolationRecordDto>> getViolationsByDrone(
            @PathVariable UUID droneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.debug("Getting violations for drone: {}", droneId);
        
        PageRequest pageRequest = PageRequest.of(page, size, 
            Sort.by("violationTime").descending());
        
        Page<ViolationRecordDto> violations = violationRepository
                .findByDrone_DroneIdOrderByViolationTimeDesc(droneId, pageRequest)
                .map(violationMapper::toDto);
        
        return ResponseEntity.ok(violations);
    }

    /**
     * Get unresolved violations
     *
     * @return 200 OK with the list of unresolved violations
     */
    @GetMapping("/unresolved")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getUnresolvedViolations() {
        
        log.debug("Getting unresolved violations");
        
        var violations = violationRepository.findUnresolvedViolations()
                .stream()
                .map(violationMapper::toDto)
                .toList();
        
        return ResponseEntity.ok(violations);
    }
} 