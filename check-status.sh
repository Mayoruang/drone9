#!/bin/bash

echo "📊 无人机管理系统状态检查"
echo "========================"

# 检查后端状态
echo -n "后端服务: "
if curl -s http://localhost:8080/api/status >/dev/null 2>&1; then
    echo "✅ 运行中"
    backend_status=$(curl -s http://localhost:8080/api/status | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', 'unknown'))")
    echo "   状态: $backend_status"
else
    echo "❌ 未运行"
fi

# 检查前端状态
echo -n "前端服务: "
if curl -s http://localhost:5666 >/dev/null 2>&1; then
    echo "✅ 运行中"
else
    echo "❌ 未运行"
fi

# 检查进程
echo ""
echo "运行中的相关进程:"
ps aux | grep -E "(spring-boot|vite.*5666)" | grep -v grep || echo "  无相关进程"

# 检查端口占用
echo ""
echo "端口占用情况:"
lsof -i :8080 2>/dev/null | head -2 || echo "  端口 8080: 未占用"
lsof -i :5666 2>/dev/null | head -2 || echo "  端口 5666: 未占用"
