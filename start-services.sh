#!/bin/bash

# 无人机管理系统服务启动脚本
echo "=== 无人机管理系统服务启动 ==="

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查docker-compose是否可用
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose未安装"
    exit 1
fi

echo "🔧 停止并清理现有容器..."
docker-compose down

echo "🏗️ 构建后端服务镜像..."
docker-compose build backend

echo "🚀 启动所有服务..."
docker-compose up -d

echo "⏳ 等待服务启动..."
sleep 10

echo "📊 检查服务状态..."
docker-compose ps

echo ""
echo "=== 服务访问信息 ==="
echo "🔗 后端API服务: http://localhost:8080"
echo "🔗 后端健康检查: http://localhost:8080/actuator/health"
echo "🔗 PostgreSQL: localhost:5432"
echo "🔗 InfluxDB: http://localhost:8086"
echo "🔗 EMQX Dashboard: http://localhost:18083 (admin/public)"
echo "🔗 MQTT Broker: localhost:1883"
echo ""
echo "=== 无人机注册API示例 ==="
echo "POST http://localhost:8080/api/drones/register"
echo "Content-Type: application/json"
echo ""
echo '{'
echo '  "droneId": "DRONE_001",'
echo '  "name": "测试无人机",'
echo '  "model": "DJI Phantom 4",'
echo '  "manufacturer": "DJI",'
echo '  "serialNumber": "SN123456789"'
echo '}'
echo ""
echo "✅ 服务启动完成！"
echo "💡 使用 'docker-compose logs -f backend' 查看后端日志"
echo "💡 使用 'docker-compose down' 停止所有服务" 