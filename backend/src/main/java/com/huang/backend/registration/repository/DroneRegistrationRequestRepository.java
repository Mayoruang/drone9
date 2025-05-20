package com.huang.backend.registration.repository;

import com.huang.backend.registration.entity.DroneRegistrationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for drone registration requests
 */
@Repository
public interface DroneRegistrationRequestRepository extends JpaRepository<DroneRegistrationRequest, UUID> {

    /**
     * Find a request by serial number
     */
    Optional<DroneRegistrationRequest> findBySerialNumber(String serialNumber);
    
    /**
     * Find requests by status
     */
    List<DroneRegistrationRequest> findByStatus(DroneRegistrationRequest.RegistrationStatus status);
    
    /**
     * Find requests by status with pagination
     */
    Page<DroneRegistrationRequest> findByStatus(DroneRegistrationRequest.RegistrationStatus status, Pageable pageable);
    
    /**
     * Find requests whose status is not the given status (used to exclude DELETED by default).
     */
    Page<DroneRegistrationRequest> findByStatusNot(DroneRegistrationRequest.RegistrationStatus status, Pageable pageable);
    
    /**
     * Check if a request with the given serial number exists
     */
    boolean existsBySerialNumber(String serialNumber);
} 