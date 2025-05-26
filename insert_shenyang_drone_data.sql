-- 插入沈阳地区的无人机Mock数据
-- 沈阳坐标范围大约在: 
-- 纬度: 41.6 - 42.0
-- 经度: 123.2 - 123.6

-- 首先创建一些注册请求记录（因为无人机实体需要registration_request_id）
INSERT INTO drone_registration_requests (
    request_id, 
    serial_number, 
    model, 
    requested_at, 
    status, 
    processed_at, 
    admin_notes
) VALUES 
(
    gen_random_uuid(),
    'SY-DJI-001',
    '大疆 Mavic Air 2',
    NOW() - INTERVAL '7 days',
    'APPROVED',
    NOW() - INTERVAL '6 days',
    '沈阳市政府大楼区域巡检 - 申请人: 张伟 (zhangwei@drone.com)'
),
(
    gen_random_uuid(),
    'SY-DJI-002', 
    '大疆 Mini 3 Pro',
    NOW() - INTERVAL '5 days',
    'APPROVED',
    NOW() - INTERVAL '4 days',
    '沈阳工业大学校园监控 - 申请人: 李娜 (lina@drone.com)'
),
(
    gen_random_uuid(),
    'SY-AUTEL-001',
    'Autel EVO II Pro',
    NOW() - INTERVAL '3 days',
    'APPROVED',
    NOW() - INTERVAL '2 days',
    '沈阳北站物流园区 - 申请人: 王军 (wangjun@drone.com)'
),
(
    gen_random_uuid(),
    'SY-YUNEEC-001',
    'Yuneec Typhoon H Plus',
    NOW() - INTERVAL '4 days',
    'APPROVED',
    NOW() - INTERVAL '3 days',
    '沈阳农业科技园 - 申请人: 刘芳 (liufang@drone.com)'
),
(
    gen_random_uuid(),
    'SY-DJI-003',
    '大疆 Phantom 4 RTK',
    NOW() - INTERVAL '2 days',
    'APPROVED',
    NOW() - INTERVAL '1 day',
    '沈阳奥体中心建设项目 - 申请人: 陈明 (chenming@drone.com)'
),
(
    gen_random_uuid(),
    'SY-DJI-004',
    '大疆 Matrice 300 RTK',
    NOW() - INTERVAL '6 days',
    'APPROVED',
    NOW() - INTERVAL '5 days',
    '沈阳桃仙机场安防 - 申请人: 赵丽 (zhaoli@drone.com)'
);

-- 插入无人机数据，使用刚创建的注册请求ID
INSERT INTO drones (
    drone_id,
    serial_number,
    model,
    registration_request_id,
    approved_at,
    mqtt_broker_url,
    mqtt_username,
    mqtt_password_hash,
    mqtt_topic_telemetry,
    mqtt_topic_commands,
    last_heartbeat_at,
    current_status,
    created_at,
    updated_at
)
SELECT 
    gen_random_uuid(),
    rr.serial_number,
    rr.model,
    rr.request_id,
    rr.processed_at,
    'tcp://localhost:1883',
    LOWER(REPLACE(rr.serial_number, '-', '_')),
    '$2a$10$dummy.hash.for.mock.data.only.testing.purposes',
    'telemetry/' || LOWER(REPLACE(rr.serial_number, '-', '_')),
    'commands/' || LOWER(REPLACE(rr.serial_number, '-', '_')),
    CASE 
        WHEN rr.serial_number IN ('SY-DJI-001', 'SY-DJI-002', 'SY-AUTEL-001') THEN NOW() - INTERVAL '5 minutes'
        WHEN rr.serial_number = 'SY-DJI-003' THEN NOW() - INTERVAL '2 hours'
        ELSE NOW() - INTERVAL '1 day'
    END,
    CASE 
        WHEN rr.serial_number = 'SY-DJI-001' THEN 'FLYING'
        WHEN rr.serial_number IN ('SY-DJI-002', 'SY-AUTEL-001') THEN 'ONLINE'
        WHEN rr.serial_number = 'SY-DJI-003' THEN 'IDLE'
        ELSE 'OFFLINE'
    END,
    rr.processed_at,
    rr.processed_at
FROM drone_registration_requests rr
WHERE rr.serial_number LIKE 'SY-%'
  AND rr.status = 'APPROVED';

-- 为每个无人机插入遥测数据（位置在沈阳不同地点）
-- 使用InfluxDB的方式插入数据需要通过应用程序，这里我们可以通过HTTP API模拟

-- 注意：以下是一些沈阳地区的具体位置参考：
-- 沈阳故宫: 41.7963, 123.4318
-- 沈阳市政府: 41.8057, 123.4315  
-- 沈阳工业大学: 41.8342, 123.4222
-- 沈阳北站: 41.8751, 123.4697
-- 沈阳农业大学: 41.8267, 123.5631
-- 沈阳奥体中心: 41.7056, 123.4464
-- 沈阳桃仙机场: 41.6398, 123.4830

-- 为了在数据库中保存位置信息，我们需要在无人机表中添加当前位置字段
-- 这些数据将通过遥测数据的形式存储在InfluxDB中，但为了演示目的，我们也可以在关系数据库中存储最后已知位置

-- 添加无人机的最后已知位置信息（如果表结构支持的话）
-- 注意：这里假设我们可以通过其他方式（如API调用）来设置遥测数据

-- 创建一些模拟的任务和飞行计划（可选）
-- 这些数据可以帮助测试地理围栏功能

COMMIT; 