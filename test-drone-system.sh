#!/bin/bash

# 无人机系统测试脚本
echo "=== 无人机管理系统测试 ==="

# 检查是否在正确的目录
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ 请在项目根目录运行此脚本"
    exit 1
fi

# 检查Docker服务是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查Python是否安装
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3未安装"
    exit 1
fi

echo "🚀 启动Docker服务..."
docker-compose up -d

echo "⏳ 等待服务启动..."
sleep 15

echo "📊 检查服务状态..."
docker-compose ps

echo ""
echo "=== 测试后端服务连通性 ==="
echo "测试健康检查端点..."
for i in {1..5}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null; then
        echo "✅ 后端服务可访问"
        break
    else
        echo "⏳ 等待后端服务启动... ($i/5)"
        sleep 5
    fi
done

echo ""
echo "=== 安装Python依赖 ==="
cd simulator
if [ ! -f "requirements.txt" ]; then
    echo "❌ requirements.txt 文件不存在"
    exit 1
fi

pip3 install -r requirements.txt

echo ""
echo "=== 无人机操作菜单 ==="
echo "1. 注册新无人机"
echo "2. 列出已注册的无人机"
echo "3. 启动无人机模拟器"
echo "4. 退出"

while true; do
    read -p "请选择操作 (1-4): " choice
    
    case $choice in
        1)
            echo "🚁 注册新无人机..."
            python3 drone_simulator.py
            ;;
        2)
            echo "📋 列出已注册的无人机..."
            python3 drone_simulator.py --list
            ;;
        3)
            echo "📋 首先查看可用的无人机："
            python3 existing_drone_simulator.py --list
            echo ""
            read -p "请输入要模拟的无人机ID: " drone_id
            if [ ! -z "$drone_id" ]; then
                echo "🚁 启动无人机模拟器 (ID: $drone_id)..."
                python3 existing_drone_simulator.py --drone-id "$drone_id"
            else
                echo "❌ 无人机ID不能为空"
            fi
            ;;
        4)
            echo "👋 退出测试脚本"
            break
            ;;
        *)
            echo "❌ 无效选择，请输入1-4"
            ;;
    esac
    
    echo ""
    echo "按Enter键继续..."
    read
    echo "=== 无人机操作菜单 ==="
    echo "1. 注册新无人机"
    echo "2. 列出已注册的无人机"
    echo "3. 启动无人机模拟器"
    echo "4. 退出"
done

cd ..
echo ""
echo "💡 提示:"
echo "  - 使用 'docker-compose logs -f backend' 查看后端日志"
echo "  - 使用 'docker-compose down' 停止所有服务"
echo "  - 后端API: http://localhost:8080"
echo "  - EMQX控制台: http://localhost:18083 (admin/public)" 