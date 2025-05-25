package com.huang.backend.geofence.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for point query requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointQueryDto {
    
    @NotNull(message = "经度不能为空")
    private Double longitude;
    
    @NotNull(message = "纬度不能为空")
    private Double latitude;
} 