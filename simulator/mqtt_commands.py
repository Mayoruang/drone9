#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MQTT命令测试工具 - 无人机遥控器
用于向无人机模拟器发送各种命令进行测试
模拟飞手的遥控器操作
"""

import json
import time
import uuid
import argparse
import paho.mqtt.client as mqtt

# 默认配置
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_DRONE_ID = "3b1f02cd-a18d-4729-93b6-6134b116df74"

def create_command(command_type, **parameters):
    """创建命令JSON"""
    return {
        "commandId": f"cmd-{str(uuid.uuid4())[:8]}",
        "type": command_type,
        "parameters": parameters
    }

def send_command(client, drone_id, command):
    """发送命令到无人机"""
    topic = f"drones/{drone_id}/commands"
    payload = json.dumps(command)
    print(f"📡 发送命令到 {topic}")
    print(f"📄 命令内容: {payload}")
    
    result = client.publish(topic, payload, qos=1)
    result.wait_for_publish(timeout=2)
    print("✅ 命令已发送")
    print("-" * 50)

def print_help():
    """打印帮助信息"""
    print("\n🎮 无人机遥控器 - 可用命令:")
    print("=" * 50)
    print("📋 基础操作:")
    print("  arm                    - 解锁无人机")
    print("  disarm                 - 锁定无人机")
    print("  takeoff [高度]         - 起飞 (默认30米)")
    print("  land                   - 降落")
    print("  rtl                    - 返航")
    print("")
    print("🎯 飞行控制:")
    print("  goto <纬度> <经度> [高度] [速度] - 单点飞行")
    print("  stop                   - 停止当前任务，悬停")
    print("")
    print("🔄 轨迹巡航:")
    print("  patrol <类型> [大小] [高度] [速度] - 启动轨迹巡航")
    print("    类型: RECTANGLE, CIRCLE, TRIANGLE, LINE")
    print("    示例: patrol RECTANGLE 150 25 5")
    print("")
    print("🔧 其他:")
    print("  help                   - 显示帮助")
    print("  quit                   - 退出遥控器")
    print("=" * 50)

def main():
    parser = argparse.ArgumentParser(description="无人机MQTT遥控器")
    parser.add_argument("--mqtt-host", default=DEFAULT_MQTT_HOST, help="MQTT代理地址")
    parser.add_argument("--mqtt-port", type=int, default=DEFAULT_MQTT_PORT, help="MQTT端口")
    parser.add_argument("--drone-id", default=DEFAULT_DRONE_ID, help="目标无人机ID")
    parser.add_argument("--command", help="直接发送指定命令")
    parser.add_argument("--interactive", "-i", action="store_true", help="交互模式")
    
    args = parser.parse_args()
    
    # 创建MQTT客户端（兼容不同版本）
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    except AttributeError:
        # 兼容老版本的paho-mqtt
        client = mqtt.Client()
    
    try:
        print(f"🔗 连接到MQTT代理: {args.mqtt_host}:{args.mqtt_port}")
        client.connect(args.mqtt_host, args.mqtt_port, 60)
        client.loop_start()
        time.sleep(1)  # 等待连接建立
        
        if args.command:
            # 直接发送指定命令
            cmd_lower = args.command.lower()
            if cmd_lower == "arm":
                cmd = create_command("ARM")
            elif cmd_lower == "disarm":
                cmd = create_command("DISARM")
            elif cmd_lower == "takeoff":
                cmd = create_command("TAKEOFF", altitude=30)
            elif cmd_lower == "land":
                cmd = create_command("LAND")
            elif cmd_lower == "rtl":
                cmd = create_command("RTL")
            else:
                print(f"❌ 未知命令: {args.command}")
                return
            
            send_command(client, args.drone_id, cmd)
            
        elif args.interactive:
            # 交互模式
            print(f"\n🎮 无人机遥控器 - 交互模式")
            print(f"🎯 目标无人机: {args.drone_id}")
            print_help()
            
            while True:
                try:
                    user_input = input("\n🔧 遥控器> ").strip().split()
                    if not user_input:
                        continue
                    
                    cmd_type = user_input[0].lower()
                    
                    if cmd_type == "quit" or cmd_type == "exit":
                        break
                    elif cmd_type == "help" or cmd_type == "h":
                        print_help()
                        continue
                        
                    elif cmd_type == "arm":
                        cmd = create_command("ARM")
                        send_command(client, args.drone_id, cmd)
                        print("💡 提示: 无人机已解锁，现在可以起飞了")
                        
                    elif cmd_type == "disarm":
                        cmd = create_command("DISARM")
                        send_command(client, args.drone_id, cmd)
                        print("💡 提示: 无人机已锁定，所有飞行任务已停止")
                        
                    elif cmd_type == "takeoff":
                        altitude = float(user_input[1]) if len(user_input) > 1 else 30
                        cmd = create_command("TAKEOFF", altitude=altitude)
                        send_command(client, args.drone_id, cmd)
                        print(f"💡 提示: 无人机正在起飞到{altitude}米高度")
                        
                    elif cmd_type == "land":
                        cmd = create_command("LAND")
                        send_command(client, args.drone_id, cmd)
                        print("💡 提示: 无人机正在原地降落")
                        
                    elif cmd_type == "goto":
                        if len(user_input) < 3:
                            print("❌ GOTO命令需要经纬度参数")
                            print("   格式: goto <纬度> <经度> [高度] [速度]")
                            continue
                        latitude = float(user_input[1])
                        longitude = float(user_input[2])
                        altitude = float(user_input[3]) if len(user_input) > 3 else 25
                        speed = float(user_input[4]) if len(user_input) > 4 else 5
                        cmd = create_command("GOTO", 
                                           latitude=latitude, 
                                           longitude=longitude, 
                                           altitude=altitude, 
                                           speed=speed)
                        send_command(client, args.drone_id, cmd)
                        print(f"💡 提示: 无人机正在飞往目标点 ({latitude:.6f}, {longitude:.6f})")
                        
                    elif cmd_type == "patrol":
                        if len(user_input) < 2:
                            print("❌ PATROL命令需要轨迹类型参数")
                            print("   格式: patrol <类型> [大小] [高度] [速度]")
                            print("   类型: RECTANGLE, CIRCLE, TRIANGLE, LINE")
                            continue
                        traj_type = user_input[1].upper()
                        if traj_type not in ["RECTANGLE", "CIRCLE", "TRIANGLE", "LINE"]:
                            print("❌ 轨迹类型必须是: RECTANGLE, CIRCLE, TRIANGLE, LINE")
                            continue
                            
                        size = float(user_input[2]) if len(user_input) > 2 else 100
                        altitude = float(user_input[3]) if len(user_input) > 3 else 30
                        speed = float(user_input[4]) if len(user_input) > 4 else 5
                        
                        cmd = create_command("PATROL",
                                           trajectoryType=traj_type,
                                           size=size,
                                           altitude=altitude,
                                           speed=speed)
                        send_command(client, args.drone_id, cmd)
                        print(f"💡 提示: 无人机开始执行{traj_type}轨迹巡航")
                        
                    elif cmd_type == "stop":
                        cmd = create_command("STOP_PATROL")
                        send_command(client, args.drone_id, cmd)
                        print("💡 提示: 无人机停止巡航，保持悬停状态")
                        
                    elif cmd_type == "rtl":
                        cmd = create_command("RTL")
                        send_command(client, args.drone_id, cmd)
                        print("💡 提示: 无人机正在返回起飞点")
                        
                    else:
                        print(f"❌ 未知命令: {cmd_type}")
                        print("💡 输入 'help' 查看可用命令")
                        
                except KeyboardInterrupt:
                    print("\n\n👋 退出遥控器")
                    break
                except ValueError:
                    print("❌ 参数格式错误，请检查数值输入")
                except Exception as e:
                    print(f"❌ 命令执行错误: {e}")
        else:
            # 演示模式：发送一系列预定义命令
            print(f"\n🎯 演示模式 - 完整飞行流程演示")
            print(f"🎯 目标无人机: {args.drone_id}")
            print("=" * 40)
            
            # 1. 解锁
            print("1️⃣ 解锁无人机...")
            cmd = create_command("ARM")
            send_command(client, args.drone_id, cmd)
            time.sleep(2)
            
            # 2. 起飞
            print("2️⃣ 发送起飞命令...")
            cmd = create_command("TAKEOFF", altitude=25)
            send_command(client, args.drone_id, cmd)
            time.sleep(5)
            
            # 3. 矩形轨迹巡航
            print("3️⃣ 发送矩形轨迹巡航命令...")
            cmd = create_command("PATROL", 
                               trajectoryType="RECTANGLE",
                               size=150,
                               altitude=30,
                               speed=6)
            send_command(client, args.drone_id, cmd)
            time.sleep(5)
            
            # 4. 等待一段时间后停止巡航
            print("4️⃣ 等待30秒后停止巡航...")
            time.sleep(30)
            
            cmd = create_command("STOP_PATROL")
            send_command(client, args.drone_id, cmd)
            time.sleep(2)
            
            # 5. 降落
            print("5️⃣ 发送降落命令...")
            cmd = create_command("LAND")
            send_command(client, args.drone_id, cmd)
            
            print("🎉 演示完成！")
    
    except Exception as e:
        print(f"❌ 连接错误: {e}")
    finally:
        client.disconnect()

if __name__ == "__main__":
    main() 