#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无人机模拟器脚本
专注于无人机状态模拟和MQTT通信
启动后默认处于地面待命状态，等待遥控器命令
"""

import argparse
import json
import random
import time
import uuid
import threading
import signal
import sys
import requests
import paho.mqtt.client as mqtt
import math
from datetime import datetime, timezone

# --- 配置常量 ---
DEFAULT_BACKEND_URL = "http://localhost:8080/api/v1"
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_MODEL = "SimDrone-X2"
DEFAULT_TELEMETRY_INTERVAL = 5  # 秒
DEFAULT_POLL_INTERVAL = 10      # 秒
DEFAULT_INITIAL_LATITUDE = 41.7962   # 沈阳坐标
DEFAULT_INITIAL_LONGITUDE = 123.4309
DEFAULT_INITIAL_ALTITUDE = 0.0
DEFAULT_BATTERY_LEVEL = 100.0
DEFAULT_SPEED = 5.0  # m/s

# 飞行状态常量
STATUS_IDLE = "IDLE"
STATUS_REGISTERING = "REGISTERING"
STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
STATUS_CONNECTING_MQTT = "CONNECTING_MQTT"
STATUS_ONLINE = "ONLINE"
STATUS_FLYING = "FLYING"
STATUS_LANDING = "LANDING"
STATUS_RTL = "RETURNING_TO_LAUNCH"
STATUS_ERROR = "ERROR"
STATUS_TAKEOFF = "TAKEOFF"
STATUS_PATROL = "PATROL"  # 巡航模式
STATUS_HOVERING = "HOVERING"  # 悬停模式

# 轨迹类型常量
TRAJECTORY_RECTANGLE = "RECTANGLE"
TRAJECTORY_CIRCLE = "CIRCLE"
TRAJECTORY_TRIANGLE = "TRIANGLE"
TRAJECTORY_LINE = "LINE"
TRAJECTORY_CUSTOM = "CUSTOM"

# 地球半径（米）
EARTH_RADIUS_METERS = 6371000

# --- 全局变量 ---
drone_id_internal = None
serial_number = None
mqtt_client = None
mqtt_connected = threading.Event()
stop_event = threading.Event()

# 无人机状态
current_latitude = DEFAULT_INITIAL_LATITUDE
current_longitude = DEFAULT_INITIAL_LONGITUDE
current_altitude = DEFAULT_INITIAL_ALTITUDE
current_battery = DEFAULT_BATTERY_LEVEL
current_flight_status = STATUS_IDLE
current_speed = 0.0
current_heading = 0.0
current_temperature = 20.0
current_satellites = 8
current_signal_strength = 85.0

# 目标状态（用于GOTO命令）
target_latitude = None
target_longitude = None
target_altitude = None

# 轨迹相关变量
trajectory_points = []  # 轨迹点列表
current_trajectory_index = 0  # 当前轨迹点索引
trajectory_direction = 1  # 飞行方向：1为正向，-1为反向
trajectory_type = None  # 当前轨迹类型
patrol_active = False  # 巡航是否激活
trajectory_cycles = 0  # 已完成的轨迹循环次数

# 任务状态
is_armed = False  # 无人机是否解锁
home_latitude = DEFAULT_INITIAL_LATITUDE  # 起飞点
home_longitude = DEFAULT_INITIAL_LONGITUDE
home_altitude = DEFAULT_INITIAL_ALTITUDE

def get_serial_number(provided_serial):
    """生成或使用提供的序列号"""
    if provided_serial:
        return provided_serial
    return f"SIM-{str(uuid.uuid4())[:12].upper()}"

def log_info(message):
    """信息日志"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] INFO: {message}")

def log_error(message):
    """错误日志"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] ERROR: {message}")

def log_debug(message):
    """调试日志"""
    if args.verbose:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] DEBUG: {message}")

def log_warn(message):
    """警告日志"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] WARN: {message}")

# --- 轨迹生成函数 ---
def generate_rectangle_trajectory(center_lat, center_lon, width, height, altitude):
    """生成矩形轨迹"""
    # 将米转换为经纬度偏移
    lat_offset = height / (2 * EARTH_RADIUS_METERS) * 180 / math.pi
    lon_offset = width / (2 * EARTH_RADIUS_METERS * math.cos(math.radians(center_lat))) * 180 / math.pi
    
    points = [
        (center_lat + lat_offset, center_lon - lon_offset, altitude),  # 右上
        (center_lat + lat_offset, center_lon + lon_offset, altitude),  # 左上
        (center_lat - lat_offset, center_lon + lon_offset, altitude),  # 左下
        (center_lat - lat_offset, center_lon - lon_offset, altitude),  # 右下
    ]
    
    log_info(f"生成矩形轨迹: 中心({center_lat:.6f}, {center_lon:.6f}), 尺寸{width}x{height}米")
    return points

def generate_circle_trajectory(center_lat, center_lon, radius, altitude, num_points=8):
    """生成圆形轨迹"""
    points = []
    for i in range(num_points):
        angle = 2 * math.pi * i / num_points
        # 计算相对于中心点的经纬度偏移
        lat_offset = radius * math.cos(angle) / EARTH_RADIUS_METERS * 180 / math.pi
        lon_offset = radius * math.sin(angle) / (EARTH_RADIUS_METERS * math.cos(math.radians(center_lat))) * 180 / math.pi
        
        lat = center_lat + lat_offset
        lon = center_lon + lon_offset
        points.append((lat, lon, altitude))
    
    log_info(f"生成圆形轨迹: 中心({center_lat:.6f}, {center_lon:.6f}), 半径{radius}米, {num_points}个点")
    return points

def generate_triangle_trajectory(center_lat, center_lon, size, altitude):
    """生成三角形轨迹"""
    # 等边三角形
    height = size * math.sqrt(3) / 2
    lat_offset_top = height * 2/3 / EARTH_RADIUS_METERS * 180 / math.pi
    lat_offset_bottom = height * 1/3 / EARTH_RADIUS_METERS * 180 / math.pi
    lon_offset = size / 2 / (EARTH_RADIUS_METERS * math.cos(math.radians(center_lat))) * 180 / math.pi
    
    points = [
        (center_lat + lat_offset_top, center_lon, altitude),              # 顶点
        (center_lat - lat_offset_bottom, center_lon - lon_offset, altitude),  # 左下
        (center_lat - lat_offset_bottom, center_lon + lon_offset, altitude),  # 右下
    ]
    
    log_info(f"生成三角形轨迹: 中心({center_lat:.6f}, {center_lon:.6f}), 边长{size}米")
    return points

def generate_line_trajectory(start_lat, start_lon, end_lat, end_lon, altitude, num_points=4):
    """生成直线往复轨迹"""
    points = []
    for i in range(num_points + 1):
        t = i / num_points
        lat = start_lat + t * (end_lat - start_lat)
        lon = start_lon + t * (end_lon - start_lon)
        points.append((lat, lon, altitude))
    
    log_info(f"生成直线轨迹: 从({start_lat:.6f}, {start_lon:.6f})到({end_lat:.6f}, {end_lon:.6f}), {num_points+1}个点")
    return points

def set_trajectory(traj_type, **kwargs):
    """设置轨迹"""
    global trajectory_points, current_trajectory_index, trajectory_direction, trajectory_type
    global target_latitude, target_longitude, target_altitude
    
    center_lat = kwargs.get('center_lat', current_latitude)
    center_lon = kwargs.get('center_lon', current_longitude)
    altitude = kwargs.get('altitude', current_altitude + 20)
    size = kwargs.get('size', 100)
    
    if traj_type == TRAJECTORY_RECTANGLE:
        width = kwargs.get('width', size)
        height = kwargs.get('height', size)
        trajectory_points = generate_rectangle_trajectory(center_lat, center_lon, width, height, altitude)
    elif traj_type == TRAJECTORY_CIRCLE:
        radius = kwargs.get('radius', size/2)
        num_points = kwargs.get('num_points', 8)
        trajectory_points = generate_circle_trajectory(center_lat, center_lon, radius, altitude, num_points)
    elif traj_type == TRAJECTORY_TRIANGLE:
        trajectory_points = generate_triangle_trajectory(center_lat, center_lon, size, altitude)
    elif traj_type == TRAJECTORY_LINE:
        end_lat = kwargs.get('end_lat', center_lat + 0.001)
        end_lon = kwargs.get('end_lon', center_lon + 0.001)
        num_points = kwargs.get('num_points', 4)
        trajectory_points = generate_line_trajectory(center_lat, center_lon, end_lat, end_lon, altitude, num_points)
    elif traj_type == TRAJECTORY_CUSTOM:
        trajectory_points = kwargs.get('points', [])
    
    trajectory_type = traj_type
    current_trajectory_index = 0
    trajectory_direction = 1
    
    if trajectory_points:
        target_latitude, target_longitude, target_altitude = trajectory_points[0]
        log_info(f"设置{traj_type}轨迹，共{len(trajectory_points)}个点")
        for i, (lat, lon, alt) in enumerate(trajectory_points):
            log_debug(f"  轨迹点{i}: ({lat:.6f}, {lon:.6f}, {alt:.1f})")
    else:
        log_error("轨迹点生成失败")

def get_next_trajectory_point():
    """获取下一个轨迹点"""
    global current_trajectory_index, trajectory_direction, trajectory_cycles
    
    if not trajectory_points:
        return None
    
    current_trajectory_index += trajectory_direction
    
    # 检查轨迹边界，实现往复飞行
    if current_trajectory_index >= len(trajectory_points):
        current_trajectory_index = len(trajectory_points) - 2
        trajectory_direction = -1
        trajectory_cycles += 0.5
        log_info(f"到达轨迹末端，开始反向飞行 (第{trajectory_cycles:.1f}轮)")
    elif current_trajectory_index < 0:
        current_trajectory_index = 1
        trajectory_direction = 1
        trajectory_cycles += 0.5
        log_info(f"到达轨迹起点，开始正向飞行 (第{trajectory_cycles:.1f}轮)")
    
    if 0 <= current_trajectory_index < len(trajectory_points):
        return trajectory_points[current_trajectory_index]
    return None

# --- API交互函数 ---
def register_drone(serial_number, model, backend_url):
    """注册无人机"""
    log_info(f"尝试注册无人机 - 序列号: {serial_number}, 型号: {model}")
    payload = {
        "serialNumber": serial_number,
        "model": model,
        "notes": f"Python模拟器生成的无人机 - {datetime.now().isoformat()}"
    }
    try:
        response = requests.post(f"{backend_url}/drones/register", json=payload, timeout=10)
        response.raise_for_status()
        data = response.json()
        log_info(f"注册请求已提交，请求ID: {data.get('requestId')}")
        return data.get("requestId")
    except requests.exceptions.RequestException as e:
        log_error(f"注册失败: {e}")
        return None

def check_registration_status(request_id, backend_url):
    """检查注册状态"""
    try:
        response = requests.get(f"{backend_url}/drones/registration/{request_id}/status", timeout=10)
        response.raise_for_status()
        data = response.json()
        log_debug(f"注册状态查询响应: {data}")
        return data
    except requests.exceptions.RequestException as e:
        log_error(f"查询注册状态失败: {e}")
        return None

# --- MQTT回调函数 ---
def on_connect(client, userdata, flags, rc, properties=None):
    """MQTT连接回调"""
    global current_flight_status, drone_id_internal
    if rc == 0:
        log_info(f"成功连接到MQTT代理，无人机ID: {drone_id_internal}")
        mqtt_connected.set()
        command_topic = userdata.get("command_topic")
        if command_topic:
            client.subscribe(command_topic, qos=1)
            log_info(f"已订阅命令主题: {command_topic}")
        else:
            log_error("在MQTT用户数据中未找到命令主题")
        current_flight_status = STATUS_ONLINE
    else:
        log_error(f"MQTT连接失败，错误代码: {rc}")
        current_flight_status = STATUS_ERROR
        mqtt_connected.clear()

def on_disconnect(client, userdata, rc, properties=None):
    """MQTT断开连接回调"""
    global current_flight_status
    log_info(f"从MQTT代理断开连接 (rc: {rc})")
    mqtt_connected.clear()
    current_flight_status = STATUS_IDLE

def on_message(client, userdata, msg):
    """接收MQTT消息回调"""
    global current_flight_status, target_latitude, target_longitude, target_altitude, current_speed
    global patrol_active, is_armed
    
    payload_str = msg.payload.decode()
    log_info(f"收到遥控器命令，主题 '{msg.topic}': {payload_str}")
    try:
        command_data = json.loads(payload_str)
        command_id = command_data.get("commandId", "unknown")
        action = command_data.get("type")
        parameters = command_data.get("parameters", {})

        log_info(f"处理命令: {action}, ID: {command_id}")

        if action == "ARM":
            is_armed = True
            log_info("✅ 无人机已解锁，准备飞行")
            send_command_ack(command_id, "SUCCESS", "无人机已解锁")
            
        elif action == "DISARM":
            is_armed = False
            patrol_active = False
            current_flight_status = STATUS_ONLINE
            current_speed = 0
            log_info("🔒 无人机已锁定")
            send_command_ack(command_id, "SUCCESS", "无人机已锁定")
            
        elif action == "TAKEOFF":
            if not is_armed:
                send_command_ack(command_id, "FAILED", "无人机未解锁，无法起飞")
                return
            
            patrol_active = False
            target_altitude = parameters.get("altitude", current_altitude + 20)
            target_latitude = current_latitude  # 垂直起飞
            target_longitude = current_longitude
            current_flight_status = STATUS_TAKEOFF
            current_speed = 2.0
            log_info(f"🚁 执行起飞命令，目标高度: {target_altitude}米")
            send_command_ack(command_id, "SUCCESS", f"开始起飞到{target_altitude}米")
            
        elif action == "GOTO":
            if not is_armed:
                send_command_ack(command_id, "FAILED", "无人机未解锁，无法飞行")
                return
                
            patrol_active = False  # 停止巡航模式
            target_latitude = parameters.get("latitude", current_latitude)
            target_longitude = parameters.get("longitude", current_longitude) 
            target_altitude = parameters.get("altitude", current_altitude)
            current_speed = parameters.get("speed", DEFAULT_SPEED)
            current_flight_status = STATUS_FLYING
            log_info(f"🎯 执行GOTO命令: Lat={target_latitude}, Lon={target_longitude}, Alt={target_altitude}")
            send_command_ack(command_id, "SUCCESS", "开始执行GOTO命令")
            
        elif action == "PATROL":
            if not is_armed:
                send_command_ack(command_id, "FAILED", "无人机未解锁，无法巡航")
                return
                
            # 巡航命令
            traj_type = parameters.get("trajectoryType", TRAJECTORY_RECTANGLE)
            size = parameters.get("size", 100)
            altitude = parameters.get("altitude", max(current_altitude, 20))
            
            set_trajectory(traj_type, 
                         center_lat=current_latitude,
                         center_lon=current_longitude,
                         size=size,
                         altitude=altitude,
                         **parameters)
            
            if trajectory_points:
                patrol_active = True
                current_flight_status = STATUS_PATROL
                current_speed = parameters.get("speed", DEFAULT_SPEED)
                target_latitude, target_longitude, target_altitude = trajectory_points[0]
                log_info(f"🔄 开始{traj_type}轨迹巡航，共{len(trajectory_points)}个点")
                send_command_ack(command_id, "SUCCESS", f"开始{traj_type}轨迹巡航")
            else:
                send_command_ack(command_id, "FAILED", "轨迹生成失败")
            
        elif action == "STOP_PATROL":
            # 停止巡航，保持悬停
            patrol_active = False
            current_flight_status = STATUS_HOVERING
            current_speed = 0
            log_info("⏸️ 停止巡航模式，保持悬停")
            send_command_ack(command_id, "SUCCESS", "已停止巡航模式")
            
        elif action == "LAND":
            patrol_active = False
            current_flight_status = STATUS_LANDING
            target_latitude = current_latitude  # 原地降落
            target_longitude = current_longitude
            target_altitude = 0
            current_speed = 1.0
            log_info("🛬 执行降落命令")
            send_command_ack(command_id, "SUCCESS", "开始降落")
            
        elif action == "RTL":
            patrol_active = False
            current_flight_status = STATUS_RTL
            target_latitude = home_latitude
            target_longitude = home_longitude
            target_altitude = home_altitude
            current_speed = DEFAULT_SPEED * 0.8
            log_info("🏠 执行返航命令")
            send_command_ack(command_id, "SUCCESS", "开始返航")
            
        else:
            log_warn(f"❓ 未知命令类型: {action}")
            send_command_ack(command_id, "FAILED", f"不支持的命令类型: {action}")

    except json.JSONDecodeError:
        log_error(f"解析命令JSON失败: {payload_str}")
    except Exception as e:
        log_error(f"处理命令时出错: {e}")

def send_command_ack(command_id, status, message):
    """发送命令确认"""
    global mqtt_client, drone_id_internal
    if mqtt_client and mqtt_connected.is_set():
        ack_payload = {
            "commandId": command_id,
            "droneId": drone_id_internal,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "status": status,
            "message": message
        }
        ack_topic = f"drones/{drone_id_internal}/responses"
        try:
            result = mqtt_client.publish(ack_topic, json.dumps(ack_payload), qos=1)
            result.wait_for_publish(timeout=2)
            log_debug(f"发送命令确认到 {ack_topic}: {ack_payload}")
        except Exception as e:
            log_error(f"发送命令确认失败: {e}")

def connect_mqtt(host, port, username, password, command_topic):
    """连接MQTT"""
    global mqtt_client
    client_id = f"sim-drone-{serial_number}-{str(uuid.uuid4())[:4]}"
    
    # 创建MQTT客户端（兼容不同版本）
    try:
        mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    except AttributeError:
        # 兼容老版本的paho-mqtt
        mqtt_client = mqtt.Client(client_id=client_id)
    
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message

    mqtt_client.user_data_set({"command_topic": command_topic})

    if username and password:
        mqtt_client.username_pw_set(username, password)

    try:
        log_info(f"尝试连接MQTT: {host}:{port}，客户端ID: {client_id}")
        mqtt_client.connect(host, port, keepalive=60)
        mqtt_client.loop_start()
        return True
    except Exception as e:
        log_error(f"MQTT连接失败: {e}")
        return False

# --- 飞行模拟函数 ---
def calculate_distance(lat1, lon1, lat2, lon2):
    """计算两点间距离（米）"""
    if lat1 is None or lon1 is None or lat2 is None or lon2 is None:
        return 0
    
    lat1_rad = math.radians(lat1)
    lon1_rad = math.radians(lon1)
    lat2_rad = math.radians(lat2)
    lon2_rad = math.radians(lon2)
    
    dlat = lat2_rad - lat1_rad
    dlon = lon2_rad - lon1_rad
    
    a = math.sin(dlat/2)**2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon/2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    
    return EARTH_RADIUS_METERS * c

def calculate_bearing(lat1, lon1, lat2, lon2):
    """计算方向角（度）"""
    if lat1 is None or lon1 is None or lat2 is None or lon2 is None:
        return 0
    
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    dlon_rad = math.radians(lon2 - lon1)
    
    x = math.sin(dlon_rad) * math.cos(lat2_rad)
    y = math.cos(lat1_rad) * math.sin(lat2_rad) - math.sin(lat1_rad) * math.cos(lat2_rad) * math.cos(dlon_rad)
    
    bearing_rad = math.atan2(x, y)
    bearing_deg = math.degrees(bearing_rad)
    
    return (bearing_deg + 360) % 360

def move_towards_target(distance_to_move):
    """朝目标移动"""
    global current_latitude, current_longitude, current_altitude, current_heading
    
    if target_latitude is None or target_longitude is None:
        return
    
    distance_to_target = calculate_distance(current_latitude, current_longitude, target_latitude, target_longitude)
    
    if distance_to_target <= distance_to_move:
        # 到达目标
        current_latitude = target_latitude
        current_longitude = target_longitude
        return True
    
    # 计算移动方向
    bearing = calculate_bearing(current_latitude, current_longitude, target_latitude, target_longitude)
    current_heading = bearing
    
    # 计算新位置
    bearing_rad = math.radians(bearing)
    lat_rad = math.radians(current_latitude)
    
    new_lat_rad = math.asin(
        math.sin(lat_rad) * math.cos(distance_to_move / EARTH_RADIUS_METERS) +
        math.cos(lat_rad) * math.sin(distance_to_move / EARTH_RADIUS_METERS) * math.cos(bearing_rad)
    )
    
    new_lon_rad = math.radians(current_longitude) + math.atan2(
        math.sin(bearing_rad) * math.sin(distance_to_move / EARTH_RADIUS_METERS) * math.cos(lat_rad),
        math.cos(distance_to_move / EARTH_RADIUS_METERS) - math.sin(lat_rad) * math.sin(new_lat_rad)
    )
    
    current_latitude = math.degrees(new_lat_rad)
    current_longitude = math.degrees(new_lon_rad)
    return False

def simulate_movement_and_battery():
    """模拟移动和电池消耗"""
    global current_altitude, current_battery, current_flight_status, current_speed
    global current_temperature, current_satellites, current_signal_strength
    global target_latitude, target_longitude, target_altitude, patrol_active
    
    # 根据飞行状态更新位置和参数
    if current_flight_status == STATUS_PATROL and patrol_active:
        # 巡航模式：沿轨迹飞行
        if target_latitude is not None and target_longitude is not None:
            distance_to_move = current_speed * args.telemetry_interval
            reached_target = move_towards_target(distance_to_move)
            
            if reached_target:
                # 到达当前轨迹点，获取下一个点
                next_point = get_next_trajectory_point()
                if next_point:
                    target_latitude, target_longitude, target_altitude = next_point
                    log_debug(f"巡航前往下一个轨迹点: ({target_latitude:.6f}, {target_longitude:.6f}, {target_altitude:.1f})")
                else:
                    log_error("无法获取下一个轨迹点")
        
        # 高度调整
        if target_altitude is not None:
            altitude_diff = target_altitude - current_altitude
            if abs(altitude_diff) > 0.5:
                altitude_change = min(abs(altitude_diff), 2.0 * args.telemetry_interval)
                current_altitude += altitude_change if altitude_diff > 0 else -altitude_change
            else:
                current_altitude = target_altitude
        
        # 巡航时电池消耗
        current_battery -= random.uniform(0.15, 0.25)
        current_temperature += random.uniform(-0.5, 1.0)
        
    elif current_flight_status == STATUS_FLYING:
        if target_latitude is not None and target_longitude is not None:
            distance_to_move = current_speed * args.telemetry_interval
            reached_target = move_towards_target(distance_to_move)
            
            if reached_target:
                log_info("✅ 已到达目标位置，进入悬停模式")
                current_flight_status = STATUS_HOVERING
                current_speed = 0
        
        # 高度调整
        if target_altitude is not None:
            altitude_diff = target_altitude - current_altitude
            if abs(altitude_diff) > 0.5:
                altitude_change = min(abs(altitude_diff), 2.0 * args.telemetry_interval)
                current_altitude += altitude_change if altitude_diff > 0 else -altitude_change
            else:
                current_altitude = target_altitude
        
        # 飞行时电池消耗
        current_battery -= random.uniform(0.1, 0.3)
        current_temperature += random.uniform(-0.5, 1.0)
        
    elif current_flight_status == STATUS_TAKEOFF:
        if target_altitude is not None:
            altitude_change = 3.0 * args.telemetry_interval  # 起飞速度
            current_altitude += altitude_change
            if current_altitude >= target_altitude:
                current_altitude = target_altitude
                current_flight_status = STATUS_HOVERING
                current_speed = 0
                log_info(f"✅ 起飞完成，当前高度: {current_altitude}米，进入悬停模式")
        current_battery -= random.uniform(0.2, 0.4)
        
    elif current_flight_status == STATUS_LANDING:
        if current_altitude > 0:
            altitude_change = 2.0 * args.telemetry_interval  # 降落速度
            current_altitude = max(0, current_altitude - altitude_change)
            if current_altitude <= 0:
                current_altitude = 0
                current_flight_status = STATUS_ONLINE
                current_speed = 0
                is_armed = False  # 降落后自动锁定
                log_info("✅ 降落完成，无人机已锁定")
        current_battery -= random.uniform(0.1, 0.2)
        
    elif current_flight_status == STATUS_RTL:
        distance_to_move = current_speed * args.telemetry_interval
        reached_target = move_towards_target(distance_to_move)
        
        if reached_target:
            log_info("✅ 返航完成，开始降落")
            current_flight_status = STATUS_LANDING
        current_battery -= random.uniform(0.1, 0.2)
        
    elif current_flight_status == STATUS_HOVERING:
        # 悬停模式，少量电池消耗
        current_battery -= random.uniform(0.05, 0.15)
        current_temperature += random.uniform(-0.2, 0.2)
        
    else:
        # 地面状态，电池消耗很少
        current_battery -= random.uniform(0.01, 0.05)
    
    # 确保电池电量不低于0
    current_battery = max(0, current_battery)
    
    # 模拟环境参数变化
    current_temperature += random.uniform(-0.2, 0.2)
    current_temperature = max(-10, min(50, current_temperature))
    
    current_satellites = max(0, min(20, current_satellites + random.randint(-1, 1)))
    current_signal_strength += random.uniform(-2, 2)
    current_signal_strength = max(0, min(100, current_signal_strength))

def get_flight_mode_display():
    """获取飞行模式显示字符串"""
    if current_flight_status == STATUS_PATROL and patrol_active:
        return f"PATROL_{trajectory_type}" if trajectory_type else "PATROL"
    elif current_flight_status == STATUS_FLYING:
        return "FLYING"
    elif current_flight_status == STATUS_TAKEOFF:
        return "TAKEOFF"
    elif current_flight_status == STATUS_LANDING:
        return "LANDING"
    elif current_flight_status == STATUS_RTL:
        return "RTL"
    elif current_flight_status == STATUS_HOVERING:
        return "HOVERING"
    elif current_flight_status == STATUS_ONLINE:
        if is_armed:
            return "ARMED"
        else:
            return "IDLE"
    else:
        return current_flight_status

def publish_telemetry_periodically(telemetry_topic):
    """定期发布遥测数据"""
    global mqtt_client
    while not stop_event.is_set():
        if mqtt_connected.is_set() and mqtt_client:
            simulate_movement_and_battery()
            
            # 构建飞行模式显示
            flight_mode = get_flight_mode_display()
            
            payload = {
                "droneId": drone_id_internal,
                "timestamp": time.time(),
                "latitude": round(current_latitude, 6),
                "longitude": round(current_longitude, 6),
                "altitude": round(current_altitude, 2),
                "batteryLevel": round(current_battery, 1),
                "batteryVoltage": round(current_battery * 0.12 + 10, 2),
                "speed": round(current_speed, 2),
                "heading": round(current_heading, 1),
                "satellites": current_satellites,
                "signalStrength": round(current_signal_strength, 1),
                "flightMode": flight_mode,
                "temperature": round(current_temperature, 1),
                "status": current_flight_status,
                "isArmed": is_armed,
                # 添加轨迹相关信息
                "trajectoryInfo": {
                    "type": trajectory_type,
                    "active": patrol_active,
                    "currentIndex": current_trajectory_index if trajectory_points else None,
                    "totalPoints": len(trajectory_points),
                    "direction": trajectory_direction,
                    "cycles": trajectory_cycles
                } if trajectory_points else None
            }
            
            try:
                json_payload = json.dumps(payload)
                result = mqtt_client.publish(telemetry_topic, json_payload, qos=0)
                log_debug(f"📡 发布遥测数据: {flight_mode}, 高度={current_altitude:.1f}m, 电池={current_battery:.1f}%")
            except Exception as e:
                log_error(f"发布遥测数据时出错: {e}")
        
        time.sleep(args.telemetry_interval)

def send_farewell_message(telemetry_topic):
    """发送告别消息"""
    global mqtt_client, drone_id_internal
    if mqtt_client and mqtt_connected.is_set():
        farewell_payload = {
            "type": "FAREWELL",
            "droneId": drone_id_internal,
            "timestamp": time.time(),
            "message": "无人机模拟器正在关闭",
            "reason": "用户停止"
        }
        try:
            result = mqtt_client.publish(telemetry_topic, json.dumps(farewell_payload), qos=1)
            result.wait_for_publish(timeout=3)
            log_info("已发送告别消息")
        except Exception as e:
            log_error(f"发送告别消息失败: {e}")

def signal_handler(signum, frame):
    """信号处理函数"""
    log_info("收到终止信号，正在关闭...")
    stop_event.set()

def main():
    global serial_number, drone_id_internal, args, home_latitude, home_longitude
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description="无人机模拟器 - 地面待命状态，等待遥控器命令")
    parser.add_argument("--serial", help="无人机序列号")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="无人机型号")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL, help="后端API地址")
    parser.add_argument("--mqtt-host", default=DEFAULT_MQTT_HOST, help="MQTT代理地址")
    parser.add_argument("--mqtt-port", type=int, default=DEFAULT_MQTT_PORT, help="MQTT端口")
    parser.add_argument("--telemetry-interval", type=int, default=DEFAULT_TELEMETRY_INTERVAL, help="遥测数据发送间隔（秒）")
    parser.add_argument("--poll-interval", type=int, default=DEFAULT_POLL_INTERVAL, help="注册状态查询间隔（秒）")
    parser.add_argument("--verbose", "-v", action="store_true", help="详细输出")
    
    args = parser.parse_args()
    
    # 设置信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    serial_number = get_serial_number(args.serial)
    home_latitude = current_latitude
    home_longitude = current_longitude
    
    log_info(f"🚁 启动无人机模拟器")
    log_info(f"📋 序列号: {serial_number}")
    log_info(f"🔧 型号: {args.model}")
    log_info(f"🌐 后端地址: {args.backend_url}")
    log_info(f"📡 MQTT地址: {args.mqtt_host}:{args.mqtt_port}")
    log_info(f"📍 起飞点: ({current_latitude:.6f}, {current_longitude:.6f})")
    
    # 第一步：注册无人机
    log_info("🔑 第一步：注册无人机")
    request_id = register_drone(serial_number, args.model, args.backend_url)
    if not request_id:
        log_error("❌ 注册失败，退出程序")
        return
    
    # 第二步：轮询注册状态
    log_info("⏳ 第二步：等待注册审批")
    mqtt_credentials = None
    poll_count = 0
    max_polls = 60  # 最多轮询60次（10分钟）
    
    while poll_count < max_polls and not stop_event.is_set():
        status_data = check_registration_status(request_id, args.backend_url)
        if status_data:
            status = status_data.get("status")
            log_info(f"📋 注册状态: {status}")
            
            if status == "APPROVED":
                drone_id_internal = status_data.get("droneId")
                mqtt_credentials = status_data.get("mqttCredentials")
                log_info(f"✅ 注册已批准！无人机ID: {drone_id_internal}")
                break
            elif status == "REJECTED":
                message = status_data.get("message", "未知原因")
                log_error(f"❌ 注册被拒绝: {message}")
                return
            elif status == "PENDING_APPROVAL":
                log_info("⏳ 等待管理员审批...")
            else:
                log_warn(f"❓ 未知状态: {status}")
        
        poll_count += 1
        time.sleep(args.poll_interval)
    
    if not mqtt_credentials or not drone_id_internal:
        log_error("❌ 注册超时或失败，退出程序")
        return
    
    # 第三步：连接MQTT并进入地面待命状态
    log_info("📡 第三步：连接MQTT并进入地面待命状态")
    mqtt_host = mqtt_credentials.get("mqttBrokerUrl", "").replace("tcp://", "").split(":")[0] or args.mqtt_host
    mqtt_port = int(mqtt_credentials.get("mqttBrokerUrl", "").split(":")[-1]) if ":" in mqtt_credentials.get("mqttBrokerUrl", "") else args.mqtt_port
    mqtt_username = mqtt_credentials.get("mqttUsername")
    mqtt_password = mqtt_credentials.get("mqttPassword")
    telemetry_topic = mqtt_credentials.get("mqttTopicTelemetry")
    command_topic = mqtt_credentials.get("mqttTopicCommands")
    
    log_info(f"🔧 MQTT配置 - 用户名: {mqtt_username}")
    log_info(f"📡 遥测主题: {telemetry_topic}")
    log_info(f"🎮 命令主题: {command_topic}")
    
    if connect_mqtt(mqtt_host, mqtt_port, mqtt_username, mqtt_password, command_topic):
        # 等待MQTT连接
        if mqtt_connected.wait(timeout=10):
            log_info("🟢 无人机已上线，等待遥控器命令...")
            log_info("🎮 可使用 mqtt_commands.py 工具控制无人机")
            
            # 启动遥测数据发送线程
            telemetry_thread = threading.Thread(target=publish_telemetry_periodically, args=(telemetry_topic,))
            telemetry_thread.start()
            
            try:
                # 主循环：保持程序运行
                while not stop_event.is_set():
                    time.sleep(1)
            except KeyboardInterrupt:
                log_info("收到键盘中断信号")
            
            # 清理
            log_info("🔄 正在关闭...")
            stop_event.set()
            
            # 发送告别消息
            send_farewell_message(telemetry_topic)
            
            # 等待线程结束
            telemetry_thread.join(timeout=5)
            
            # 断开MQTT连接
            if mqtt_client:
                mqtt_client.disconnect()
                mqtt_client.loop_stop()
            
            log_info("👋 无人机模拟器已关闭")
        else:
            log_error("❌ MQTT连接超时")
    else:
        log_error("❌ MQTT连接失败")

if __name__ == "__main__":
    main()