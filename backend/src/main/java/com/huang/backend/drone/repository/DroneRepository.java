package com.huang.backend.drone.repository;

import com.huang.backend.drone.entity.Drone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for drones
 */
@Repository
public interface DroneRepository extends JpaRepository<Drone, UUID> {

    /**
     * Find a drone by serial number
     */
    Optional<Drone> findBySerialNumber(String serialNumber);
    
    /**
     * Find a drone by registration request ID
     */
    Optional<Drone> findByRegistrationRequestId(UUID registrationRequestId);
    
    /**
     * Find drones by current status
     */
    List<Drone> findByCurrentStatus(Drone.DroneStatus status);
    
    /**
     * Find drones with no heartbeat after a certain time
     */
    List<Drone> findByLastHeartbeatAtBeforeOrLastHeartbeatAtIsNull(ZonedDateTime time);
    
    /**
     * Check if a drone with given serial number exists
     */
    boolean existsBySerialNumber(String serialNumber);
    
    /**
     * Check if a drone with given MQTT username exists
     */
    boolean existsByMqttUsername(String mqttUsername);
    
    /**
     * Find a drone with eagerly loaded geofences
     * 
     * @param id the drone ID
     * @return the drone with all geofence associations
     */
    @Query("SELECT d FROM Drone d LEFT JOIN FETCH d.geofences WHERE d.droneId = :id")
    Optional<Drone> findByIdWithGeofences(UUID id);
} 