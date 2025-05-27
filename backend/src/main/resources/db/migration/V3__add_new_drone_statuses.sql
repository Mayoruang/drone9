-- 添加新的无人机状态支持
-- V3__add_new_drone_statuses.sql

-- 删除现有的检查约束（如果存在）
ALTER TABLE drones DROP CONSTRAINT IF EXISTS drones_current_status_check;

-- 添加新的检查约束，支持更多状态
ALTER TABLE drones ADD CONSTRAINT drones_current_status_check 
    CHECK (current_status IN (
        'OFFLINE', 
        'ONLINE', 
        'FLYING', 
        'IDLE', 
        'ERROR',
        'LOW_BATTERY',
        'TRAJECTORY_ERROR',
        'GEOFENCE_VIOLATION'
    ));

-- 添加注释说明
COMMENT ON COLUMN drones.current_status IS '无人机当前状态：OFFLINE-离线, ONLINE-在线, FLYING-飞行中, IDLE-待机, ERROR-错误, LOW_BATTERY-低电量, TRAJECTORY_ERROR-轨迹异常, GEOFENCE_VIOLATION-地理围栏违规'; 