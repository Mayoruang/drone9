package com.huang.backend.drone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;

/**
 * DTO for drone command requests from API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DroneCommandRequestDto {

    /**
     * Command action type
     */
    @NotBlank(message = "命令动作不能为空")
    private String action;

    /**
     * Command parameters
     */
    private Map<String, Object> parameters;

    /**
     * Command priority (1-10, higher is more important)
     */
    @Min(value = 1, message = "优先级最小值为1")
    @Max(value = 10, message = "优先级最大值为10")
    @Builder.Default
    private Integer priority = 5;

    /**
     * Command timeout in seconds
     */
    @Min(value = 1, message = "超时时间最小值为1秒")
    @Max(value = 300, message = "超时时间最大值为300秒")
    @Builder.Default
    private Integer timeoutSeconds = 30;

    /**
     * Whether this command can be cancelled
     */
    @Builder.Default
    private Boolean cancellable = true;

    /**
     * Additional notes for the command
     */
    private String notes;
} 