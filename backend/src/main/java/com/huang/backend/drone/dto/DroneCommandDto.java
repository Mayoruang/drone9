package com.huang.backend.drone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for drone control commands
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DroneCommandDto {

    /**
     * Unique command ID for tracking
     */
    private String commandId;

    /**
     * Target drone ID
     */
    @NotNull(message = "无人机ID不能为空")
    private UUID droneId;

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
    @Builder.Default
    private Integer priority = 5;

    /**
     * Command timeout in seconds
     */
    @Builder.Default
    private Integer timeoutSeconds = 30;

    /**
     * Whether this command can be cancelled
     */
    @Builder.Default
    private Boolean cancellable = true;

    /**
     * Username who issued the command
     */
    private String issuedBy;

    /**
     * Timestamp when command was issued
     */
    private ZonedDateTime issuedAt;

    /**
     * Command execution status
     */
    private CommandStatus status;

    /**
     * Response message from drone
     */
    private String response;

    /**
     * Error message if command failed
     */
    private String errorMessage;

    /**
     * Timestamp when command was executed
     */
    private ZonedDateTime executedAt;

    /**
     * Timestamp when command was completed
     */
    private ZonedDateTime completedAt;

    /**
     * Enum for command status
     */
    public enum CommandStatus {
        PENDING,    // 待发送
        SENT,       // 已发送
        RECEIVED,   // 无人机已接收
        EXECUTING,  // 执行中
        COMPLETED,  // 执行完成
        FAILED,     // 执行失败
        CANCELLED,  // 已取消
        TIMEOUT     // 超时
    }

    /**
     * Common command actions
     */
    public static class Actions {
        public static final String ARM = "ARM";
        public static final String DISARM = "DISARM";
        public static final String TAKEOFF = "TAKEOFF";
        public static final String LAND = "LAND";
        public static final String RETURN_TO_HOME = "RETURN_TO_HOME";
        public static final String HOVER = "HOVER";
        public static final String EMERGENCY_STOP = "EMERGENCY_STOP";
        public static final String SET_SPEED = "SET_SPEED";
        public static final String SET_ALTITUDE = "SET_ALTITUDE";
        public static final String MOVE_TO = "MOVE_TO";
        public static final String ROTATE = "ROTATE";
        public static final String START_MISSION = "START_MISSION";
        public static final String PAUSE_MISSION = "PAUSE_MISSION";
        public static final String RESUME_MISSION = "RESUME_MISSION";
        public static final String ABORT_MISSION = "ABORT_MISSION";
        public static final String CALIBRATE = "CALIBRATE";
        public static final String SET_HOME = "SET_HOME";
        public static final String ENABLE_FAILSAFE = "ENABLE_FAILSAFE";
        public static final String DISABLE_FAILSAFE = "DISABLE_FAILSAFE";
    }
} 