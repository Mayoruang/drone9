package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneStatusDto;
import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.drone.repository.DroneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the DroneStatusService interface
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneStatusServiceImpl implements DroneStatusService {

    private final DroneRepository droneRepository;
    private final DroneInfluxDBService influxDBService;
    
    @Override
    public List<DroneStatusDto> getAllDronesStatus() {
        log.debug("Getting status for all drones");
        List<Drone> drones = droneRepository.findAll();
        return drones.stream()
                .map(this::enrichDroneStatusWithTelemetry)
                .collect(Collectors.toList());
    }

    @Override
    public DroneStatusDto getDroneStatus(UUID droneId) {
        log.debug("Getting status for drone: {}", droneId);
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        return enrichDroneStatusWithTelemetry(drone);
    }

    @Override
    public List<DroneStatusDto> getDronesByStatus(Drone.DroneStatus status) {
        log.debug("Getting drones with status: {}", status);
        List<Drone> drones;
        
        if (status == null) {
            drones = droneRepository.findAll();
        } else {
            drones = droneRepository.findByCurrentStatus(status);
        }
        
        return drones.stream()
                .map(this::enrichDroneStatusWithTelemetry)
                .collect(Collectors.toList());
    }

    @Override
    public DroneTelemetryDto getLatestTelemetry(UUID droneId) {
        log.debug("Getting latest telemetry for drone: {}", droneId);
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        return influxDBService.getLatestTelemetry(drone.getSerialNumber());
    }

    @Override
    public List<DroneTelemetryDto> getTelemetryHistory(UUID droneId, Instant start, Instant end, int limit) {
        log.debug("Getting telemetry history for drone: {}, from {} to {}, limit: {}", 
                droneId, start, end, limit);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new NoSuchElementException("Drone not found with ID: " + droneId));
        
        return influxDBService.getTelemetryHistory(drone.getSerialNumber(), start, end, limit);
    }
    
    /**
     * Enrich a drone status DTO with telemetry data
     * 
     * @param drone the drone entity
     * @return the enriched drone status DTO
     */
    private DroneStatusDto enrichDroneStatusWithTelemetry(Drone drone) {
        DroneStatusDto dto = DroneStatusDto.fromEntity(drone);
        
        try {
            // Get latest telemetry to enhance the status
            DroneTelemetryDto telemetry = influxDBService.getLatestTelemetry(drone.getSerialNumber());
            
            if (telemetry != null) {
                dto.setBatteryLevel(telemetry.getBatteryLevel());
                dto.setLatitude(telemetry.getLatitude());
                dto.setLongitude(telemetry.getLongitude());
                dto.setAltitude(telemetry.getAltitude());
            }
        } catch (Exception e) {
            log.warn("Error enriching drone status with telemetry for drone {}: {}", 
                    drone.getSerialNumber(), e.getMessage());
            // Continue without telemetry data
        }
        
        return dto;
    }
} 