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
import re
from datetime import datetime

# --- 配置常量 ---
# 支持多种后端地址检测
DEFAULT_BACKEND_URLS = [
    "http://localhost:8080/api/v1",  # Docker宿主机
    "http://127.0.0.1:8080/api/v1",  # 本地环回
    "http://host.docker.internal:8080/api/v1"  # Docker内部访问宿主机
]
DEFAULT_MODEL = "SimDrone-X2"
DEFAULT_POLL_INTERVAL = 10      # 秒
DEFAULT_MAX_POLLS = 60          # 最多轮询60次（10分钟）

# 注册状态常量
STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
STATUS_APPROVED = "APPROVED"
STATUS_REJECTED = "REJECTED"

# --- 全局变量 ---
stop_event = False

def detect_backend_url():
    """自动检测可用的后端服务地址"""
    import socket
    
    # 检查8080端口是否可访问
    def check_port(host, port, timeout=3):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(timeout)
            result = sock.connect_ex((host, port))
            sock.close()
            return result == 0
        except:
            return False
    
    # 检测顺序：localhost -> 127.0.0.1
    if check_port('localhost', 8080):
        return "http://localhost:8080/api/v1"
    elif check_port('127.0.0.1', 8080):
        return "http://127.0.0.1:8080/api/v1"
    else:
        log_warn("无法检测到后端服务，将使用默认地址")
        return "http://localhost:8080/api/v1"

def get_serial_number(provided_serial):
    """生成或使用提供的序列号"""
    if provided_serial:
        return provided_serial
    return f"SIM-{str(uuid.uuid4())[:12].upper()}"

def validate_serial_number(serial_number):
    """验证序列号格式"""
    # 检查序列号是否只包含字母、数字、连字符和下划线
    if not re.match(r'^[a-zA-Z0-9_-]+$', serial_number):
        return False, "序列号只能包含字母、数字、连字符(-)和下划线(_)"
    
    # 检查长度
    if len(serial_number) < 1:
        return False, "序列号不能为空"
    
    if len(serial_number) > 50:
        return False, "序列号长度不能超过50个字符"
    
    return True, "有效的序列号"

def suggest_serial_number(original_serial):
    """为无效的序列号提供建议"""
    # 移除无效字符，替换为下划线
    clean_serial = re.sub(r'[^a-zA-Z0-9_-]', '_', original_serial)
    
    # 移除连续的下划线
    clean_serial = re.sub(r'_+', '_', clean_serial)
    
    # 移除开头和结尾的下划线
    clean_serial = clean_serial.strip('_')
    
    # 如果太短，添加随机后缀
    if len(clean_serial) < 3:
        clean_serial = f"DRONE_{clean_serial}_{str(uuid.uuid4())[:8].upper()}"
    
    return clean_serial

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
    
    log_debug(f"注册请求数据: {json.dumps(payload, ensure_ascii=False, indent=2)}")
    
    try:
        response = requests.post(f"{backend_url}/drones/register", json=payload, timeout=10)
        
        # 详细的错误处理
        if not response.ok:
            error_details = "未知错误"
            try:
                error_json = response.json()
                error_details = error_json.get('message', error_json.get('error', str(error_json)))
            except:
                error_details = response.text if response.text else f"HTTP {response.status_code}"
            
            log_error(f"注册失败 (HTTP {response.status_code}): {error_details}")
            log_debug(f"完整响应: {response.text}")
            return None
        
        data = response.json()
        log_info(f"注册请求已提交，请求ID: {data.get('requestId')}")
        return data.get("requestId")
        
    except requests.exceptions.RequestException as e:
        log_error(f"网络请求失败: {e}")
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
    parser.add_argument("--backend-url", help=f"后端API地址（默认: 自动检测）")
    parser.add_argument("--poll-interval", type=int, default=DEFAULT_POLL_INTERVAL, help=f"注册状态查询间隔秒数（默认: {DEFAULT_POLL_INTERVAL}）")
    parser.add_argument("--max-polls", type=int, default=DEFAULT_MAX_POLLS, help=f"最大查询次数（默认: {DEFAULT_MAX_POLLS}）")
    parser.add_argument("--list", "-l", action="store_true", help="列出系统中已注册的无人机")
    parser.add_argument("--verbose", "-v", action="store_true", help="详细输出")
    
    args = parser.parse_args()
    
    # 如果未指定后端URL，自动检测
    if not args.backend_url:
        log_info("🔍 自动检测后端服务地址...")
        args.backend_url = detect_backend_url()
    
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
    
    # 验证序列号格式
    is_valid, validation_message = validate_serial_number(serial_number)
    if not is_valid:
        log_error(f"❌ 序列号格式无效: {validation_message}")
        log_error(f"   当前序列号: {serial_number}")
        
        # 提供建议的序列号
        suggested_serial = suggest_serial_number(serial_number)
        log_info(f"💡 建议使用的序列号: {suggested_serial}")
        log_info(f"📋 序列号格式要求:")
        log_info(f"   - 只能包含字母、数字、连字符(-)和下划线(_)")
        log_info(f"   - 长度在1-50个字符之间")
        log_info(f"   - 例如: DRONE-001, UAV_X2, SIM-ABC123")
        
        # 询问是否使用建议的序列号
        print(f"\n是否使用建议的序列号 '{suggested_serial}'? (y/n): ", end="")
        try:
            choice = input().lower().strip()
            if choice in ['y', 'yes', '是']:
                serial_number = suggested_serial
                log_info(f"✅ 已更新序列号为: {serial_number}")
            else:
                log_error("❌ 序列号无效且未使用建议序列号，退出程序")
                return 1
        except (KeyboardInterrupt, EOFError):
            log_error("❌ 用户取消操作，退出程序")
            return 1
    
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