-- ============================================================================
-- 地理围栏扩展表结构 V2
-- ============================================================================

-- 扩展地理围栏主表
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS geofence_type VARCHAR(32) NOT NULL DEFAULT 'NO_FLY_ZONE';
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 1;
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS area_square_meters DOUBLE PRECISION;
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS perimeter_meters DOUBLE PRECISION;
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS center_point GEOMETRY(POINT, 4326);
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS updated_by VARCHAR(128);
ALTER TABLE geofences ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- 创建地理围栏统计表
CREATE TABLE IF NOT EXISTS geofence_statistics (
    geofence_id UUID PRIMARY KEY REFERENCES geofences(geofence_id) ON DELETE CASCADE,
    drone_count INTEGER NOT NULL DEFAULT 0,
    violation_count INTEGER NOT NULL DEFAULT 0,
    last_violation_time TIMESTAMP WITH TIME ZONE,
    total_flight_time_minutes INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 地理围栏违规记录表
CREATE TABLE IF NOT EXISTS geofence_violations (
    violation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geofence_id UUID NOT NULL REFERENCES geofences(geofence_id),
    drone_id UUID NOT NULL REFERENCES drones(drone_id),
    violation_type VARCHAR(32) NOT NULL,
    violation_point GEOMETRY(POINT, 4326),
    altitude DOUBLE PRECISION,
    violation_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(128),
    notes TEXT
);

-- 地理围栏告警规则表
CREATE TABLE IF NOT EXISTS geofence_alert_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geofence_id UUID NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,
    alert_type VARCHAR(32) NOT NULL,
    condition_json TEXT,
    notification_channels TEXT[],
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 地理围栏历史版本表
CREATE TABLE IF NOT EXISTS geofence_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geofence_id UUID NOT NULL REFERENCES geofences(geofence_id),
    version INTEGER NOT NULL,
    operation VARCHAR(16) NOT NULL,
    changes_json TEXT,
    operated_by VARCHAR(128) NOT NULL,
    operated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 性能优化索引
-- ============================================================================

-- 业务索引
CREATE INDEX IF NOT EXISTS idx_geofence_type_active ON geofences(geofence_type, active);
CREATE INDEX IF NOT EXISTS idx_geofence_time_range ON geofences(start_time, end_time) WHERE start_time IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_geofence_priority ON geofences(priority DESC);
CREATE INDEX IF NOT EXISTS idx_geofence_center_point ON geofences USING GIST(center_point);

-- 违规记录索引
CREATE INDEX IF NOT EXISTS idx_violation_time ON geofence_violations(violation_time DESC);
CREATE INDEX IF NOT EXISTS idx_violation_drone_time ON geofence_violations(drone_id, violation_time DESC);
CREATE INDEX IF NOT EXISTS idx_violation_unresolved ON geofence_violations(resolved, severity) WHERE resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_violation_point ON geofence_violations USING GIST(violation_point);

-- 统计索引
CREATE INDEX IF NOT EXISTS idx_statistics_updated ON geofence_statistics(updated_at);
CREATE INDEX IF NOT EXISTS idx_alert_rules_active ON geofence_alert_rules(active, geofence_id);

-- ============================================================================
-- 触发器：自动更新时间戳
-- ============================================================================

-- 地理围栏更新时间戳触发器
CREATE OR REPLACE FUNCTION update_geofence_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER geofence_update_timestamp
    BEFORE UPDATE ON geofences
    FOR EACH ROW
    EXECUTE FUNCTION update_geofence_timestamp();

-- 统计表更新时间戳触发器
CREATE OR REPLACE FUNCTION update_statistics_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER statistics_update_timestamp
    BEFORE UPDATE ON geofence_statistics
    FOR EACH ROW
    EXECUTE FUNCTION update_statistics_timestamp();

-- ============================================================================
-- 初始化统计数据
-- ============================================================================

-- 为现有地理围栏创建统计记录
INSERT INTO geofence_statistics (geofence_id, drone_count, violation_count)
SELECT 
    g.geofence_id,
    COALESCE(d.drone_count, 0) as drone_count,
    0 as violation_count
FROM geofences g
LEFT JOIN (
    SELECT geofence_id, COUNT(*) as drone_count
    FROM drone_geofence
    GROUP BY geofence_id
) d ON g.geofence_id = d.geofence_id
ON CONFLICT (geofence_id) DO NOTHING;

-- 计算并更新地理围栏的几何属性
UPDATE geofences SET
    area_square_meters = ST_Area(geom::geography),
    perimeter_meters = ST_Perimeter(geom::geography),
    center_point = ST_Centroid(geom)
WHERE area_square_meters IS NULL; 