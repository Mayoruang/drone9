#!/bin/bash

echo "🔄 重启无人机管理系统..."

# 停止系统
./stop-system.sh

# 等待进程完全停止
sleep 3

echo "🚀 重新启动系统..."
# 重新启动系统（包含智能网络检测）
./start-system.sh
