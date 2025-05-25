package com.huang.backend.drone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 无人机统计信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneStatsDto {
    
    /** 总数 */
    private int total;
    
    /** 在线数量 */
    private int online;
    
    /** 离线数量 */
    private int offline;
    
    /** 飞行中数量 */
    private int flying;
    
    /** 空闲数量 */
    private int idle;
    
    /** 错误状态数量 */
    private int error;
    
    /** 低电量数量 */
    private int lowBattery;
    
    /** 各状态详细统计 */
    private Map<String, Integer> statusCounts;
} 