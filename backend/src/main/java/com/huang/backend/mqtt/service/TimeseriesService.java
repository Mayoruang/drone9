package com.huang.backend.mqtt.service;

import com.huang.backend.drone.model.TelemetryData;
import com.huang.backend.mqtt.model.DroneTelemetryData;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Service for writing telemetry data to InfluxDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeseriesService {

    private final InfluxDBClient influxDBClient;
    
    @Value("${influxdb.bucket}")
    private String bucket;
    
    @Value("${influxdb.org}")
    private String organization;
    
    private static final String MEASUREMENT = "drone_telemetry";

    /**
     * Writes drone telemetry data to InfluxDB using the Point class
     * 
     * NOTE: This method may not work correctly based on testing. Use writeTelemetryData instead.
     *
     * @param data the telemetry data to write
     */
    public void writeTelemetryDataUsingPoint(TelemetryData data) {
        try {
            Point point = Point.measurement(MEASUREMENT)
                    .time(data.getTimestamp(), WritePrecision.NS)
                    .addTag("drone_id", data.getDroneId());
            
            // Add fields (only if they are not null)
            if (data.getBatteryLevel() != null) point.addField("battery_level", data.getBatteryLevel());
            if (data.getBatteryVoltage() != null) point.addField("battery_voltage", data.getBatteryVoltage());
            if (data.getLatitude() != null) point.addField("latitude", data.getLatitude());
            if (data.getLongitude() != null) point.addField("longitude", data.getLongitude());
            if (data.getAltitude() != null) point.addField("altitude", data.getAltitude());
            if (data.getSpeed() != null) point.addField("speed", data.getSpeed());
            if (data.getHeading() != null) point.addField("heading", data.getHeading());
            if (data.getSatellites() != null) point.addField("satellites", data.getSatellites());
            if (data.getSignalStrength() != null) point.addField("signal_strength", data.getSignalStrength());
            if (data.getFlightMode() != null) point.addField("flight_mode", data.getFlightMode());
            if (data.getTemperature() != null) point.addField("temperature", data.getTemperature());
            
            try (WriteApi writeApi = influxDBClient.getWriteApi()) {
                writeApi.writePoint(point);
                log.debug("Telemetry data for drone {} written to InfluxDB (Point API)", data.getDroneId());
            }
        } catch (Exception e) {
            log.error("Failed to write telemetry data to InfluxDB using Point API: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Writes drone telemetry data to InfluxDB using the Line Protocol format, which has been
     * proven to work better in our environment.
     *
     * @param data the telemetry data to write
     */
    public void writeTelemetryData(TelemetryData data) {
        try {
            // Start building the line protocol string
            StringBuilder sb = new StringBuilder();
            sb.append(MEASUREMENT).append(","); // Measurement name
            
            // Add tags
            sb.append("drone_id=").append(data.getDroneId());
            
            // Add fields (only if they are not null)
            sb.append(" ");
            boolean hasFields = false;
            
            if (data.getBatteryLevel() != null) {
                sb.append("battery_level=").append(data.getBatteryLevel());
                hasFields = true;
            }
            
            if (data.getBatteryVoltage() != null) {
                if (hasFields) sb.append(",");
                sb.append("battery_voltage=").append(data.getBatteryVoltage());
                hasFields = true;
            }
            
            if (data.getLatitude() != null) {
                if (hasFields) sb.append(",");
                sb.append("latitude=").append(data.getLatitude());
                hasFields = true;
            }
            
            if (data.getLongitude() != null) {
                if (hasFields) sb.append(",");
                sb.append("longitude=").append(data.getLongitude());
                hasFields = true;
            }
            
            if (data.getAltitude() != null) {
                if (hasFields) sb.append(",");
                sb.append("altitude=").append(data.getAltitude());
                hasFields = true;
            }
            
            if (data.getSpeed() != null) {
                if (hasFields) sb.append(",");
                sb.append("speed=").append(data.getSpeed());
                hasFields = true;
            }
            
            if (data.getHeading() != null) {
                if (hasFields) sb.append(",");
                sb.append("heading=").append(data.getHeading());
                hasFields = true;
            }
            
            if (data.getSatellites() != null) {
                if (hasFields) sb.append(",");
                sb.append("satellites=").append(data.getSatellites());
                hasFields = true;
            }
            
            if (data.getSignalStrength() != null) {
                if (hasFields) sb.append(",");
                sb.append("signal_strength=").append(data.getSignalStrength());
                hasFields = true;
            }
            
            if (data.getFlightMode() != null) {
                if (hasFields) sb.append(",");
                sb.append("flight_mode=\"").append(data.getFlightMode()).append("\"");
                hasFields = true;
            }
            
            if (data.getTemperature() != null) {
                if (hasFields) sb.append(",");
                sb.append("temperature=").append(data.getTemperature());
                hasFields = true;
            }
            
            // If no fields are present, we can't write the data
            if (!hasFields) {
                log.warn("No fields present in telemetry data for drone {}, skipping write", data.getDroneId());
                return;
            }
            
            // Add timestamp in nanoseconds
            Instant timestamp = data.getTimestamp();
            long timestampNanos = TimeUnit.SECONDS.toNanos(timestamp.getEpochSecond()) + timestamp.getNano();
            sb.append(" ").append(timestampNanos);
            
            // Get the final line protocol string
            String lineProtocol = sb.toString();
            log.debug("Line protocol: {}", lineProtocol);
            
            // Write the data to InfluxDB
            try (WriteApi writeApi = influxDBClient.getWriteApi()) {
                writeApi.writeRecord(bucket, organization, WritePrecision.NS, lineProtocol);
                log.debug("Telemetry data for drone {} written to InfluxDB (Line Protocol)", data.getDroneId());
            }
        } catch (Exception e) {
            log.error("Failed to write telemetry data to InfluxDB using Line Protocol: {}", e.getMessage(), e);
        }
    }
} 