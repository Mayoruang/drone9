package com.huang.backend.geofence.service;

import com.huang.backend.geofence.dto.GeofenceOverviewDto;
import com.huang.backend.geofence.dto.GeofenceStatisticsDto;
import com.huang.backend.geofence.entity.Geofence;
import com.huang.backend.geofence.entity.GeofenceStatistics;
import com.huang.backend.geofence.entity.GeofenceViolation;
import com.huang.backend.geofence.repository.GeofenceRepository;
import com.huang.backend.geofence.repository.GeofenceStatisticsRepository;
import com.huang.backend.geofence.repository.GeofenceViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for geofence analytics and statistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeofenceAnalyticsService {

    private final GeofenceRepository geofenceRepository;
    private final GeofenceStatisticsRepository statisticsRepository;
    private final GeofenceViolationRepository violationRepository;

    /**
     * Get comprehensive geofence statistics
     */
    @Cacheable(value = "geofence:statistics", unless = "#result == null")
    public GeofenceStatisticsDto getGeofenceStatistics() {
        log.debug("Calculating geofence statistics");

        // Get basic counts
        List<Object[]> typeCounts = geofenceRepository.countByType();
        List<Object[]> areaByType = geofenceRepository.sumAreaByType();
        Object[] summary = geofenceRepository.getStatisticsSummary();
        
        // Calculate violation statistics
        ZonedDateTime since = ZonedDateTime.now().minusDays(30);
        Object[] violationStats = violationRepository.getViolationStatistics(since);

        // Build type statistics
        List<GeofenceStatisticsDto.GeofenceTypeStatDto> typeStatistics = new ArrayList<>();
        Map<String, Long> typeCountMap = typeCounts.stream()
                .collect(Collectors.toMap(
                        arr -> ((Geofence.GeofenceType) arr[0]).name(),
                        arr -> ((Number) arr[1]).longValue()
                ));
        
        Map<String, Double> areaMap = areaByType.stream()
                .collect(Collectors.toMap(
                        arr -> ((Geofence.GeofenceType) arr[0]).name(),
                        arr -> arr[1] != null ? ((Number) arr[1]).doubleValue() : 0.0
                ));

        for (Geofence.GeofenceType type : Geofence.GeofenceType.values()) {
            String typeName = type.name();
            typeStatistics.add(GeofenceStatisticsDto.GeofenceTypeStatDto.builder()
                    .type(typeName)
                    .count(typeCountMap.getOrDefault(typeName, 0L))
                    .totalArea(areaMap.getOrDefault(typeName, 0.0))
                    .violationCount(0L) // TODO: Calculate per-type violations
                    .build());
        }

        return GeofenceStatisticsDto.builder()
                .totalCount(summary != null && summary[0] != null ? ((Number) summary[0]).longValue() : 0L)
                .activeCount(summary != null && summary[1] != null ? ((Number) summary[1]).longValue() : 0L)
                .totalArea(summary != null && summary[2] != null ? ((Number) summary[2]).doubleValue() : 0.0)
                .noFlyZoneCount(typeCountMap.getOrDefault("NO_FLY_ZONE", 0L))
                .flyZoneCount(typeCountMap.getOrDefault("FLY_ZONE", 0L))
                .restrictedZoneCount(typeCountMap.getOrDefault("RESTRICTED_ZONE", 0L))
                .violationCount(violationStats != null && violationStats[0] != null ? 
                        ((Number) violationStats[0]).longValue() : 0L)
                .typeStatistics(typeStatistics)
                .build();
    }

    /**
     * Get geofence overview data for dashboard
     */
    @Cacheable(value = "geofence:overview", unless = "#result == null")
    public GeofenceOverviewDto getGeofenceOverview() {
        log.debug("Generating geofence overview");

        // Get daily violations for last 7 days
        ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);
        List<Object[]> dailyViolations = violationRepository.countViolationsByDate(sevenDaysAgo);
        
        Map<String, Long> dailyViolationMap = dailyViolations.stream()
                .collect(Collectors.toMap(
                        arr -> arr[0].toString(),
                        arr -> ((Number) arr[1]).longValue()
                ));

        // Get monthly trends for last 12 months
        ZonedDateTime twelveMonthsAgo = ZonedDateTime.now().minusMonths(12);
        List<Object[]> monthlyData = violationRepository.countViolationsByDate(twelveMonthsAgo);
        
        Map<String, Long> monthlyTrends = new HashMap<>();
        // Group by month-year
        for (Object[] row : monthlyData) {
            LocalDate date = (LocalDate) row[0];
            String monthKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            long count = ((Number) row[1]).longValue();
            monthlyTrends.merge(monthKey, count, Long::sum);
        }

        // Get top violation geofences
        List<GeofenceStatistics> topViolationStats = statisticsRepository.findTopViolationGeofences();
        List<GeofenceOverviewDto.TopViolationGeofenceDto> topViolationGeofences = 
                topViolationStats.stream()
                        .limit(10)
                        .map(stats -> GeofenceOverviewDto.TopViolationGeofenceDto.builder()
                                .geofenceId(stats.getGeofenceId())
                                .name(stats.getGeofence().getName())
                                .type(stats.getGeofence().getGeofenceType().name())
                                .violationCount(stats.getViolationCount().longValue())
                                .lastViolationTime(stats.getLastViolationTime())
                                .build())
                        .collect(Collectors.toList());

        // Get recent activities (simplified)
        List<GeofenceOverviewDto.RecentActivityDto> recentActivities = new ArrayList<>();
        // TODO: Implement recent activities based on geofence history

        return GeofenceOverviewDto.builder()
                .dailyViolations(dailyViolationMap)
                .monthlyTrends(monthlyTrends)
                .topViolationGeofences(topViolationGeofences)
                .recentActivities(recentActivities)
                .build();
    }

    /**
     * Get statistics for a specific geofence
     */
    public GeofenceStatistics getGeofenceDetailStatistics(java.util.UUID geofenceId) {
        return statisticsRepository.findById(geofenceId).orElse(null);
    }

    /**
     * Update statistics for all geofences
     */
    @Transactional
    public void updateAllStatistics() {
        log.info("Updating all geofence statistics");
        
        List<Geofence> activeGeofences = geofenceRepository.findByActiveTrue();
        
        for (Geofence geofence : activeGeofences) {
            updateGeofenceStatistics(geofence.getGeofenceId());
        }
    }

    /**
     * Update statistics for a specific geofence
     */
    @Transactional
    public void updateGeofenceStatistics(java.util.UUID geofenceId) {
        GeofenceStatistics stats = statisticsRepository.findById(geofenceId)
                .orElse(GeofenceStatistics.builder()
                        .geofenceId(geofenceId)
                        .droneCount(0)
                        .violationCount(0)
                        .totalFlightTimeMinutes(0)
                        .build());

        // Update drone count
        int droneCount = geofenceRepository.findById(geofenceId)
                .map(geofence -> geofence.getDrones().size())
                .orElse(0);
        stats.setDroneCount(droneCount);

        // Update violation count
        long violationCount = violationRepository.countViolationsByGeofence().stream()
                .filter(arr -> arr[0].equals(geofenceId))
                .findFirst()
                .map(arr -> ((Number) arr[1]).longValue())
                .orElse(0L);
        stats.setViolationCount((int) violationCount);

        // Update last violation time
        List<GeofenceViolation> recentViolations = violationRepository
                .findByGeofence_GeofenceIdOrderByViolationTimeDesc(
                        geofenceId, 
                        org.springframework.data.domain.PageRequest.of(0, 1)
                ).getContent();
        
        if (!recentViolations.isEmpty()) {
            stats.setLastViolationTime(recentViolations.get(0).getViolationTime());
        }

        statisticsRepository.save(stats);
    }
} 