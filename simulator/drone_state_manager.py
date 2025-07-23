#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无人机状态管理器
用于管理多个无人机的保存状态
"""

import json
import os
import argparse
import glob
from datetime import datetime

def list_all_states():
    """列出所有保存的无人机状态"""
    state_files = glob.glob("drone_state_*.json")
    
    if not state_files:
        print("📁 未找到任何保存的无人机状态文件")
        return
    
    print(f"📋 找到 {len(state_files)} 个无人机状态文件:\n")
    
    for state_file in sorted(state_files):
        try:
            with open(state_file, 'r', encoding='utf-8') as f:
                state_data = json.load(f)
            
            drone_id = state_data.get('drone_id', '未知')
            saved_at = state_data.get('saved_at', '未知时间')
            
            print(f"📄 {state_file}")
            print(f"   🆔 无人机ID: {drone_id}")
            print(f"   💾 保存时间: {saved_at}")
            print(f"   📍 位置: ({state_data.get('current_latitude', 0):.6f}, {state_data.get('current_longitude', 0):.6f})")
            print(f"   ⬆️ 高度: {state_data.get('current_altitude', 0):.1f}m")
            print(f"   🔋 电量: {state_data.get('current_battery', 0):.1f}%")
            print(f"   🛰️ 状态: {state_data.get('current_status', 'Unknown')}")
            print()
            
        except Exception as e:
            print(f"❌ 读取 {state_file} 失败: {e}")

def clean_all_states():
    """清理所有保存的无人机状态"""
    state_files = glob.glob("drone_state_*.json")
    
    if not state_files:
        print("📁 未找到任何保存的无人机状态文件")
        return
    
    print(f"🗑️ 准备删除 {len(state_files)} 个状态文件...")
    
    for state_file in state_files:
        try:
            os.remove(state_file)
            print(f"   ✅ 已删除: {state_file}")
        except Exception as e:
            print(f"   ❌ 删除失败 {state_file}: {e}")
    
    print("🗑️ 清理完成")

def clean_old_states(days=7):
    """清理指定天数之前的状态文件"""
    state_files = glob.glob("drone_state_*.json")
    
    if not state_files:
        print("📁 未找到任何保存的无人机状态文件")
        return
    
    from datetime import datetime, timezone, timedelta
    cutoff_time = datetime.now(timezone.utc) - timedelta(days=days)
    
    cleaned_count = 0
    for state_file in state_files:
        try:
            with open(state_file, 'r', encoding='utf-8') as f:
                state_data = json.load(f)
                
            saved_at_str = state_data.get('saved_at')
            if saved_at_str:
                saved_at = datetime.fromisoformat(saved_at_str.replace('Z', '+00:00'))
                if saved_at < cutoff_time:
                    os.remove(state_file)
                    print(f"🗑️ 删除过期状态文件: {state_file} (保存于: {saved_at_str})")
                    cleaned_count += 1
            else:
                # 如果没有保存时间，检查文件修改时间
                file_mtime = datetime.fromtimestamp(os.path.getmtime(state_file), timezone.utc)
                if file_mtime < cutoff_time:
                    os.remove(state_file)
                    print(f"🗑️ 删除过期状态文件: {state_file} (修改于: {file_mtime})")
                    cleaned_count += 1
                    
        except Exception as e:
            print(f"❌ 处理 {state_file} 失败: {e}")
    
    if cleaned_count > 0:
        print(f"🗑️ 共清理了 {cleaned_count} 个过期状态文件")
    else:
        print(f"✅ 没有发现超过 {days} 天的过期状态文件")

def export_states(output_file="drone_states_backup.json"):
    """导出所有状态到备份文件"""
    state_files = glob.glob("drone_state_*.json")
    
    if not state_files:
        print("📁 未找到任何保存的无人机状态文件")
        return
    
    backup_data = {
        "export_time": datetime.now().isoformat(),
        "states": []
    }
    
    for state_file in state_files:
        try:
            with open(state_file, 'r', encoding='utf-8') as f:
                state_data = json.load(f)
            
            backup_data["states"].append({
                "filename": state_file,
                "data": state_data
            })
            
        except Exception as e:
            print(f"❌ 读取 {state_file} 失败: {e}")
    
    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(backup_data, f, ensure_ascii=False, indent=2)
        
        print(f"💾 已导出 {len(backup_data['states'])} 个状态到: {output_file}")
        
    except Exception as e:
        print(f"❌ 导出失败: {e}")

def import_states(input_file="drone_states_backup.json"):
    """从备份文件导入状态"""
    if not os.path.exists(input_file):
        print(f"❌ 备份文件不存在: {input_file}")
        return
    
    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            backup_data = json.load(f)
        
        states = backup_data.get('states', [])
        if not states:
            print("❌ 备份文件中没有状态数据")
            return
        
        imported_count = 0
        for state_info in states:
            filename = state_info.get('filename')
            state_data = state_info.get('data')
            
            if filename and state_data:
                try:
                    with open(filename, 'w', encoding='utf-8') as f:
                        json.dump(state_data, f, ensure_ascii=False, indent=2)
                    
                    print(f"✅ 已导入: {filename}")
                    imported_count += 1
                    
                except Exception as e:
                    print(f"❌ 导入 {filename} 失败: {e}")
        
        print(f"💾 共导入了 {imported_count} 个状态文件")
        
    except Exception as e:
        print(f"❌ 导入失败: {e}")

def main():
    parser = argparse.ArgumentParser(description='无人机状态管理器')
    parser.add_argument('--list', '-l', action='store_true',
                       help='列出所有保存的状态')
    parser.add_argument('--clean-all', action='store_true',
                       help='清理所有保存的状态')
    parser.add_argument('--clean-old', type=int, metavar='DAYS',
                       help='清理指定天数之前的状态 (默认: 7天)')
    parser.add_argument('--export', '-e', metavar='FILE',
                       help='导出所有状态到文件 (默认: drone_states_backup.json)')
    parser.add_argument('--import', '-i', metavar='FILE', dest='import_file',
                       help='从文件导入状态 (默认: drone_states_backup.json)')
    
    args = parser.parse_args()
    
    if args.list:
        list_all_states()
    elif args.clean_all:
        confirm = input("⚠️ 确定要删除所有状态文件吗? (y/N): ")
        if confirm.lower() == 'y':
            clean_all_states()
        else:
            print("❌ 操作已取消")
    elif args.clean_old is not None:
        days = args.clean_old if args.clean_old > 0 else 7
        clean_old_states(days)
    elif args.export is not None:
        output_file = args.export if args.export else "drone_states_backup.json"
        export_states(output_file)
    elif args.import_file is not None:
        input_file = args.import_file if args.import_file else "drone_states_backup.json"
        import_states(input_file)
    else:
        parser.print_help()
        print("\n示例:")
        print("  python drone_state_manager.py --list")
        print("  python drone_state_manager.py --clean-old 3")
        print("  python drone_state_manager.py --export backup.json")
        print("  python drone_state_manager.py --import backup.json")

if __name__ == "__main__":
    main() 