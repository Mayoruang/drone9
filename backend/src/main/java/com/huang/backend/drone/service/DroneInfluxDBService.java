package com.huang.backend.drone.service;

import com.huang.backend.drone.dto.DroneTelemetryDto;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.DeleteApi;
import com.influxdb.client.domain.DeletePredicateRequest;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for querying drone telemetry data from InfluxDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneInfluxDBService {

    private final InfluxDBClient influxDBClient;
    
    @Value("${influxdb.bucket}")
    private String bucket;
    
    @Value("${influxdb.org}")
    private String organization;
    
    private static final String MEASUREMENT = "drone_telemetry";

    /**
     * Get the latest telemetry data for a specific drone
     * 
     * @param droneId the ID of the drone
     * @return the latest telemetry data, or null if not found
     */
    public DroneTelemetryDto getLatestTelemetry(String droneId) {
        String query = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -1h) " +
                "|> filter(fn: (r) => r._measurement == \"%s\") " +
                "|> filter(fn: (r) => r.drone_id == \"%s\") " +
                "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                "|> limit(n: 1)",
                bucket, MEASUREMENT, droneId);
        
        log.debug("Executing InfluxDB query: {}", query);
        
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(query, organization);
            
            if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
                log.warn("No telemetry data found for drone: {}", droneId);
                return null;
            }
            
            FluxRecord record = tables.get(0).getRecords().get(0);
            return mapRecordToDto(record, droneId);
            
        } catch (Exception e) {
            log.error("Error querying latest telemetry from InfluxDB: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get historical telemetry data for a specific drone within a time range
     * 
     * @param droneId the ID of the drone
     * @param start the start time
     * @param end the end time
     * @param limit the maximum number of records to return
     * @return a list of telemetry data points within the specified time range
     */
    public List<DroneTelemetryDto> getTelemetryHistory(String droneId, Instant start, Instant end, int limit) {
        String query = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r._measurement == \"%s\") " +
                "|> filter(fn: (r) => r.drone_id == \"%s\") " +
                "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> sort(columns: [\"_time\"]) " +
                "|> limit(n: %d)",
                bucket, 
                start.toString(), 
                end.toString(), 
                MEASUREMENT, 
                droneId,
                limit);
        
        log.debug("Executing InfluxDB query: {}", query);
        
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(query, organization);
            
            if (tables.isEmpty()) {
                log.warn("No telemetry history found for drone: {}", droneId);
                return new ArrayList<>();
            }
            
            List<DroneTelemetryDto> telemetryList = new ArrayList<>();
            
            for (FluxRecord record : tables.get(0).getRecords()) {
                DroneTelemetryDto dto = mapRecordToDto(record, droneId);
                if (dto != null) {
                    telemetryList.add(dto);
                }
            }
            
            return telemetryList;
            
        } catch (Exception e) {
            log.error("Error querying telemetry history from InfluxDB: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Map a flux record to a telemetry DTO
     * 
     * @param record the flux record
     * @param droneId the drone ID
     * @return a telemetry DTO
     */
    private DroneTelemetryDto mapRecordToDto(FluxRecord record, String droneId) {
        try {
            DroneTelemetryDto.DroneTelemetryDtoBuilder builder = DroneTelemetryDto.builder()
                    .droneId(droneId)
                    .timestamp(record.getTime());
            
            // Safely get each field from the record
            if (record.getValueByKey("battery_level") != null) {
                builder.batteryLevel(toDouble(record.getValueByKey("battery_level")));
            }
            
            if (record.getValueByKey("battery_voltage") != null) {
                builder.batteryVoltage(toDouble(record.getValueByKey("battery_voltage")));
            }
            
            if (record.getValueByKey("latitude") != null) {
                builder.latitude(toDouble(record.getValueByKey("latitude")));
            }
            
            if (record.getValueByKey("longitude") != null) {
                builder.longitude(toDouble(record.getValueByKey("longitude")));
            }
            
            if (record.getValueByKey("altitude") != null) {
                builder.altitude(toDouble(record.getValueByKey("altitude")));
            }
            
            if (record.getValueByKey("speed") != null) {
                builder.speed(toDouble(record.getValueByKey("speed")));
            }
            
            if (record.getValueByKey("heading") != null) {
                builder.heading(toDouble(record.getValueByKey("heading")));
            }
            
            if (record.getValueByKey("satellites") != null) {
                Object value = record.getValueByKey("satellites");
                if (value instanceof Number) {
                    builder.satellites(((Number) value).intValue());
                }
            }
            
            if (record.getValueByKey("signal_strength") != null) {
                builder.signalStrength(toDouble(record.getValueByKey("signal_strength")));
            }
            
            if (record.getValueByKey("flight_mode") != null) {
                builder.flightMode(String.valueOf(record.getValueByKey("flight_mode")));
            }
            
            if (record.getValueByKey("temperature") != null) {
                builder.temperature(toDouble(record.getValueByKey("temperature")));
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error mapping flux record to DTO: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Safely convert an object to a Double
     * 
     * @param value the value to convert
     * @return the Double value, or null if conversion fails
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Delete all telemetry for given drone serial from InfluxDB bucket.
     *
     * @param serialNumber drone serial (same tag value as written)
     */
    public void deleteAllTelemetry(String serialNumber) {
        try {
            DeleteApi deleteApi = influxDBClient.getDeleteApi();
            String predicate = String.format("_measurement=\"%s\" AND drone_id=\"%s\"", MEASUREMENT, serialNumber);
            OffsetDateTime start = OffsetDateTime.parse("1970-01-01T00:00:00Z");
            OffsetDateTime stop = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
            deleteApi.delete(start, stop, predicate, bucket, organization);
            log.info("Deleted all telemetry for drone {} from InfluxDB", serialNumber);
        } catch (Exception e) {
            log.error("Failed to delete telemetry for drone {}: {}", serialNumber, e.getMessage(), e);
        }
    }
} 