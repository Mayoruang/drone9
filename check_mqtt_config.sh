#!/bin/bash
# 检查MQTT配置是否正确加载到Spring后端

echo "正在检查后端MQTT配置..."

# 使用curl调用后端健康检查接口
HEALTH_RESPONSE=$(curl -s http://localhost:8080/actuator/health)

if [[ $HEALTH_RESPONSE == *"UP"* ]]; then
  echo "✅ 后端服务正常运行"
else
  echo "❌ 后端服务未运行或健康检查失败"
  exit 1
fi

# 检查环境变量
echo "正在检查MQTT环境变量..."
CONTAINER_ID=$(docker ps -qf "name=drone-backend")

if [ -z "$CONTAINER_ID" ]; then
  echo "❌ 未找到drone-backend容器"
  exit 1
fi

# 检查MQTT配置是否存在
echo "MQTT配置:"
docker exec $CONTAINER_ID env | grep -i mqtt

# 检查后端日志中的MQTT相关信息
echo -e "\nMQTT配置日志:"
docker logs $CONTAINER_ID 2>&1 | grep -i mqtt | tail -10

echo -e "\n请确认以下配置项存在且正确:"
echo "1. MQTT_BROKER_URL - MQTT代理连接URL"
echo "2. MQTT_TOPICS_TELEMETRY - 遥测数据主题模式"
echo "3. MQTT_TOPICS_COMMANDS - 命令主题模式"
echo "4. MQTT_TOPICS_RESPONSES - 响应主题模式"

echo -e "\n如果配置正确但仍然无法获取MQTT凭证，请检查后端注册服务代码是否正确处理MQTT凭证生成。" 