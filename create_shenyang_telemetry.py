#!/usr/bin/env python3
"""
为沈阳地区的无人机创建遥测数据的脚本
通过InfluxDB API插入位置数据
"""

import requests
import json
from datetime import datetime, timedelta
import random

# InfluxDB配置
INFLUXDB_URL = "http://localhost:8086"
INFLUXDB_TOKEN = "my-super-secret-token"
INFLUXDB_ORG = "drone_org"
INFLUXDB_BUCKET = "drone_data"

# 沈阳地区的具体位置坐标
SHENYANG_LOCATIONS = {
    'SY-DJI-001': {
        'name': 'Shenyang_Palace',
        'lat': 41.7963,
        'lng': 123.4318,
        'altitude': 50,  # 飞行中
        'description': '沈阳市政府大楼区域巡检'
    },
    'SY-DJI-002': {
        'name': 'Shenyang_University',
        'lat': 41.8342,
        'lng': 123.4222,
        'altitude': 0,  # 在线但未飞行
        'description': '沈阳工业大学校园监控'
    },
    'SY-AUTEL-001': {
        'name': 'Shenyang_North_Station',
        'lat': 41.8751,
        'lng': 123.4697,
        'altitude': 0,  # 在线但未飞行
        'description': '沈阳北站物流园区'
    },
    'SY-YUNEEC-001': {
        'name': 'Shenyang_Agricultural_University',
        'lat': 41.8267,
        'lng': 123.5631,
        'altitude': 0,  # 离线
        'description': '沈阳农业科技园'
    },
    'SY-DJI-003': {
        'name': 'Shenyang_Olympic_Center',
        'lat': 41.7056,
        'lng': 123.4464,
        'altitude': 0,  # 待机
        'description': '沈阳奥体中心建设项目'
    },
    'SY-DJI-004': {
        'name': 'Shenyang_Taoxian_Airport',
        'lat': 41.6398,
        'lng': 123.4830,
        'altitude': 0,  # 离线
        'description': '沈阳桃仙机场安防'
    }
}

# 无人机状态映射
DRONE_STATUS = {
    'SY-DJI-001': 'FLYING',
    'SY-DJI-002': 'ONLINE',
    'SY-AUTEL-001': 'ONLINE',
    'SY-YUNEEC-001': 'OFFLINE',
    'SY-DJI-003': 'IDLE',
    'SY-DJI-004': 'OFFLINE'
}

def create_telemetry_data(drone_id, location_info, status, timestamp):
    """创建遥测数据点"""
    
    # 根据状态调整数据
    if status == 'FLYING':
        # 飞行中，数据更动态
        battery_level = random.uniform(60, 85)
        speed = random.uniform(5, 15)  # m/s
        altitude = location_info['altitude'] + random.uniform(-10, 20)
        signal_strength = random.uniform(80, 95)
        temperature = random.uniform(15, 25)
        heading = random.uniform(0, 359)
        satellites = random.randint(8, 12)
    elif status == 'ONLINE':
        # 在线但未飞行
        battery_level = random.uniform(85, 100)
        speed = 0
        altitude = 0
        signal_strength = random.uniform(70, 90)
        temperature = random.uniform(18, 22)
        heading = 0
        satellites = random.randint(6, 10)
    elif status == 'IDLE':
        # 待机状态
        battery_level = random.uniform(90, 100)
        speed = 0
        altitude = 0
        signal_strength = random.uniform(60, 80)
        temperature = random.uniform(20, 25)
        heading = 0
        satellites = random.randint(4, 8)
    else:  # OFFLINE
        # 离线，数据较旧或不完整
        battery_level = random.uniform(10, 30)
        speed = 0
        altitude = 0
        signal_strength = 0
        temperature = random.uniform(15, 20)
        heading = 0
        satellites = 0
    
    # 添加小的位置偏移（模拟真实情况）
    lat_offset = random.uniform(-0.001, 0.001)  # 约100米的偏移
    lng_offset = random.uniform(-0.001, 0.001)
    
    return {
        'measurement': 'drone_telemetry',
        'tags': {
            'drone_id': drone_id,
            'location': location_info['name'],
            'status': status
        },
        'fields': {
            'latitude': location_info['lat'] + lat_offset,
            'longitude': location_info['lng'] + lng_offset,
            'altitude': max(0, altitude),
            'battery_level': battery_level,
            'battery_voltage': battery_level * 0.12 + 10,  # 简单的电压计算
            'speed': speed,
            'heading': heading,
            'satellites': satellites,
            'signal_strength': signal_strength,
            'temperature': temperature,
            'flight_mode': 'HOVER' if status == 'FLYING' else 'LAND'
        },
        'time': timestamp
    }

def write_to_influxdb(data_points):
    """写入数据到InfluxDB"""
    url = f"{INFLUXDB_URL}/api/v2/write"
    headers = {
        'Authorization': f'Token {INFLUXDB_TOKEN}',
        'Content-Type': 'application/x-ndjson',
        'Accept': 'application/json'
    }
    
    params = {
        'org': INFLUXDB_ORG,
        'bucket': INFLUXDB_BUCKET,
        'precision': 's'
    }
    
    # 转换为Line Protocol格式
    lines = []
    for point in data_points:
        # 构建tags部分
        tags = ','.join([f"{k}={v}" for k, v in point['tags'].items()])
        
        # 构建fields部分
        fields = []
        for k, v in point['fields'].items():
            if isinstance(v, str):
                fields.append(f'{k}="{v}"')
            else:
                fields.append(f'{k}={v}')
        fields_str = ','.join(fields)
        
        # 构建时间戳（秒级）
        timestamp_s = int(point['time'].timestamp())
        
        line = f"{point['measurement']},{tags} {fields_str} {timestamp_s}"
        lines.append(line)
    
    data = '\n'.join(lines)
    
    try:
        response = requests.post(url, headers=headers, params=params, data=data)
        response.raise_for_status()
        print(f"✅ 成功写入 {len(data_points)} 个数据点到InfluxDB")
        return True
    except requests.exceptions.RequestException as e:
        print(f"❌ 写入InfluxDB失败: {e}")
        if hasattr(e.response, 'text'):
            print(f"响应内容: {e.response.text}")
        return False

def main():
    """主函数"""
    print("🚁 开始为沈阳地区无人机创建遥测数据...")
    
    all_data_points = []
    current_time = datetime.now()
    
    # 为每个无人机创建多个时间点的数据
    for drone_id, location_info in SHENYANG_LOCATIONS.items():
        status = DRONE_STATUS[drone_id]
        print(f"\n📍 处理无人机 {drone_id} ({location_info['name']}) - 状态: {status}")
        
        # 创建最近24小时的数据点
        for i in range(24):
            # 每小时一个数据点
            timestamp = current_time - timedelta(hours=i)
            
            # 离线无人机只有较旧的数据
            if status == 'OFFLINE' and i < 12:
                continue
                
            data_point = create_telemetry_data(drone_id, location_info, status, timestamp)
            all_data_points.append(data_point)
    
    print(f"\n📊 总共创建了 {len(all_data_points)} 个遥测数据点")
    
    # 分批写入InfluxDB
    batch_size = 50
    for i in range(0, len(all_data_points), batch_size):
        batch = all_data_points[i:i + batch_size]
        success = write_to_influxdb(batch)
        if not success:
            print("❌ 写入失败，停止处理")
            break
    
    print("\n🎉 沈阳地区无人机遥测数据创建完成！")
    print("\n📋 创建的无人机位置信息:")
    location_names = {
        'SY-DJI-001': '沈阳故宫',
        'SY-DJI-002': '沈阳工业大学', 
        'SY-AUTEL-001': '沈阳北站',
        'SY-YUNEEC-001': '沈阳农业大学',
        'SY-DJI-003': '沈阳奥体中心',
        'SY-DJI-004': '沈阳桃仙机场'
    }
    for drone_id, location_info in SHENYANG_LOCATIONS.items():
        status = DRONE_STATUS[drone_id]
        chinese_name = location_names[drone_id]
        print(f"  • {drone_id}: {chinese_name} ({location_info['lat']:.4f}, {location_info['lng']:.4f}) - {status}")

if __name__ == "__main__":
    main() 