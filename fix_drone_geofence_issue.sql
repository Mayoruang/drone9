-- 修复无人机地理围栏问题的SQL脚本

-- 1. 检查当前数据状态
SELECT 'Checking existing data...' as status;

-- 检查无人机数量
SELECT COUNT(*) as drone_count FROM drones;

-- 检查地理围栏数量  
SELECT COUNT(*) as geofence_count FROM geofences;

-- 检查关联关系数量
SELECT COUNT(*) as association_count FROM drone_geofence_associations;

-- 2. 确保问题中的无人机存在
-- 如果不存在，创建一个测试无人机
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM drones WHERE drone_id = '17b6627e-3dd1-4e56-ba2c-80777cf3ef4b') THEN
        -- 首先创建注册请求
        INSERT INTO drone_registration_requests (
            request_id, 
            serial_number, 
            model, 
            requested_at, 
            status, 
            processed_at, 
            admin_notes
        ) VALUES (
            gen_random_uuid(),
            'TEST-DRONE-001',
            'Test Drone Model',
            NOW() - INTERVAL '1 day',
            'APPROVED',
            NOW() - INTERVAL '12 hours',
            'Test drone for geofence debugging'
        );
        
        -- 然后创建无人机记录
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
        ) VALUES (
            '17b6627e-3dd1-4e56-ba2c-80777cf3ef4b',
            'TEST-DRONE-001',
            'Test Drone Model',
            (SELECT request_id FROM drone_registration_requests WHERE serial_number = 'TEST-DRONE-001'),
            NOW() - INTERVAL '12 hours',
            'tcp://localhost:1883',
            'test_drone_001',
            '$2a$10$dummy.hash.for.testing.purposes.only',
            'telemetry/test_drone_001',
            'commands/test_drone_001',
            NOW() - INTERVAL '5 minutes',
            'ONLINE',
            NOW() - INTERVAL '12 hours',
            NOW() - INTERVAL '12 hours'
        );
        
        RAISE NOTICE 'Created test drone with ID: 17b6627e-3dd1-4e56-ba2c-80777cf3ef4b';
    ELSE
        RAISE NOTICE 'Drone already exists: 17b6627e-3dd1-4e56-ba2c-80777cf3ef4b';
    END IF;
END $$;

-- 3. 确保有一些测试地理围栏
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM geofences) = 0 THEN
        -- 创建测试地理围栏
        INSERT INTO geofences (
            geofence_id,
            name,
            description,
            geom,
            active,
            geofence_type,
            priority,
            area_square_meters,
            perimeter_meters,
            altitude_min,
            altitude_max,
            created_by,
            created_at,
            updated_at
        ) VALUES 
        (
            gen_random_uuid(),
            '测试禁飞区1',
            '用于测试的禁飞区域',
            ST_GeomFromText('POLYGON((123.4 41.8, 123.5 41.8, 123.5 41.9, 123.4 41.9, 123.4 41.8))', 4326),
            true,
            'NO_FLY_ZONE',
            1,
            1000000.0,
            4000.0,
            0.0,
            100.0,
            'system',
            NOW(),
            NOW()
        ),
        (
            gen_random_uuid(),
            '测试限飞区1', 
            '用于测试的限飞区域',
            ST_GeomFromText('POLYGON((123.3 41.7, 123.4 41.7, 123.4 41.8, 123.3 41.8, 123.3 41.7))', 4326),
            true,
            'RESTRICTED_ZONE',
            2,
            1000000.0,
            4000.0,
            0.0,
            50.0,
            'system',
            NOW(),
            NOW()
        ),
        (
            gen_random_uuid(),
            '测试允飞区1',
            '用于测试的允飞区域', 
            ST_GeomFromText('POLYGON((123.2 41.6, 123.3 41.6, 123.3 41.7, 123.2 41.7, 123.2 41.6))', 4326),
            true,
            'FLY_ZONE',
            3,
            1000000.0,
            4000.0,
            0.0,
            200.0,
            'system',
            NOW(),
            NOW()
        );
        
        RAISE NOTICE 'Created test geofences';
    ELSE
        RAISE NOTICE 'Geofences already exist: %', (SELECT COUNT(*) FROM geofences);
    END IF;
END $$;

-- 4. 验证数据完整性
SELECT 'Data verification results:' as status;

-- 验证无人机
SELECT 
    drone_id,
    serial_number,
    model,
    current_status,
    created_at
FROM drones 
WHERE drone_id = '17b6627e-3dd1-4e56-ba2c-80777cf3ef4b';

-- 验证地理围栏
SELECT 
    geofence_id,
    name,
    geofence_type,
    active,
    created_at
FROM geofences 
ORDER BY created_at DESC
LIMIT 5;

-- 5. 清理可能存在的坏数据
-- 删除孤立的关联记录（引用不存在的无人机或地理围栏）
DELETE FROM drone_geofence_associations 
WHERE drone_id NOT IN (SELECT drone_id FROM drones)
   OR geofence_id NOT IN (SELECT geofence_id FROM geofences);

SELECT 'Database repair completed!' as status; 