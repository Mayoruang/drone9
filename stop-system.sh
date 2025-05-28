#!/bin/bash

echo "🛑 停止无人机管理系统..."

# 停止服务
if [[ -f .backend.pid ]]; then
    backend_pid=$(cat .backend.pid)
    if kill -0 "$backend_pid" 2>/dev/null; then
        echo "停止后端服务 (PID: $backend_pid)..."
        kill "$backend_pid"
    fi
    rm -f .backend.pid
fi

if [[ -f .frontend.pid ]]; then
    frontend_pid=$(cat .frontend.pid)
    if kill -0 "$frontend_pid" 2>/dev/null; then
        echo "停止前端服务 (PID: $frontend_pid)..."
        kill "$frontend_pid"
    fi
    rm -f .frontend.pid
fi

# 强制停止相关进程
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite.*5666" 2>/dev/null || true

echo "✅ 系统已停止"
