#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
已注册无人机模拟器
用于模拟已经注册并获得MQTT凭据的无人机
支持通过命令行参数指定要模拟的无人机ID
"""

import json
import time
import uuid
import threading
import signal
import sys
import math
import argparse
import requests
import random
import paho.mqtt.client as mqtt

class DroneSimulator:
    def __init__(self, drone_id, backend_url="http://localhost:8080"):
        self.drone_id = drone_id
        self.backend_url = backend_url
        self.mqtt_host = "localhost"
        self.mqtt_port = 1883
        
        # 从后端获取无人机信息
        self.drone_info = self.fetch_drone_info()
        
        # MQTT主题
        self.telemetry_topic = f"drones/{self.drone_id}/telemetry"
        self.commands_topic = f"drones/{self.drone_id}/commands"
        self.responses_topic = f"drones/{self.drone_id}/responses"
        
        # 生成沈阳市内随机初始位置
        initial_lat, initial_lon = self.generate_random_shenyang_position()
        
        # 无人机状态
        self.current_latitude = initial_lat
        self.current_longitude = initial_lon
        self.home_latitude = initial_lat  # 记录起飞点
        self.home_longitude = initial_lon  # 记录起飞点
        self.current_altitude = 0.0
        self.current_battery = 95.0
        self.current_status = "IDLE"
        self.current_speed = 0.0
        self.current_heading = 0.0
        self.is_armed = False
        self.target_altitude = None
        self.target_latitude = None
        self.target_longitude = None
        self.flying = False
        
        # 控制变量
        self.mqtt_client = None
        self.stop_event = threading.Event()
        self.mqtt_connected = threading.Event()
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
        
        print(f"📍 无人机初始位置: ({self.current_latitude:.6f}, {self.current_longitude:.6f})")

    def generate_random_shenyang_position(self):
        """
        在沈阳市区内随机生成初始位置
        沈阳市大致坐标范围:
        - 纬度: 41.6° - 42.1°N
        - 经度: 123.0° - 123.8°E
        
        为了更真实，我们选择市区核心区域:
        - 纬度: 41.75° - 41.85°N  
        - 经度: 123.35° - 123.55°E
        """
        # 沈阳市核心区域范围
        min_lat = 41.75
        max_lat = 41.85
        min_lon = 123.35
        max_lon = 123.55
        
        # 生成随机坐标
        random_lat = random.uniform(min_lat, max_lat)
        random_lon = random.uniform(min_lon, max_lon)
        
        return round(random_lat, 6), round(random_lon, 6)

    def fetch_drone_info(self):
        """从后端获取无人机信息"""
        try:
            url = f"{self.backend_url}/api/v1/drones/{self.drone_id}"
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                drone_info = response.json()
                print(f"✅ 成功获取无人机信息:")
                print(f"   - 序列号: {drone_info.get('serialNumber', 'Unknown')}")
                print(f"   - 型号: {drone_info.get('model', 'Unknown')}")
                print(f"   - 状态: {drone_info.get('currentStatus', 'Unknown')}")
                return drone_info
            else:
                print(f"⚠️  无法获取无人机信息 (HTTP {response.status_code})，使用默认配置")
                return {"serialNumber": f"SIM-{self.drone_id[:8]}", "model": "Simulator"}
                
        except Exception as e:
            print(f"⚠️  获取无人机信息失败: {e}，使用默认配置")
            return {"serialNumber": f"SIM-{self.drone_id[:8]}", "model": "Simulator"}

    def signal_handler(self, sig, frame):
        print('\n正在关闭模拟器...')
        self.stop_event.set()
        if self.mqtt_client:
            self.mqtt_client.disconnect()
        sys.exit(0)

    def on_connect(self, client, userdata, flags, rc):
        print(f'连接到MQTT代理，返回码: {rc}')
        if rc == 0:
            self.mqtt_connected.set()
            client.subscribe(self.commands_topic)
            print(f'订阅命令主题: {self.commands_topic}')
        else:
            print(f'连接失败，返回码: {rc}')

    def on_disconnect(self, client, userdata, rc):
        print(f'从MQTT代理断开连接，返回码: {rc}')
        self.mqtt_connected.clear()

    def on_message(self, client, userdata, msg):
        """处理接收到的命令"""
        try:
            command = json.loads(msg.payload.decode())
            command_type = command.get('type')
            command_id = command.get('commandId', 'unknown')
            parameters = command.get('parameters', {})
            
            print(f'收到命令: {command_type}, ID: {command_id}, 参数: {parameters}')
            
            # 发送命令确认函数
            def send_command_ack(status, message):
                """发送命令确认"""
                ack_payload = {
                    "commandId": command_id,
                    "droneId": self.drone_id,
                    "timestamp": time.time(),
                    "status": status,
                    "message": message
                }
                try:
                    result = client.publish(self.responses_topic, json.dumps(ack_payload), qos=1)
                    result.wait_for_publish(timeout=2)
                    print(f'📤 发送命令确认: {status} - {message}')
                except Exception as e:
                    print(f'❌ 发送命令确认失败: {e}')
            
            # 处理各种命令
            self.process_command(command_type, parameters, send_command_ack)
            
        except json.JSONDecodeError as e:
            print(f'❌ 解析命令JSON失败: {e}')
        except Exception as e:
            print(f'❌ 处理命令时出错: {e}')

    def is_on_ground(self):
        """检查无人机是否在地面"""
        return self.current_altitude <= 0.5  # 0.5米以下视为地面
    
    def is_airborne(self):
        """检查无人机是否在空中"""
        return self.current_altitude > 0.5
    
    def can_execute_flight_command(self):
        """检查是否可以执行飞行命令（需要在空中且解锁）"""
        return self.is_armed and self.is_airborne()
    
    def can_takeoff(self):
        """检查是否可以起飞"""
        return self.is_armed and self.is_on_ground() and self.current_status == "IDLE"
    
    def can_land(self):
        """检查是否可以降落"""
        return self.is_armed and self.is_airborne() and self.current_status in ["FLYING", "HOVER", "GOTO"]
    
    def can_goto(self):
        """检查是否可以执行GOTO命令"""
        return self.is_armed and self.is_airborne() and self.current_status in ["FLYING", "HOVER", "GOTO"]
    
    def can_hover(self):
        """检查是否可以悬停"""
        return self.is_armed and self.is_airborne() and self.current_status in ["FLYING", "GOTO"]
    
    def can_set_speed(self):
        """检查是否可以设置速度"""
        return self.is_armed and self.is_airborne() and self.current_status in ["FLYING", "GOTO", "RETURNING_TO_LAUNCH"]
    
    def can_set_altitude(self):
        """检查是否可以设置高度"""
        return self.is_armed and self.current_status in ["FLYING", "HOVER", "GOTO"]

    def process_command(self, command_type, parameters, send_ack):
        """处理具体的命令逻辑 - 重新设计的严格状态机"""
        print(f'🎮 处理命令: {command_type}, 当前状态: {self.current_status}, 高度: {self.current_altitude:.2f}m, 解锁: {self.is_armed}')
        
        if command_type == 'ARM':
            if not self.is_armed:
                self.is_armed = True
                self.current_status = "IDLE"
                print('✅ 无人机已解锁')
                send_ack("SUCCESS", "无人机已解锁")
            else:
                print('⚠️ 无人机已经处于解锁状态')
                send_ack("SUCCESS", "无人机已经处于解锁状态")
            
        elif command_type == 'DISARM':
            if self.is_airborne():
                print('❌ 无人机在空中，无法锁定')
                send_ack("FAILED", "无人机在空中，无法锁定，请先降落")
            else:
                self.is_armed = False
                self.current_status = "IDLE"
                self.flying = False
                self.target_altitude = None
                self.target_latitude = None
                self.target_longitude = None
                self.current_speed = 0.0
                print('✅ 无人机已锁定')
                send_ack("SUCCESS", "无人机已锁定")
            
        elif command_type == 'TAKEOFF':
            if self.can_takeoff():
                self.target_altitude = float(parameters.get('altitude', 30.0))
                self.target_altitude = max(5.0, min(500.0, self.target_altitude))  # 限制起飞高度
                self.current_status = "FLYING"
                self.flying = True
                self.current_speed = 2.0
                print(f'✅ 开始起飞到 {self.target_altitude}米')
                send_ack("SUCCESS", f"开始起飞到{self.target_altitude}米")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法起飞')
                send_ack("FAILED", "无人机未解锁，无法起飞")
            elif not self.is_on_ground():
                print('❌ 无人机不在地面，无法起飞')
                send_ack("FAILED", "无人机不在地面，无法起飞")
            elif self.current_status != "IDLE":
                print(f'❌ 无人机状态不正确（{self.current_status}），只能在IDLE状态下起飞')
                send_ack("FAILED", f"无人机状态不正确（{self.current_status}），只能在IDLE状态下起飞")
            else:
                print('❌ 无法起飞，条件不满足')
                send_ack("FAILED", "无法起飞，条件不满足")
                
        elif command_type == 'LAND':
            if self.can_land():
                self.target_altitude = 0.0
                self.current_status = "LANDING"
                self.current_speed = 1.0
                self.target_latitude = None  # 清除位置目标，就地降落
                self.target_longitude = None
                print('✅ 开始降落')
                send_ack("SUCCESS", "开始降落")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法降落')
                send_ack("FAILED", "无人机未解锁，无法降落")
            elif self.is_on_ground():
                print('❌ 无人机已在地面，无需降落')
                send_ack("FAILED", "无人机已在地面，无需降落")
            elif self.current_status in ["LANDING", "RETURNING_TO_LAUNCH"]:
                print(f'❌ 无人机正在执行{self.current_status}，无法重复降落')
                send_ack("FAILED", f"无人机正在执行{self.current_status}，无法重复降落")
            else:
                print(f'❌ 当前状态（{self.current_status}）无法执行降落命令')
                send_ack("FAILED", f"当前状态（{self.current_status}）无法执行降落命令")
            
        elif command_type == 'RTL':
            if self.is_armed and (self.is_airborne() or self.current_status == "IDLE"):
                self.target_latitude = self.home_latitude
                self.target_longitude = self.home_longitude
                self.target_altitude = 0.0 if self.is_on_ground() else max(20.0, self.current_altitude)  # 如果在空中，先保持安全高度
                self.current_status = "RETURNING_TO_LAUNCH"
                self.current_speed = 2.0
                self.flying = True
                print(f'✅ 返回起飞点: ({self.home_latitude:.6f}, {self.home_longitude:.6f})')
                send_ack("SUCCESS", f"返回起飞点: ({self.home_latitude:.6f}, {self.home_longitude:.6f})")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法执行RTL命令')
                send_ack("FAILED", "无人机未解锁，无法执行RTL命令")
            elif self.current_status in ["LANDING"]:
                print(f'❌ 无人机正在{self.current_status}，无法执行RTL')
                send_ack("FAILED", f"无人机正在{self.current_status}，无法执行RTL")
            else:
                print('❌ 无法执行RTL命令')
                send_ack("FAILED", "无法执行RTL命令")
            
        elif command_type == 'GOTO':
            if self.can_goto():
                try:
                    self.target_latitude = float(parameters.get('latitude'))
                    self.target_longitude = float(parameters.get('longitude'))
                    self.target_altitude = float(parameters.get('altitude', self.current_altitude))
                    self.target_altitude = max(5.0, min(500.0, self.target_altitude))  # 限制高度范围
                    self.current_status = "GOTO"
                    self.current_speed = float(parameters.get('speed', 5.0))
                    self.current_speed = max(0.5, min(20.0, self.current_speed))  # 限制速度范围
                    self.flying = True
                    print(f'✅ 前往位置: ({self.target_latitude}, {self.target_longitude}, {self.target_altitude}), 速度: {self.current_speed}m/s')
                    send_ack("SUCCESS", f"前往位置: ({self.target_latitude:.6f}, {self.target_longitude:.6f}, {self.target_altitude:.1f})")
                except (ValueError, TypeError) as e:
                    print(f'❌ GOTO参数格式错误: {e}')
                    send_ack("FAILED", f"参数格式错误: {e}")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法执行GOTO命令')
                send_ack("FAILED", "无人机未解锁，无法执行GOTO命令")
            elif self.is_on_ground():
                print('❌ 无人机在地面，无法执行GOTO命令，请先起飞')
                send_ack("FAILED", "无人机在地面，无法执行GOTO命令，请先起飞")
            elif self.current_status in ["LANDING", "RETURNING_TO_LAUNCH"]:
                print(f'❌ 无人机正在{self.current_status}，无法执行GOTO')
                send_ack("FAILED", f"无人机正在{self.current_status}，无法执行GOTO")
            else:
                print(f'❌ 当前状态（{self.current_status}）无法执行GOTO命令')
                send_ack("FAILED", f"当前状态（{self.current_status}）无法执行GOTO命令")
                
        elif command_type == 'HOVER':
            if self.can_hover():
                self.current_status = "HOVER"
                self.current_speed = 0.0
                self.target_latitude = None  # 清除位置目标
                self.target_longitude = None
                print('✅ 开始悬停')
                send_ack("SUCCESS", "开始悬停")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法悬停')
                send_ack("FAILED", "无人机未解锁，无法悬停")
            elif self.is_on_ground():
                print('❌ 无人机在地面，无法悬停，请先起飞')
                send_ack("FAILED", "无人机在地面，无法悬停，请先起飞")
            elif self.current_status in ["LANDING", "RETURNING_TO_LAUNCH"]:
                print(f'❌ 无人机正在{self.current_status}，无法悬停')
                send_ack("FAILED", f"无人机正在{self.current_status}，无法悬停")
            elif self.current_status == "HOVER":
                print('⚠️ 无人机已在悬停状态')
                send_ack("SUCCESS", "无人机已在悬停状态")
            else:
                print(f'❌ 当前状态（{self.current_status}）无法悬停')
                send_ack("FAILED", f"当前状态（{self.current_status}）无法悬停")
        
        elif command_type == 'SET_SPEED':
            if self.can_set_speed():
                try:
                    new_speed = float(parameters.get('speed', self.current_speed))
                    self.current_speed = max(0.1, min(20.0, new_speed))
                    print(f'✅ 设置速度为 {self.current_speed}m/s')
                    send_ack("SUCCESS", f"设置速度为{self.current_speed}m/s")
                except (ValueError, TypeError) as e:
                    print(f'❌ 速度参数格式错误: {e}')
                    send_ack("FAILED", f"速度参数格式错误: {e}")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法设置速度')
                send_ack("FAILED", "无人机未解锁，无法设置速度")
            elif self.is_on_ground():
                print('❌ 无人机在地面，无法设置飞行速度')
                send_ack("FAILED", "无人机在地面，无法设置飞行速度")
            elif self.current_status in ["HOVER", "LANDING"]:
                print(f'❌ 无人机在{self.current_status}状态，无法设置速度')
                send_ack("FAILED", f"无人机在{self.current_status}状态，无法设置速度")
            else:
                print(f'❌ 当前状态（{self.current_status}）无法设置速度')
                send_ack("FAILED", f"当前状态（{self.current_status}）无法设置速度")
                
        elif command_type == 'SET_ALTITUDE':
            if self.can_set_altitude():
                try:
                    new_altitude = float(parameters.get('altitude', self.current_altitude))
                    self.target_altitude = max(0.0, min(500.0, new_altitude))
                    
                    if self.target_altitude <= 0.0:
                        self.current_status = "LANDING"
                        self.current_speed = 1.0
                        print(f'✅ 设置目标高度为 {self.target_altitude}米 (开始降落)')
                    else:
                        if self.current_status == "HOVER":
                            self.current_status = "FLYING"
                        self.flying = True
                        print(f'✅ 设置目标高度为 {self.target_altitude}米')
                    
                    send_ack("SUCCESS", f"设置目标高度为{self.target_altitude}米")
                except (ValueError, TypeError) as e:
                    print(f'❌ 高度参数格式错误: {e}')
                    send_ack("FAILED", f"高度参数格式错误: {e}")
            elif not self.is_armed:
                print('❌ 无人机未解锁，无法设置高度')
                send_ack("FAILED", "无人机未解锁，无法设置高度")
            elif self.current_status in ["LANDING", "RETURNING_TO_LAUNCH"]:
                print(f'❌ 无人机正在{self.current_status}，无法手动设置高度')
                send_ack("FAILED", f"无人机正在{self.current_status}，无法手动设置高度")
            else:
                print(f'❌ 当前状态（{self.current_status}）无法设置高度')
                send_ack("FAILED", f"当前状态（{self.current_status}）无法设置高度")
        
        else:
            print(f'❓ 未知命令类型: {command_type}')
            send_ack("FAILED", f"不支持的命令类型: {command_type}")

    def simulate_flight(self):
        """模拟飞行动作 - 严格的状态机控制"""
        print(f'🔄 模拟飞行步骤开始:')
        print(f'   当前位置: ({self.current_latitude:.6f}, {self.current_longitude:.6f}, {self.current_altitude:.2f}m)')
        print(f'   当前状态: {self.current_status}, 速度: {self.current_speed:.2f}m/s, 解锁: {self.is_armed}')
        
        # 如果无人机未解锁且在地面，不执行任何动作
        if not self.is_armed and self.is_on_ground():
            print('   ⚠️ 无人机未解锁且在地面，跳过飞行模拟')
            return
        
        # 根据状态执行相应的动作
        if self.current_status == "IDLE":
            # IDLE状态下只能在地面，不执行任何移动
            if not self.is_on_ground():
                print('   ⚠️ IDLE状态但不在地面，强制着陆')
                self.current_altitude = max(0.0, self.current_altitude - 5.0)
                if self.current_altitude <= 0.5:
                    self.current_altitude = 0.0
                    print('   🛬 强制着陆完成')
            self.current_speed = 0.0
            self.target_latitude = None
            self.target_longitude = None
            self.target_altitude = None
            print('   ✅ IDLE状态 - 地面待命')
            
        elif self.current_status in ["FLYING", "TAKEOFF", "LANDING", "GOTO", "HOVER", "RETURNING_TO_LAUNCH"]:
            # 只有在这些状态下才执行飞行动作
            
            # 高度控制
            if self.target_altitude is not None:
                altitude_diff = self.target_altitude - self.current_altitude
                print(f'   高度控制: 目标={self.target_altitude:.2f}m, 当前={self.current_altitude:.2f}m, 差值={altitude_diff:.2f}m')
                
                if abs(altitude_diff) > 0.1:
                    # 根据状态调整爬升/下降速度
                    if self.current_status == "LANDING":
                        altitude_change = min(abs(altitude_diff), 3.0)  # 降落较慢
                    elif self.current_status in ["FLYING", "TAKEOFF"]:
                        altitude_change = min(abs(altitude_diff), 5.0)  # 正常速度
                    else:
                        altitude_change = min(abs(altitude_diff), 2.0)  # 其他状态较慢
                        
                    if altitude_diff > 0:
                        self.current_altitude += altitude_change
                        print(f'   ⬆️ 上升 {altitude_change:.1f}m 到 {self.current_altitude:.2f}m')
                    else:
                        self.current_altitude -= altitude_change
                        print(f'   ⬇️ 下降 {altitude_change:.1f}m 到 {self.current_altitude:.2f}m')
                else:
                    self.current_altitude = self.target_altitude
                    print(f'   ✅ 已到达目标高度: {self.current_altitude:.2f}m')
                    
                    # 根据状态处理到达目标高度后的逻辑
                    if self.target_altitude <= 0.5:
                        self.current_status = "IDLE"
                        self.current_speed = 0.0
                        self.flying = False
                        self.target_altitude = None
                        self.target_latitude = None
                        self.target_longitude = None
                        print('   🛬 已着陆，进入IDLE状态')
                    elif self.current_status in ["FLYING", "TAKEOFF"] and self.target_latitude is None and self.target_longitude is None:
                        self.current_status = "HOVER"
                        self.current_speed = 0.0
                        print('   🚁 到达目标高度，开始悬停')
            
            # 位置控制（只有在特定状态下才执行）
            if (self.target_latitude is not None and self.target_longitude is not None and 
                self.current_status in ["GOTO", "RETURNING_TO_LAUNCH", "FLYING"]):
                
                print(f'   位置控制: 目标=({self.target_latitude:.6f}, {self.target_longitude:.6f})')
                
                lat_diff = self.target_latitude - self.current_latitude
                lon_diff = self.target_longitude - self.current_longitude
                
                distance_deg = (lat_diff**2 + lon_diff**2)**0.5
                distance_meters = distance_deg * 111000
                
                if distance_deg > 0.0001 and self.current_speed > 0:
                    lat_direction = lat_diff / distance_deg
                    lon_direction = lon_diff / distance_deg
                    
                    time_step = 5.0
                    move_distance_meters = self.current_speed * time_step
                    move_distance_deg = move_distance_meters / 111000.0
                    
                    lat_move = lat_direction * move_distance_deg
                    lon_move = lon_direction * move_distance_deg
                    
                    if abs(lat_move) > abs(lat_diff):
                        lat_move = lat_diff
                    if abs(lon_move) > abs(lon_diff):
                        lon_move = lon_diff
                    
                    old_lat, old_lon = self.current_latitude, self.current_longitude
                    self.current_latitude += lat_move
                    self.current_longitude += lon_move
                    
                    self.current_heading = math.degrees(math.atan2(lon_diff, lat_diff)) % 360
                    
                    actual_move_deg = ((lat_move**2 + lon_move**2)**0.5)
                    actual_move_meters = actual_move_deg * 111000
                    
                    print(f'   🛫 位置更新: ({old_lat:.6f}, {old_lon:.6f}) → ({self.current_latitude:.6f}, {self.current_longitude:.6f})')
                    print(f'   📏 实际移动: {actual_move_meters:.2f}米, 剩余距离: {distance_meters-actual_move_meters:.1f}米')
                    print(f'   🧭 航向: {self.current_heading:.1f}°')
                else:
                    # 到达目标位置
                    if distance_deg <= 0.0001:
                        self.current_latitude = self.target_latitude
                        self.current_longitude = self.target_longitude
                        
                        if self.current_status == "GOTO":
                            self.current_status = "HOVER"
                            self.current_speed = 0.0
                            self.target_latitude = None
                            self.target_longitude = None
                            print(f'   ✅ 已到达目标位置: ({self.current_latitude:.6f}, {self.current_longitude:.6f})')
                            print(f'   🚁 进入悬停模式')
                        elif self.current_status == "RETURNING_TO_LAUNCH":
                            self.target_altitude = 0.0
                            self.current_status = "LANDING"
                            self.current_speed = 1.0
                            self.target_latitude = None
                            self.target_longitude = None
                            print(f'   ✅ 已返回起飞点，开始降落')
            
            # HOVER状态特殊处理
            if self.current_status == "HOVER":
                self.current_speed = 0.0
                # 清除位置目标，保持悬停
                if self.target_latitude is not None or self.target_longitude is not None:
                    self.target_latitude = None
                    self.target_longitude = None
                    print('   🚁 悬停状态 - 清除位置目标')
        
        else:
            # 其他状态（如ERROR等）不执行任何动作
            print(f'   ⚠️ 状态 {self.current_status} 不执行飞行动作')
        
        # 电池消耗（根据状态调整消耗率）
        old_battery = self.current_battery
        if not self.is_armed:
            # 未解锁时极低消耗
            self.current_battery -= 0.01
        elif self.current_status == "IDLE":
            # 地面待命时低消耗
            self.current_battery -= 0.05
        elif self.current_status == "HOVER":
            # 悬停时中等消耗
            self.current_battery -= 0.2
        elif self.current_status in ["FLYING", "GOTO", "RETURNING_TO_LAUNCH"]:
            # 飞行时高消耗
            self.current_battery -= 0.5
        elif self.current_status in ["TAKEOFF", "LANDING"]:
            # 起降时中高消耗
            self.current_battery -= 0.3
        else:
            # 其他状态默认消耗
            self.current_battery -= 0.1
        
        self.current_battery = max(0.0, self.current_battery)
        
        # 低电量保护
        if self.current_battery <= 20.0 and self.is_airborne() and self.current_status != "LANDING":
            print(f'   ⚠️ 电量过低 ({self.current_battery:.1f}%)，强制返航降落')
            self.target_latitude = self.home_latitude
            self.target_longitude = self.home_longitude
            self.target_altitude = 0.0
            self.current_status = "RETURNING_TO_LAUNCH"
            self.current_speed = 2.0
        
        print(f'   🔋 电池: {old_battery:.1f}% → {self.current_battery:.1f}%')
        print(f'🔄 模拟飞行步骤结束\n')

    def publish_telemetry(self):
        """发布遥测数据"""
        while not self.stop_event.is_set():
            if self.mqtt_connected.is_set():
                self.simulate_flight()
                
                payload = {
                    "droneId": self.drone_id,
                    "timestamp": time.time(),
                    "latitude": round(self.current_latitude, 6),
                    "longitude": round(self.current_longitude, 6),
                    "altitude": round(self.current_altitude, 2),
                    "batteryLevel": round(self.current_battery, 1),
                    "speed": round(self.current_speed, 2),
                    "heading": round(self.current_heading, 1),
                    "status": self.current_status,
                    "isArmed": self.is_armed,
                    "satellites": 8,
                    "signalStrength": 85.0,
                    "temperature": 20.0
                }
                
                try:
                    json_payload = json.dumps(payload)
                    result = self.mqtt_client.publish(self.telemetry_topic, json_payload)
                    print(f'📡 发送遥测: 状态={self.current_status}, 高度={self.current_altitude:.1f}m, 电量={self.current_battery:.1f}%')
                except Exception as e:
                    print(f'❌ 发送遥测失败: {e}')
            
            time.sleep(5)

    def start(self):
        """启动模拟器"""
        print(f'🚁 启动已注册无人机模拟器')
        print(f'📋 序列号: {self.drone_info.get("serialNumber", "Unknown")}')
        print(f'🆔 UUID: {self.drone_id}')
        print(f'📡 遥测主题: {self.telemetry_topic}')
        print(f'🎮 命令主题: {self.commands_topic}')
        print(f'📤 响应主题: {self.responses_topic}')
        
        # 连接MQTT
        client_id = f"sim-drone-{self.drone_id[:8]}-{str(uuid.uuid4())[:4]}"
        self.mqtt_client = mqtt.Client(client_id=client_id)
        self.mqtt_client.on_connect = self.on_connect
        self.mqtt_client.on_disconnect = self.on_disconnect
        self.mqtt_client.on_message = self.on_message
        
        try:
            self.mqtt_client.connect(self.mqtt_host, self.mqtt_port, 60)
            self.mqtt_client.loop_start()
            
            # 启动遥测线程
            telemetry_thread = threading.Thread(target=self.publish_telemetry, daemon=True)
            telemetry_thread.start()
            
            print('✅ 模拟器已启动，等待命令...')
            print('按 Ctrl+C 停止模拟器')
            
            # 主循环
            while not self.stop_event.is_set():
                time.sleep(1)
                
        except Exception as e:
            print(f'❌ 启动失败: {e}')
        finally:
            if self.mqtt_client:
                self.mqtt_client.disconnect()
            print('模拟器已关闭')

def list_available_drones(backend_url):
    """列出系统中可用的无人机"""
    try:
        url = f"{backend_url}/api/v1/drones"
        response = requests.get(url, timeout=10)
        
        if response.status_code == 200:
            drones = response.json()
            if drones:
                print("📋 系统中可用的无人机:")
                for drone in drones:
                    print(f"   - ID: {drone.get('droneId')}")
                    print(f"     序列号: {drone.get('serialNumber')}")
                    print(f"     型号: {drone.get('model')}")
                    print(f"     状态: {drone.get('currentStatus')}")
                    print()
            else:
                print("❌ 系统中没有已注册的无人机")
        else:
            print(f"❌ 无法获取无人机列表 (HTTP {response.status_code})")
            
    except Exception as e:
        print(f"❌ 获取无人机列表失败: {e}")

def main():
    parser = argparse.ArgumentParser(description='已注册无人机模拟器')
    parser.add_argument('--drone-id', '-d', 
                       help='要模拟的无人机ID')
    parser.add_argument('--backend-url', '-b', 
                       default='http://localhost:8080',
                       help='后端服务URL (默认: http://localhost:8080)')
    parser.add_argument('--list', '-l', 
                       action='store_true',
                       help='列出系统中可用的无人机')
    
    args = parser.parse_args()
    
    if args.list:
        list_available_drones(args.backend_url)
        return
    
    if not args.drone_id:
        print("❌ 请指定要模拟的无人机ID")
        print("使用 --list 参数查看可用的无人机")
        print("使用 --drone-id <ID> 指定要模拟的无人机")
        print("\n示例:")
        print("  python existing_drone_simulator.py --list")
        print("  python existing_drone_simulator.py --drone-id 3b1f02cd-a18d-4729-93b6-6134b116df74")
        return
    
    # 创建并启动模拟器
    simulator = DroneSimulator(args.drone_id, args.backend_url)
    simulator.start()

if __name__ == "__main__":
    main() 