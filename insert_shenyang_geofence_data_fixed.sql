-- 修复版本：以沈阳为中心的地理围栏数据

-- 插入更多沈阳地区的地理围栏
INSERT INTO geofences (
    geofence_id, name, description, geom, center_point, thumbnail_url, active, 
    geofence_type, priority, area_square_meters, perimeter_meters,
    altitude_min, altitude_max, created_by, created_at, updated_at
) VALUES
-- 沈阳故宫周边禁飞区
('60000000-6000-6000-6000-600000000000', '沈阳故宫文物保护区', '沈阳故宫周边文物保护禁飞区域，严禁任何无人机飞行', 
 ST_GeomFromText('POLYGON((123.446 41.797, 123.452 41.797, 123.452 41.801, 123.446 41.801, 123.446 41.797))', 4326),
 ST_GeomFromText('POINT(123.449 41.799)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.449,41.799&width=300&height=200&zoom=16&markers=123.449,41.799',
 true, 'NO_FLY_ZONE', 10, 250000, 2000, 0, 120, 'system', NOW(), NOW()),

-- 沈阳桃仙机场禁飞区
('61000000-6100-6100-6100-610000000000', '桃仙机场核心禁飞区', '沈阳桃仙国际机场核心区域，绝对禁止无人机飞行', 
 ST_GeomFromText('POLYGON((123.48 41.635, 123.52 41.635, 123.52 41.655, 123.48 41.655, 123.48 41.635))', 4326),
 ST_GeomFromText('POINT(123.5 41.645)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.5,41.645&width=300&height=200&zoom=14&markers=123.5,41.645',
 true, 'NO_FLY_ZONE', 10, 2000000, 6000, 0, 500, 'system', NOW(), NOW()),

-- 沈阳北站商务区测试区
('62000000-6200-6200-6200-620000000000', '北站商务区无人机示范区', '沈阳北站商务区无人机应用示范和测试区域', 
 ST_GeomFromText('POLYGON((123.415 41.815, 123.425 41.815, 123.425 41.825, 123.415 41.825, 123.415 41.815))', 4326),
 ST_GeomFromText('POINT(123.42 41.82)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.42,41.82&width=300&height=200&zoom=15&markers=123.42,41.82',
 true, 'FLY_ZONE', 5, 1000000, 4000, 0, 80, 'system', NOW(), NOW()),

-- 沈阳浑河沿岸公园飞行区
('63000000-6300-6300-6300-630000000000', '浑河公园休闲飞行区', '浑河沿岸公园区域，允许休闲娱乐性无人机飞行', 
 ST_GeomFromText('POLYGON((123.395 41.74, 123.415 41.74, 123.415 41.75, 123.395 41.75, 123.395 41.74))', 4326),
 ST_GeomFromText('POINT(123.405 41.745)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.405,41.745&width=300&height=200&zoom=15&markers=123.405,41.745',
 true, 'FLY_ZONE', 3, 500000, 3000, 0, 50, 'system', NOW(), NOW()),

-- 沈阳大学城科技园区
('64000000-6400-6400-6400-640000000000', '大学城科技创新园', '沈阳大学城科技园区无人机研发测试基地', 
 ST_GeomFromText('POLYGON((123.46 41.715, 123.475 41.715, 123.475 41.725, 123.46 41.725, 123.46 41.715))', 4326),
 ST_GeomFromText('POINT(123.4675 41.72)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.4675,41.72&width=300&height=200&zoom=15&markers=123.4675,41.72',
 true, 'FLY_ZONE', 6, 750000, 3500, 0, 100, 'system', NOW(), NOW()),

-- 棋盘山风景区限制区
('65000000-6500-6500-6500-650000000000', '棋盘山生态保护区', '棋盘山国家森林公园生态敏感区域，限制飞行时段', 
 ST_GeomFromText('POLYGON((123.52 41.83, 123.54 41.83, 123.54 41.85, 123.52 41.85, 123.52 41.83))', 4326),
 ST_GeomFromText('POINT(123.53 41.84)', 4326),
 'https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=123.53,41.84&width=300&height=200&zoom=14&markers=123.53,41.84',
 true, 'RESTRICTED_ZONE', 7, 2000000, 6000, 0, 60, 'system', NOW(), NOW());

-- 验证数据插入结果
SELECT 'Data insertion completed. Current geofence count:' as message;
SELECT COUNT(*) as total_geofences FROM geofences;
SELECT name, ST_AsText(center_point) as center, thumbnail_url IS NOT NULL as has_thumbnail FROM geofences ORDER BY created_at; 