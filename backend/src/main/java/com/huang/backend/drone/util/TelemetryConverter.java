package com.huang.backend.drone.util;

import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.huang.backend.drone.model.TelemetryData;
import com.huang.backend.mqtt.model.DroneTelemetryData;

/**
 * Utility class for converting between different telemetry data types
 */
public class TelemetryConverter {

    /**
     * Convert TelemetryData to DroneTelemetryDto
     */
    public static DroneTelemetryDto toDto(TelemetryData data) {
        return DroneTelemetryDto.builder()
                .droneId(data.getDroneId())
                .timestamp(data.getTimestamp())
                .batteryLevel(data.getBatteryLevel())
                .batteryVoltage(data.getBatteryVoltage())
                .latitude(data.getLatitude())
                .longitude(data.getLongitude())
                .altitude(data.getAltitude())
                .speed(data.getSpeed())
                .heading(data.getHeading())
                .satellites(data.getSatellites())
                .signalStrength(data.getSignalStrength())
                .flightMode(data.getFlightMode())
                .temperature(data.getTemperature())
                .build();
    }
    
    /**
     * Convert TelemetryData to DroneTelemetryData
     */
    public static DroneTelemetryData toMqttData(TelemetryData data, String sourceTopic) {
        return DroneTelemetryData.builder()
                .droneId(data.getDroneId())
                .timestamp(data.getTimestamp())
                .batteryLevel(data.getBatteryLevel())
                .batteryVoltage(data.getBatteryVoltage())
                .latitude(data.getLatitude())
                .longitude(data.getLongitude())
                .altitude(data.getAltitude())
                .speed(data.getSpeed())
                .heading(data.getHeading())
                .satellites(data.getSatellites())
                .signalStrength(data.getSignalStrength())
                .flightMode(data.getFlightMode())
                .temperature(data.getTemperature())
                .sourceTopic(sourceTopic)
                .build();
    }
    
    /**
     * Convert DroneTelemetryData to TelemetryData
     */
    public static TelemetryData fromMqttData(DroneTelemetryData data) {
        return TelemetryData.builder()
                .droneId(data.getDroneId())
                .timestamp(data.getTimestamp())
                .batteryLevel(data.getBatteryLevel())
                .batteryVoltage(data.getBatteryVoltage())
                .latitude(data.getLatitude())
                .longitude(data.getLongitude())
                .altitude(data.getAltitude())
                .speed(data.getSpeed())
                .heading(data.getHeading())
                .satellites(data.getSatellites())
                .signalStrength(data.getSignalStrength())
                .flightMode(data.getFlightMode())
                .temperature(data.getTemperature())
                .build();
    }
} 