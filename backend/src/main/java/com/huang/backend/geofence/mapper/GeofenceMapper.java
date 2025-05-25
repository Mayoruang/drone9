package com.huang.backend.geofence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huang.backend.drone.entity.Drone;
import com.huang.backend.geofence.dto.GeofenceCreateDto;
import com.huang.backend.geofence.dto.GeofenceDetailDto;
import com.huang.backend.geofence.dto.GeofenceListItemDto;
import com.huang.backend.geofence.entity.Geofence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Geofence entity and DTOs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeofenceMapper {

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    
    /**
     * Convert a Geofence entity to a GeofenceListItemDto
     */
    public GeofenceListItemDto toListItemDto(Geofence geofence) {
        log.debug("Converting geofence {} to list item DTO", geofence.getGeofenceId());
        
        try {
            double[] center = getCenterPoint(geofence.getGeom());
            Object geometry = geometryToGeoJson(geofence.getGeom());
            
            return GeofenceListItemDto.builder()
                    .geofenceId(geofence.getGeofenceId())
                    .name(geofence.getName())
                    .description(geofence.getDescription())
                    .center(center)
                    .geometry(geometry)
                    .geofenceType(geofence.getGeofenceType().name())
                    .thumbnailUrl(geofence.getThumbnailUrl())
                    .active(geofence.isActive())
                    .altitudeMin(geofence.getAltitudeMin())
                    .altitudeMax(geofence.getAltitudeMax())
                    .priority(geofence.getPriority())
                    .areaSquareMeters(geofence.getAreaSquareMeters())
                    .createdAt(geofence.getCreatedAt())
                    .updatedAt(geofence.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Error converting geofence {} to list item DTO: {}", geofence.getGeofenceId(), e.getMessage(), e);
            throw new RuntimeException("Error converting geofence to DTO", e);
        }
    }
    
    /**
     * Convert a Geofence entity to a GeofenceDetailDto
     */
    public GeofenceDetailDto toDetailDto(Geofence geofence) {
        return GeofenceDetailDto.builder()
                .geofenceId(geofence.getGeofenceId())
                .name(geofence.getName())
                .description(geofence.getDescription())
                .geometry(geometryToGeoJson(geofence.getGeom()))
                .geofenceType(geofence.getGeofenceType().name())
                .thumbnailUrl(geofence.getThumbnailUrl())
                .active(geofence.isActive())
                .altitudeMin(geofence.getAltitudeMin())
                .altitudeMax(geofence.getAltitudeMax())
                .startTime(geofence.getStartTime())
                .endTime(geofence.getEndTime())
                .createdBy(geofence.getCreatedBy())
                .createdAt(geofence.getCreatedAt())
                .updatedAt(geofence.getUpdatedAt())
                .drones(geofence.getDrones().stream()
                        .map(this::toDroneMiniDto)
                        .collect(Collectors.toList()))
                .build();
    }
    
    /**
     * Convert a Drone entity to a DroneMiniDto
     */
    private GeofenceDetailDto.DroneMiniDto toDroneMiniDto(Drone drone) {
        return GeofenceDetailDto.DroneMiniDto.builder()
                .droneId(drone.getDroneId())
                .serialNumber(drone.getSerialNumber())
                .model(drone.getModel())
                .build();
    }
    
    /**
     * Convert a GeofenceCreateDto to a Geofence entity
     */
    public Geofence toEntity(GeofenceCreateDto dto) {
        Geofence geofence = new Geofence();
        geofence.setName(dto.getName());
        geofence.setDescription(dto.getDescription());
        geofence.setGeom(geoJsonToPolygon(dto.getGeometry()));
        geofence.setGeofenceType(Geofence.GeofenceType.valueOf(dto.getGeofenceType()));
        geofence.setActive(dto.isActive());
        geofence.setAltitudeMin(dto.getAltitudeMin());
        geofence.setAltitudeMax(dto.getAltitudeMax());
        geofence.setStartTime(dto.getStartTime());
        geofence.setEndTime(dto.getEndTime());
        return geofence;
    }
    
    /**
     * Update a Geofence entity from a GeofenceCreateDto
     */
    public void updateEntityFromDto(Geofence geofence, GeofenceCreateDto dto) {
        geofence.setName(dto.getName());
        geofence.setDescription(dto.getDescription());
        geofence.setGeom(geoJsonToPolygon(dto.getGeometry()));
        geofence.setGeofenceType(Geofence.GeofenceType.valueOf(dto.getGeofenceType()));
        geofence.setActive(dto.isActive());
        geofence.setAltitudeMin(dto.getAltitudeMin());
        geofence.setAltitudeMax(dto.getAltitudeMax());
        geofence.setStartTime(dto.getStartTime());
        geofence.setEndTime(dto.getEndTime());
    }
    
    /**
     * Convert JTS Polygon to GeoJSON object
     */
    @SuppressWarnings("unchecked")
    private Object geometryToGeoJson(Polygon polygon) {
        try {
            GeoJSONWriter writer = new GeoJSONWriter();
            GeoJSON json = writer.write(polygon);
            String geoJsonString = json.toString();
            return objectMapper.readValue(geoJsonString, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting geometry to GeoJSON", e);
        }
    }
    
    /**
     * Convert GeoJSON object to JTS Polygon
     */
    private Polygon geoJsonToPolygon(Object geoJson) {
        try {
            String geoJsonString = objectMapper.writeValueAsString(geoJson);
            GeoJSONReader reader = new GeoJSONReader();
            return (Polygon) reader.read(objectMapper.readValue(geoJsonString, org.wololo.geojson.Polygon.class));
        } catch (Exception e) {
            throw new RuntimeException("Error converting GeoJSON to geometry", e);
        }
    }
    
    /**
     * Get center point of a polygon as [longitude, latitude]
     */
    private double[] getCenterPoint(Polygon polygon) {
        if (polygon == null) {
            log.warn("Polygon geometry is null, returning default center point");
            return new double[] { 0.0, 0.0 };
        }
        
        try {
            log.debug("Getting center point for polygon with SRID: {}", polygon.getSRID());
            
            Coordinate centroid = polygon.getCentroid().getCoordinate();
            if (centroid == null) {
                log.warn("Centroid calculation returned null, returning default center point");
                return new double[] { 0.0, 0.0 };
            }
            
            double[] result = new double[] { centroid.x, centroid.y };
            log.debug("Calculated center point: [{}, {}]", result[0], result[1]);
            return result;
        } catch (Exception e) {
            log.error("Error calculating center point: {}", e.getMessage(), e);
            return new double[] { 0.0, 0.0 };
        }
    }
} 