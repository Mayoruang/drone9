#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
已注册无人机模拟器
用于模拟已经注册并获得MQTT凭据的无人机
"""

import json
import time
import uuid
import threading
import signal
import sys
import paho.mqtt.client as mqtt

# 配置
MQTT_HOST = "localhost"
MQTT_PORT = 1883
DRONE_ID = "3b1f02cd-a18d-4729-93b6-6134b116df74"
SERIAL_NUMBER = "SIM-2C25A5D1-A60"
TELEMETRY_TOPIC = "drones/3b1f02cd-a18d-4729-93b6-6134b116df74/telemetry"
COMMANDS_TOPIC = "drones/3b1f02cd-a18d-4729-93b6-6134b116df74/commands"

# 无人机状态
current_latitude = 41.7962
current_longitude = 123.4309
current_altitude = 0.0
current_battery = 95.0
current_status = "IDLE"
current_speed = 0.0
current_heading = 0.0
is_armed = False
target_altitude = None
flying = False

# 控制变量
mqtt_client = None
stop_event = threading.Event()
mqtt_connected = threading.Event()

def signal_handler(sig, frame):
    print('\n正在关闭模拟器...')
    stop_event.set()
    if mqtt_client:
        mqtt_client.disconnect()
    sys.exit(0)

def on_connect(client, userdata, flags, rc):
    print(f'连接到MQTT代理，返回码: {rc}')
    if rc == 0:
        mqtt_connected.set()
        client.subscribe(COMMANDS_TOPIC)
        print(f'订阅命令主题: {COMMANDS_TOPIC}')
    else:
        print(f'连接失败，返回码: {rc}')

def on_disconnect(client, userdata, rc):
    print(f'从MQTT代理断开连接，返回码: {rc}')
    mqtt_connected.clear()

def on_message(client, userdata, msg):
    """处理接收到的命令"""
    global current_status, is_armed, target_altitude, flying, current_altitude, current_speed
    
    try:
        command = json.loads(msg.payload.decode())
        command_type = command.get('type')
        parameters = command.get('parameters', {})
        
        print(f'收到命令: {command_type}, 参数: {parameters}')
        
        if command_type == 'ARM':
            is_armed = True
            current_status = "IDLE"  # 解锁后保持地面待命
            print('✅ 无人机已解锁')
            
        elif command_type == 'DISARM':
            is_armed = False
            current_status = "IDLE"
            flying = False
            print('✅ 无人机已锁定')
            
        elif command_type == 'TAKEOFF':
            if is_armed:
                target_altitude = parameters.get('altitude', 30.0)
                current_status = "FLYING"
                flying = True
                current_speed = 2.0
                print(f'✅ 开始起飞到 {target_altitude}米')
            else:
                print('❌ 无人机未解锁，无法起飞')
                
        elif command_type == 'LAND':
            target_altitude = 0.0
            current_status = "LANDING"
            current_speed = 1.0
            print('✅ 开始降落')
            
        elif command_type == 'RTL':
            target_altitude = 0.0
            current_status = "RETURNING_TO_LAUNCH"
            current_speed = 2.0
            print('✅ 返回起飞点')
            
        elif command_type == 'GOTO':
            if is_armed and flying:
                target_lat = parameters.get('latitude')
                target_lon = parameters.get('longitude')
                target_alt = parameters.get('altitude', current_altitude)
                print(f'✅ 前往位置: ({target_lat}, {target_lon}, {target_alt})')
            else:
                print('❌ 无人机未起飞，无法执行GOTO命令')
                
    except Exception as e:
        print(f'❌ 处理命令时出错: {e}')

def simulate_flight():
    """模拟飞行动作"""
    global current_altitude, current_battery, current_status, flying, current_speed
    
    # 高度控制
    if target_altitude is not None:
        altitude_diff = target_altitude - current_altitude
        if abs(altitude_diff) > 0.1:
            # 每秒改变2米高度
            altitude_change = min(abs(altitude_diff), 2.0)
            if altitude_diff > 0:
                current_altitude += altitude_change
            else:
                current_altitude -= altitude_change
        else:
            current_altitude = target_altitude
            # 只有在到达目标高度时才改变状态
            if target_altitude <= 0:
                current_status = "IDLE"
                current_speed = 0.0
                flying = False
                print('✅ 已着陆')
            elif current_status not in ["LANDING", "RETURNING_TO_LAUNCH"] and target_altitude > 0:
                # 只有在不是降落或返航状态时才设为FLYING
                current_status = "FLYING"
                current_speed = 0.0  # 悬停
    
    # 电池消耗
    if flying or current_status in ["FLYING", "LANDING", "RETURNING_TO_LAUNCH"]:
        current_battery -= 0.1  # 飞行时消耗较快
    else:
        current_battery -= 0.01  # 地面时消耗较慢
    
    current_battery = max(0.0, current_battery)

def publish_telemetry():
    """发布遥测数据"""
    global mqtt_client
    
    while not stop_event.is_set():
        if mqtt_connected.is_set():
            simulate_flight()
            
            payload = {
                "droneId": DRONE_ID,
                "timestamp": time.time(),
                "latitude": round(current_latitude, 6),
                "longitude": round(current_longitude, 6),
                "altitude": round(current_altitude, 2),
                "batteryLevel": round(current_battery, 1),
                "speed": round(current_speed, 2),
                "heading": round(current_heading, 1),
                "status": current_status,
                "isArmed": is_armed,
                "satellites": 8,
                "signalStrength": 85.0,
                "temperature": 20.0
            }
            
            try:
                json_payload = json.dumps(payload)
                result = mqtt_client.publish(TELEMETRY_TOPIC, json_payload)
                print(f'📡 发送遥测: 状态={current_status}, 高度={current_altitude:.1f}m, 电量={current_battery:.1f}%')
            except Exception as e:
                print(f'❌ 发送遥测失败: {e}')
        
        time.sleep(5)  # 每5秒发送一次遥测

def main():
    global mqtt_client
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    print(f'🚁 启动已注册无人机模拟器')
    print(f'📋 序列号: {SERIAL_NUMBER}')
    print(f'🆔 UUID: {DRONE_ID}')
    print(f'📡 遥测主题: {TELEMETRY_TOPIC}')
    print(f'🎮 命令主题: {COMMANDS_TOPIC}')
    
    # 连接MQTT
    client_id = f"sim-drone-{SERIAL_NUMBER}-{str(uuid.uuid4())[:4]}"
    mqtt_client = mqtt.Client(client_id=client_id)
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message
    
    try:
        mqtt_client.connect(MQTT_HOST, MQTT_PORT, 60)
        mqtt_client.loop_start()
        
        # 启动遥测线程
        telemetry_thread = threading.Thread(target=publish_telemetry, daemon=True)
        telemetry_thread.start()
        
        print('✅ 模拟器已启动，等待命令...')
        print('按 Ctrl+C 停止模拟器')
        
        # 主循环
        while not stop_event.is_set():
            time.sleep(1)
            
    except Exception as e:
        print(f'❌ 启动失败: {e}')
    finally:
        if mqtt_client:
            mqtt_client.disconnect()
        print('模拟器已关闭')

if __name__ == "__main__":
    main() 