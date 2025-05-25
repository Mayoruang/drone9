-- 创建地理围栏表
CREATE TABLE IF NOT EXISTS geofences (
    geofence_id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    geom GEOMETRY(POLYGON, 4326) NOT NULL,
    thumbnail_url VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    altitude_min DOUBLE PRECISION,
    altitude_max DOUBLE PRECISION,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 创建地理围栏索引
CREATE INDEX IF NOT EXISTS idx_geofence_geom ON geofences USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_geofence_active ON geofences(active);
CREATE INDEX IF NOT EXISTS idx_geofence_name ON geofences(name);
CREATE INDEX IF NOT EXISTS idx_geofence_created_at ON geofences(created_at);

-- 创建无人机与地理围栏关联表
CREATE TABLE IF NOT EXISTS drone_geofence (
    drone_id UUID NOT NULL REFERENCES drones(drone_id) ON DELETE CASCADE,
    geofence_id UUID NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,
    bound_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (drone_id, geofence_id)
);

-- 创建关联表索引
CREATE INDEX IF NOT EXISTS idx_drone_geofence_drone_id ON drone_geofence(drone_id);
CREATE INDEX IF NOT EXISTS idx_drone_geofence_geofence_id ON drone_geofence(geofence_id); 