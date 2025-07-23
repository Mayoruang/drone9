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
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.List;
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
    
    @Autowired
    private EntityManager entityManager;
    
    /**
     * Convert a Geofence entity to a GeofenceListItemDto
     */
    public GeofenceListItemDto toListItemDto(Geofence geofence) {
        log.debug("Converting geofence {} to list item DTO", geofence.getGeofenceId());
        
        try {
            double[] center = getCenterPoint(geofence.getGeom());
            Object geometry = geometryToGeoJson(geofence.getGeom());
            
            // 只有限制区才统计关联的无人机数量，使用单独的查询避免N+1问题
            int droneCount = 0;
            if (geofence.getGeofenceType() == Geofence.GeofenceType.RESTRICTED_ZONE) {
                Query query = entityManager.createQuery(
                    "SELECT COUNT(d) FROM Drone d JOIN d.geofences g WHERE g.geofenceId = :geofenceId");
                query.setParameter("geofenceId", geofence.getGeofenceId());
                Long count = (Long) query.getSingleResult();
                droneCount = count != null ? count.intValue() : 0;
            }
            
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
                    .droneCount(droneCount)
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
            Map<String, Object> geoJsonMap = objectMapper.readValue(geoJsonString, Map.class);
            
            // 修复：移除重复的闭合点以正确显示顶点数量
            if (geoJsonMap.containsKey("coordinates")) {
                List<List<List<Double>>> coordinates = (List<List<List<Double>>>) geoJsonMap.get("coordinates");
                if (!coordinates.isEmpty() && !coordinates.get(0).isEmpty()) {
                    List<List<Double>> ring = coordinates.get(0);
                    if (ring.size() > 1) {
                        List<Double> firstPoint = ring.get(0);
                        List<Double> lastPoint = ring.get(ring.size() - 1);
                        
                        // 如果第一个点和最后一个点相同，移除最后一个点
                        if (firstPoint != null && lastPoint != null &&
                            firstPoint.size() >= 2 && lastPoint.size() >= 2 &&
                            Math.abs(firstPoint.get(0) - lastPoint.get(0)) < 1e-10 &&
                            Math.abs(firstPoint.get(1) - lastPoint.get(1)) < 1e-10) {
                            
                            log.debug("移除重复的闭合点，原始顶点: {}, 修正后顶点: {}", 
                                    ring.size(), ring.size() - 1);
                            ring.remove(ring.size() - 1);
                        }
                    }
                }
            }
            
            return geoJsonMap;
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