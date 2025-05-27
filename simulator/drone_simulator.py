#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无人机注册脚本
专门用于生成新的无人机并注册到系统中
注册成功后，可使用 existing_drone_simulator.py 来模拟该无人机
"""

import argparse
import json
import time
import uuid
import signal
import sys
import requests
from datetime import datetime

# --- 配置常量 ---
DEFAULT_BACKEND_URL = "http://localhost:8080/api/v1"
DEFAULT_MODEL = "SimDrone-X2"
DEFAULT_POLL_INTERVAL = 10      # 秒
DEFAULT_MAX_POLLS = 60          # 最多轮询60次（10分钟）

# 注册状态常量
STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
STATUS_APPROVED = "APPROVED"
STATUS_REJECTED = "REJECTED"

# --- 全局变量 ---
stop_event = False

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

def register_drone(serial_number, model, backend_url):
    """注册无人机"""
    log_info(f"尝试注册无人机 - 序列号: {serial_number}, 型号: {model}")
    payload = {
        "serialNumber": serial_number,
        "model": model,
        "notes": f"Python注册脚本生成的无人机 - {datetime.now().isoformat()}"
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

def list_existing_drones(backend_url):
    """列出系统中已存在的无人机"""
    try:
        response = requests.get(f"{backend_url}/drones", timeout=10)
        response.raise_for_status()
        drones = response.json()
        
        if drones:
            log_info(f"系统中现有 {len(drones)} 架已注册的无人机:")
            for drone in drones:
                print(f"   - ID: {drone.get('droneId')}")
                print(f"     序列号: {drone.get('serialNumber')}")
                print(f"     型号: {drone.get('model')}")
                print(f"     状态: {drone.get('currentStatus')}")
                print()
        else:
            log_info("系统中暂无已注册的无人机")
        
        return drones
    except requests.exceptions.RequestException as e:
        log_error(f"获取无人机列表失败: {e}")
        return []

def signal_handler(signum, frame):
    """信号处理函数"""
    global stop_event
    log_info("收到终止信号，正在退出...")
    stop_event = True
    sys.exit(0)

def main():
    global args, stop_event
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description="无人机注册脚本 - 生成新无人机并注册到系统")
    parser.add_argument("--serial", help="自定义无人机序列号（可选，默认随机生成）")
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"无人机型号（默认: {DEFAULT_MODEL}）")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL, help=f"后端API地址（默认: {DEFAULT_BACKEND_URL}）")
    parser.add_argument("--poll-interval", type=int, default=DEFAULT_POLL_INTERVAL, help=f"注册状态查询间隔秒数（默认: {DEFAULT_POLL_INTERVAL}）")
    parser.add_argument("--max-polls", type=int, default=DEFAULT_MAX_POLLS, help=f"最大查询次数（默认: {DEFAULT_MAX_POLLS}）")
    parser.add_argument("--list", "-l", action="store_true", help="列出系统中已注册的无人机")
    parser.add_argument("--verbose", "-v", action="store_true", help="详细输出")
    
    args = parser.parse_args()
    
    # 设置信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    log_info("🚁 启动无人机注册脚本")
    log_info(f"🌐 后端地址: {args.backend_url}")
    
    # 如果只是列出无人机，执行后退出
    if args.list:
        log_info("📋 列出系统中已注册的无人机:")
        list_existing_drones(args.backend_url)
        return
    
    # 生成序列号
    serial_number = get_serial_number(args.serial)
    
    log_info(f"📋 无人机信息:")
    log_info(f"   序列号: {serial_number}")
    log_info(f"   型号: {args.model}")
    
    # 第一步：注册无人机
    log_info("🔑 开始注册无人机")
    request_id = register_drone(serial_number, args.model, args.backend_url)
    if not request_id:
        log_error("❌ 注册失败，退出程序")
        return 1
    
    # 第二步：轮询注册状态
    log_info("⏳ 等待注册审批...")
    poll_count = 0
    
    while poll_count < args.max_polls and not stop_event:
        status_data = check_registration_status(request_id, args.backend_url)
        if status_data:
            status = status_data.get("status")
            log_info(f"📋 注册状态: {status}")
            
            if status == STATUS_APPROVED:
                drone_id = status_data.get("droneId")
                log_info("🎉 注册成功！")
                log_info(f"✅ 无人机ID: {drone_id}")
                log_info(f"📄 序列号: {serial_number}")
                log_info(f"🔧 型号: {args.model}")
                
                # 显示使用提示
                print("\n" + "="*60)
                print("🚀 无人机注册完成！接下来的步骤:")
                print("="*60)
                print(f"1. 使用以下命令启动无人机模拟器:")
                print(f"   python existing_drone_simulator.py -d {drone_id}")
                print(f"\n2. 或者查看所有已注册的无人机:")
                print(f"   python drone_simulator.py --list")
                print(f"\n3. 或者查看模拟器帮助:")
                print(f"   python existing_drone_simulator.py --help")
                print("\n💡 提示: 模拟器启动后，您可以通过前端控制面板发送命令控制无人机")
                print("="*60)
                
                return 0
                
            elif status == STATUS_REJECTED:
                message = status_data.get("message", "未知原因")
                log_error(f"❌ 注册被拒绝: {message}")
                return 1
                
            elif status == STATUS_PENDING_APPROVAL:
                log_info("⏳ 等待管理员审批...")
                
            else:
                log_warn(f"❓ 未知状态: {status}")
        
        poll_count += 1
        if poll_count < args.max_polls:
            log_debug(f"等待 {args.poll_interval} 秒后进行下次查询... ({poll_count}/{args.max_polls})")
            time.sleep(args.poll_interval)
    
    if not stop_event:
        log_error("❌ 注册超时，可能需要更长时间等待管理员审批")
        log_info("💡 您可以稍后使用以下命令查看无人机列表:")
        log_info(f"   python drone_simulator.py --list")
        return 1
    
    return 0

if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)