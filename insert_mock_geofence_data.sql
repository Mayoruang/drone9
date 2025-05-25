-- ============================================================================
-- 地理围栏模拟数据插入脚本
-- ============================================================================

-- 设置时区
SET TIME ZONE 'Asia/Shanghai';

-- 北京区域地理围栏
INSERT INTO geofences (
    geofence_id, name, description, geom, active, geofence_type, priority,
    altitude_min, altitude_max, created_by, created_at, updated_at,
    area_square_meters, perimeter_meters, center_point
) VALUES 
(
    '66666666-6666-6666-6666-666666666666',
    '北京天安门禁飞区',
    '北京天安门广场及周边重要政治中心区域，严格禁止一切无人机飞行活动',
    ST_GeomFromText('POLYGON((116.3894 39.9065, 116.3944 39.9065, 116.3944 39.9015, 116.3894 39.9015, 116.3894 39.9065))', 4326),
    true,
    'NO_FLY_ZONE',
    5,
    0,
    500,
    'admin',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((116.3894 39.9065, 116.3944 39.9065, 116.3944 39.9015, 116.3894 39.9015, 116.3894 39.9065))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((116.3894 39.9065, 116.3944 39.9065, 116.3944 39.9015, 116.3894 39.9015, 116.3894 39.9065))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((116.3894 39.9065, 116.3944 39.9065, 116.3944 39.9015, 116.3894 39.9015, 116.3894 39.9065))', 4326))
),
(
    '77777777-7777-7777-7777-777777777777',
    '朝阳公园允飞区',
    '北京朝阳公园内指定的无人机飞行训练区域，适合初学者练习',
    ST_GeomFromText('POLYGON((116.4563 39.9355, 116.4613 39.9355, 116.4613 39.9305, 116.4563 39.9305, 116.4563 39.9355))', 4326),
    true,
    'FLY_ZONE',
    2,
    5,
    120,
    'admin',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((116.4563 39.9355, 116.4613 39.9355, 116.4613 39.9305, 116.4563 39.9305, 116.4563 39.9355))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((116.4563 39.9355, 116.4613 39.9355, 116.4613 39.9305, 116.4563 39.9305, 116.4563 39.9355))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((116.4563 39.9355, 116.4613 39.9355, 116.4613 39.9305, 116.4563 39.9305, 116.4563 39.9355))', 4326))
),

-- 上海区域地理围栏
(
    '88888888-8888-8888-8888-888888888888',
    '上海外滩观景禁飞区',
    '上海外滩黄浦江沿岸风景区，保护游客安全和城市景观',
    ST_GeomFromText('POLYGON((121.4854 31.2484, 121.4904 31.2484, 121.4904 31.2434, 121.4854 31.2434, 121.4854 31.2484))', 4326),
    true,
    'NO_FLY_ZONE',
    4,
    0,
    300,
    'vben',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((121.4854 31.2484, 121.4904 31.2484, 121.4904 31.2434, 121.4854 31.2434, 121.4854 31.2484))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((121.4854 31.2484, 121.4904 31.2484, 121.4904 31.2434, 121.4854 31.2434, 121.4854 31.2484))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((121.4854 31.2484, 121.4904 31.2484, 121.4904 31.2434, 121.4854 31.2434, 121.4854 31.2484))', 4326))
),
(
    '99999999-9999-9999-9999-999999999999',
    '世纪公园航拍区',
    '上海世纪公园指定航拍区域，周末对摄影爱好者开放',
    ST_GeomFromText('POLYGON((121.5524 31.2073, 121.5574 31.2073, 121.5574 31.2023, 121.5524 31.2023, 121.5524 31.2073))', 4326),
    true,
    'FLY_ZONE',
    1,
    10,
    150,
    'jack',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((121.5524 31.2073, 121.5574 31.2073, 121.5574 31.2023, 121.5524 31.2023, 121.5524 31.2073))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((121.5524 31.2073, 121.5574 31.2073, 121.5574 31.2023, 121.5524 31.2023, 121.5524 31.2073))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((121.5524 31.2073, 121.5574 31.2073, 121.5574 31.2023, 121.5524 31.2023, 121.5524 31.2073))', 4326))
),

-- 深圳区域地理围栏
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '深圳湾科技园测试区',
    '深圳湾科技园区无人机技术测试专用区域',
    ST_GeomFromText('POLYGON((113.9452 22.5177, 113.9502 22.5177, 113.9502 22.5127, 113.9452 22.5127, 113.9452 22.5177))', 4326),
    true,
    'FLY_ZONE',
    3,
    5,
    200,
    'admin',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((113.9452 22.5177, 113.9502 22.5177, 113.9502 22.5127, 113.9452 22.5127, 113.9452 22.5177))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((113.9452 22.5177, 113.9502 22.5177, 113.9502 22.5127, 113.9452 22.5127, 113.9452 22.5177))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((113.9452 22.5177, 113.9502 22.5177, 113.9502 22.5127, 113.9452 22.5127, 113.9452 22.5177))', 4326))
),

-- 广州区域地理围栏
(
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '广州塔周边限制区',
    '广州塔周边重要景观区域，限制无人机飞行高度和时间',
    ST_GeomFromText('POLYGON((113.3223 23.1081, 113.3273 23.1081, 113.3273 23.1031, 113.3223 23.1031, 113.3223 23.1081))', 4326),
    true,
    'RESTRICTED_ZONE',
    3,
    0,
    100,
    'vben',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((113.3223 23.1081, 113.3273 23.1081, 113.3273 23.1031, 113.3223 23.1031, 113.3223 23.1081))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((113.3223 23.1081, 113.3273 23.1081, 113.3273 23.1031, 113.3223 23.1031, 113.3223 23.1081))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((113.3223 23.1081, 113.3273 23.1081, 113.3273 23.1031, 113.3223 23.1031, 113.3223 23.1081))', 4326))
),

-- 杭州区域地理围栏
(
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '西湖景区禁飞区',
    '杭州西湖风景名胜区核心区域，保护自然和文化遗产',
    ST_GeomFromText('POLYGON((120.1339 30.2547, 120.1389 30.2547, 120.1389 30.2497, 120.1339 30.2497, 120.1339 30.2547))', 4326),
    true,
    'NO_FLY_ZONE',
    4,
    0,
    500,
    'jack',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((120.1339 30.2547, 120.1389 30.2547, 120.1389 30.2497, 120.1339 30.2497, 120.1339 30.2547))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((120.1339 30.2547, 120.1389 30.2547, 120.1389 30.2497, 120.1339 30.2497, 120.1339 30.2547))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((120.1339 30.2547, 120.1389 30.2547, 120.1389 30.2497, 120.1339 30.2497, 120.1339 30.2547))', 4326))
),

-- 南京区域地理围栏
(
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '紫金山森林公园',
    '南京紫金山国家森林公园，环保优先区域',
    ST_GeomFromText('POLYGON((118.8274 32.0616, 118.8324 32.0616, 118.8324 32.0566, 118.8274 32.0566, 118.8274 32.0616))', 4326),
    true,
    'RESTRICTED_ZONE',
    2,
    10,
    80,
    'admin',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((118.8274 32.0616, 118.8324 32.0616, 118.8324 32.0566, 118.8274 32.0566, 118.8274 32.0616))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((118.8274 32.0616, 118.8324 32.0616, 118.8324 32.0566, 118.8274 32.0566, 118.8274 32.0616))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((118.8274 32.0616, 118.8324 32.0616, 118.8324 32.0566, 118.8274 32.0566, 118.8274 32.0616))', 4326))
),

-- 成都区域地理围栏
(
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '成都科学城创新区',
    '成都高新区科学城无人机创新研发测试区域',
    ST_GeomFromText('POLYGON((104.0658 30.5728, 104.0708 30.5728, 104.0708 30.5678, 104.0658 30.5678, 104.0658 30.5728))', 4326),
    true,
    'FLY_ZONE',
    2,
    5,
    180,
    'vben',
    NOW(),
    NOW(),
    ST_Area(ST_GeomFromText('POLYGON((104.0658 30.5728, 104.0708 30.5728, 104.0708 30.5678, 104.0658 30.5678, 104.0658 30.5728))', 4326)::geography),
    ST_Perimeter(ST_GeomFromText('POLYGON((104.0658 30.5728, 104.0708 30.5728, 104.0708 30.5678, 104.0658 30.5678, 104.0658 30.5728))', 4326)::geography),
    ST_Centroid(ST_GeomFromText('POLYGON((104.0658 30.5728, 104.0708 30.5728, 104.0708 30.5678, 104.0658 30.5678, 104.0658 30.5728))', 4326))
);

-- 插入地理围栏统计数据
INSERT INTO geofence_statistics (geofence_id, drone_count, violation_count, total_flight_time_minutes, updated_at)
VALUES 
('66666666-6666-6666-6666-666666666666', 0, 3, 0, NOW()),
('77777777-7777-7777-7777-777777777777', 2, 0, 45, NOW()),
('88888888-8888-8888-8888-888888888888', 0, 1, 0, NOW()),
('99999999-9999-9999-9999-999999999999', 5, 0, 120, NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 3, 0, 89, NOW()),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 1, 2, 15, NOW()),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 0, 5, 0, NOW()),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 2, 1, 32, NOW()),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 4, 0, 67, NOW())
ON CONFLICT (geofence_id) DO UPDATE SET
    drone_count = EXCLUDED.drone_count,
    violation_count = EXCLUDED.violation_count,
    total_flight_time_minutes = EXCLUDED.total_flight_time_minutes,
    updated_at = EXCLUDED.updated_at;

-- 创建一些模拟的无人机数据（如果不存在）
INSERT INTO drones (
    drone_id, serial_number, model, manufacturer, status, 
    current_latitude, current_longitude, current_altitude,
    battery_level, signal_strength, created_at, updated_at
) VALUES 
(
    'drone001-1111-1111-1111-111111111111',
    'DJI-M300-001',
    'Matrice 300 RTK',
    'DJI',
    'IDLE',
    39.9355,
    116.4613,
    0,
    85,
    -45,
    NOW(),
    NOW()
),
(
    'drone002-2222-2222-2222-222222222222',
    'DJI-M300-002', 
    'Matrice 300 RTK',
    'DJI',
    'IDLE',
    31.2073,
    121.5574,
    0,
    92,
    -38,
    NOW(),
    NOW()
),
(
    'drone003-3333-3333-3333-333333333333',
    'AUTEL-EVO-001',
    'EVO II Pro',
    'Autel',
    'ONLINE',
    22.5177,
    113.9502,
    15,
    76,
    -52,
    NOW(),
    NOW()
),
(
    'drone004-4444-4444-4444-444444444444',
    'DJI-MINI-001',
    'Mini 3 Pro',
    'DJI',
    'IDLE',
    30.5728,
    104.0708,
    0,
    68,
    -41,
    NOW(),
    NOW()
),
(
    'drone005-5555-5555-5555-555555555555',
    'YUNEEC-H520-001',
    'Typhoon H520',
    'Yuneec',
    'ONLINE',
    30.2547,
    120.1389,
    25,
    89,
    -35,
    NOW(),
    NOW()
)
ON CONFLICT (drone_id) DO NOTHING;

-- 建立无人机与地理围栏的关联关系
INSERT INTO drone_geofence (drone_id, geofence_id, bound_at) VALUES
('drone001-1111-1111-1111-111111111111', '77777777-7777-7777-7777-777777777777', NOW()),
('drone002-2222-2222-2222-222222222222', '77777777-7777-7777-7777-777777777777', NOW()),
('drone002-2222-2222-2222-222222222222', '99999999-9999-9999-9999-999999999999', NOW()),
('drone003-3333-3333-3333-333333333333', '99999999-9999-9999-9999-999999999999', NOW()),
('drone003-3333-3333-3333-333333333333', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', NOW()),
('drone004-4444-4444-4444-444444444444', '99999999-9999-9999-9999-999999999999', NOW()),
('drone004-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', NOW()),
('drone004-4444-4444-4444-444444444444', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', NOW()),
('drone005-5555-5555-5555-555555555555', '99999999-9999-9999-9999-999999999999', NOW()),
('drone005-5555-5555-5555-555555555555', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', NOW()),
('drone001-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', NOW()),
('drone002-2222-2222-2222-222222222222', 'dddddddd-dddd-dddd-dddd-dddddddddddd', NOW()),
('drone005-5555-5555-5555-555555555555', 'dddddddd-dddd-dddd-dddd-dddddddddddd', NOW()),
('drone003-3333-3333-3333-333333333333', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', NOW()),
('drone001-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', NOW())
ON CONFLICT (drone_id, geofence_id) DO NOTHING;

-- 创建一些违规记录示例
INSERT INTO geofence_violations (
    violation_id, geofence_id, drone_id, violation_type, 
    violation_point, altitude, violation_time, severity, 
    resolved, notes
) VALUES
(
    gen_random_uuid(),
    '66666666-6666-6666-6666-666666666666',
    'drone001-1111-1111-1111-111111111111',
    'UNAUTHORIZED_ENTRY',
    ST_SetSRID(ST_MakePoint(116.3919, 39.904), 4326),
    45,
    NOW() - INTERVAL '2 hours',
    'HIGH',
    true,
    '无人机误入天安门禁飞区，已及时召回'
),
(
    gen_random_uuid(),
    '88888888-8888-8888-8888-888888888888',
    'drone002-2222-2222-2222-222222222222',
    'ALTITUDE_VIOLATION',
    ST_SetSRID(ST_MakePoint(121.4879, 31.2459), 4326),
    350,
    NOW() - INTERVAL '1 day',
    'MEDIUM',
    true,
    '超出允许飞行高度，现已整改'
),
(
    gen_random_uuid(),
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'drone005-5555-5555-5555-555555555555',
    'UNAUTHORIZED_ENTRY',
    ST_SetSRID(ST_MakePoint(120.1364, 30.2522), 4326),
    25,
    NOW() - INTERVAL '3 days',
    'HIGH',
    false,
    '西湖景区违规飞行，正在处理中'
);

-- 显示插入结果
SELECT 
    'geofences' as table_name,
    COUNT(*) as total_count
FROM geofences
WHERE geofence_id IN (
    '66666666-6666-6666-6666-666666666666',
    '77777777-7777-7777-7777-777777777777',
    '88888888-8888-8888-8888-888888888888',
    '99999999-9999-9999-9999-999999999999',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'
)

UNION ALL

SELECT 
    'drones' as table_name,
    COUNT(*) as total_count
FROM drones
WHERE drone_id LIKE 'drone%'

UNION ALL

SELECT 
    'drone_geofence_relations' as table_name,
    COUNT(*) as total_count
FROM drone_geofence

UNION ALL

SELECT 
    'violations' as table_name,
    COUNT(*) as total_count
FROM geofence_violations;

-- 显示所有地理围栏列表
SELECT 
    name,
    geofence_type,
    active,
    created_by,
    ROUND(area_square_meters::numeric, 2) as area_m2,
    created_at
FROM geofences
ORDER BY created_at DESC; 