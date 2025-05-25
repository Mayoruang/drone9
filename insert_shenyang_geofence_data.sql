-- 清理原有数据并插入以沈阳为中心的地理围栏数据
-- 沈阳市中心坐标大约：123.43, 41.8

-- 1. 更新现有地理围栏的缩略图URL
UPDATE geofences SET thumbnail_url = 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.45,41.73&width=300&height=200&zoom=15&markers=123.45,41.73' WHERE geofence_id = '11111111-1111-1111-1111-111111111111';
UPDATE geofences SET thumbnail_url = 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.41,41.71&width=300&height=200&zoom=15&markers=123.41,41.71' WHERE geofence_id = '22222222-2222-2222-2222-222222222222';
UPDATE geofences SET thumbnail_url = 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.44,41.76&width=300&height=200&zoom=15&markers=123.44,41.76' WHERE geofence_id = '33333333-3333-3333-3333-333333333333';
UPDATE geofences SET thumbnail_url = 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.48,41.69&width=300&height=200&zoom=15&markers=123.48,41.69' WHERE geofence_id = '44444444-4444-4444-4444-444444444444';
UPDATE geofences SET thumbnail_url = 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.42,41.74&width=300&height=200&zoom=15&markers=123.42,41.74' WHERE geofence_id = '55555555-5555-5555-5555-555555555555';

-- 2. 插入更多沈阳地区的地理围栏
INSERT INTO geofences (
    geofence_id, name, description, geom, center_point, thumbnail_url, active, 
    geofence_type, priority, area_square_meters, perimeter_meters,
    altitude_min, altitude_max, start_time, end_time, created_by, created_at, updated_at
) VALUES
-- 沈阳故宫周边禁飞区
('60000000-6000-6000-6000-600000000000', '沈阳故宫文物保护区', '沈阳故宫周边文物保护禁飞区域，严禁任何无人机飞行', 
 ST_GeomFromText('POLYGON((123.446 41.797, 123.452 41.797, 123.452 41.801, 123.446 41.801, 123.446 41.797))', 4326),
 ST_GeomFromText('POINT(123.449 41.799)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.449,41.799&width=300&height=200&zoom=16&markers=123.449,41.799',
 true, 'NO_FLY_ZONE', 10, 250000, 2000, 0, 120, NULL, NULL, 'system', NOW(), NOW()),

-- 沈阳桃仙机场禁飞区
('61000000-6100-6100-6100-610000000000', '桃仙机场核心禁飞区', '沈阳桃仙国际机场核心区域，绝对禁止无人机飞行', 
 ST_GeomFromText('POLYGON((123.48 41.635, 123.52 41.635, 123.52 41.655, 123.48 41.655, 123.48 41.635))', 4326),
 ST_GeomFromText('POINT(123.5 41.645)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.5,41.645&width=300&height=200&zoom=14&markers=123.5,41.645',
 true, 'NO_FLY_ZONE', 10, 2000000, 6000, 0, 500, NULL, NULL, 'system', NOW(), NOW()),

-- 沈阳北站商务区测试区
('62000000-6200-6200-6200-620000000000', '北站商务区无人机示范区', '沈阳北站商务区无人机应用示范和测试区域', 
 ST_GeomFromText('POLYGON((123.415 41.815, 123.425 41.815, 123.425 41.825, 123.415 41.825, 123.415 41.815))', 4326),
 ST_GeomFromText('POINT(123.42 41.82)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.42,41.82&width=300&height=200&zoom=15&markers=123.42,41.82',
 true, 'FLY_ZONE', 5, 1000000, 4000, 0, 80, '08:00:00', '18:00:00', 'system', NOW(), NOW()),

-- 沈阳浑河沿岸公园飞行区
('63000000-6300-6300-6300-630000000000', '浑河公园休闲飞行区', '浑河沿岸公园区域，允许休闲娱乐性无人机飞行', 
 ST_GeomFromText('POLYGON((123.395 41.74, 123.415 41.74, 123.415 41.75, 123.395 41.75, 123.395 41.74))', 4326),
 ST_GeomFromText('POINT(123.405 41.745)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.405,41.745&width=300&height=200&zoom=15&markers=123.405,41.745',
 true, 'FLY_ZONE', 3, 500000, 3000, 0, 50, '06:00:00', '22:00:00', 'system', NOW(), NOW()),

-- 沈阳大学城科技园区
('64000000-6400-6400-6400-640000000000', '大学城科技创新园', '沈阳大学城科技园区无人机研发测试基地', 
 ST_GeomFromText('POLYGON((123.46 41.715, 123.475 41.715, 123.475 41.725, 123.46 41.725, 123.46 41.715))', 4326),
 ST_GeomFromText('POINT(123.4675 41.72)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.4675,41.72&width=300&height=200&zoom=15&markers=123.4675,41.72',
 true, 'FLY_ZONE', 6, 750000, 3500, 0, 100, '09:00:00', '17:00:00', 'system', NOW(), NOW()),

-- 棋盘山风景区限制区
('65000000-6500-6500-6500-650000000000', '棋盘山生态保护区', '棋盘山国家森林公园生态敏感区域，限制飞行时段', 
 ST_GeomFromText('POLYGON((123.52 41.83, 123.54 41.83, 123.54 41.85, 123.52 41.85, 123.52 41.83))', 4326),
 ST_GeomFromText('POINT(123.53 41.84)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.53,41.84&width=300&height=200&zoom=14&markers=123.53,41.84',
 true, 'RESTRICTED_ZONE', 7, 2000000, 6000, 0, 60, '10:00:00', '16:00:00', 'system', NOW(), NOW());

-- 3. 插入地理围栏统计数据
INSERT INTO geofence_statistics (
    geofence_id, drone_count, violation_count, total_flight_time, last_violation_time, 
    last_updated, avg_flight_altitude, max_recorded_speed
) VALUES
('60000000-6000-6000-6000-600000000000', 0, 2, 0, '2024-01-15 14:30:00', NOW(), 0, 0),
('61000000-6100-6100-6100-610000000000', 0, 8, 0, '2024-02-20 09:15:00', NOW(), 0, 0),
('62000000-6200-6200-6200-620000000000', 12, 1, 3600, '2024-01-28 16:45:00', NOW(), 45, 25.5),
('63000000-6300-6300-6300-630000000000', 8, 0, 2400, NULL, NOW(), 35, 15.2),
('64000000-6400-6400-6400-640000000000', 15, 3, 5400, '2024-02-10 11:30:00', NOW(), 55, 30.8),
('65000000-6500-6500-6500-650000000000', 5, 1, 1800, '2024-01-05 13:20:00', NOW(), 40, 18.5);

-- 4. 插入一些违规记录
INSERT INTO violation_records (
    violation_id, geofence_id, drone_id, violation_type, violation_time, 
    location_lat, location_lng, altitude, speed, severity, description, 
    resolved, resolved_by, resolved_at, resolution_notes, created_at
) VALUES
-- 故宫区域违规
('v1000000-1000-1000-1000-100000000000', '60000000-6000-6000-6000-600000000000', '00000000-0000-0000-0000-000000000000', 
 'UNAUTHORIZED_ENTRY', '2024-01-15 14:30:00', 41.799, 123.449, 25, 8.5, 'HIGH', 
 '无人机未经授权进入文物保护区', true, 'admin', '2024-01-15 15:00:00', '已通知操作员并强制降落', NOW()),

-- 机场区域违规
('v2000000-2000-2000-2000-200000000000', '61000000-6100-6100-6100-610000000000', '00000000-0000-0000-0000-000000000000', 
 'ALTITUDE_VIOLATION', '2024-02-20 09:15:00', 41.645, 123.5, 150, 35.2, 'CRITICAL', 
 '无人机在机场核心区域超高飞行', true, 'admin', '2024-02-20 09:30:00', '立即启动应急程序，强制返航', NOW()),

-- 大学城测试违规
('v3000000-3000-3000-3000-300000000000', '64000000-6400-6400-6400-640000000000', '00000000-0000-0000-0000-000000000000', 
 'TIME_VIOLATION', '2024-02-10 11:30:00', 41.72, 123.4675, 65, 12.3, 'MEDIUM', 
 '在非授权时间段进行飞行测试', true, 'operator', '2024-02-10 12:00:00', '已调整飞行计划时间', NOW());

-- 验证数据插入结果
SELECT 'Data insertion completed. Current geofence count:' as message;
SELECT COUNT(*) as total_geofences FROM geofences;
SELECT name, ST_AsText(center_point) as center, thumbnail_url IS NOT NULL as has_thumbnail FROM geofences ORDER BY created_at; 