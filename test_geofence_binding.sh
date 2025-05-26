#!/bin/bash

# 地理围栏无人机绑定功能测试脚本
echo "🚁 地理围栏无人机绑定功能测试"
echo "=================================="

BASE_URL="http://localhost:8081/api/v1"

# 1. 测试后端连接
echo "🔗 1. 测试后端连接"
curl -s "$BASE_URL/geofences/test"
echo ""

# 2. 创建测试地理围栏
echo "🗺️  2. 创建测试地理围栏"
geofence_data='{
  "name": "测试绑定围栏-'$(date +%H%M%S)'",
  "type": "NO_FLY_ZONE",
  "description": "测试无人机绑定功能",
  "coordinates": [
    {"lat": 41.7962, "lng": 123.4309},
    {"lat": 41.7962, "lng": 123.4409},
    {"lat": 41.7862, "lng": 123.4409},
    {"lat": 41.7862, "lng": 123.4309}
  ]
}'

create_response=$(curl -s -X POST "$BASE_URL/geofences" \
    -H "Content-Type: application/json" \
    -d "$geofence_data")

echo "创建响应: $create_response"
geofence_id=$(echo "$create_response" | grep -o '"geofenceId":"[^"]*"' | cut -d'"' -f4)
echo "地理围栏ID: $geofence_id"

# 3. 测试无人机绑定
if [ -n "$geofence_id" ]; then
    echo "🔗 3. 测试无人机绑定"
    bind_data='{"droneIds": ["27ab5670-4643-4b87-8dd3-69de10785b65"]}'
    
    bind_response=$(curl -s -X POST "$BASE_URL/geofences/$geofence_id/drones" \
        -H "Content-Type: application/json" \
        -d "$bind_data")
    
    echo "绑定响应: $bind_response"
    
    # 4. 验证绑定结果
    echo "✅ 4. 验证绑定结果"
    detail_response=$(curl -s "$BASE_URL/geofences/$geofence_id")
    echo "详情响应: $detail_response"
    
    # 5. 清理
    echo "🧹 5. 清理测试数据"
    curl -s -X DELETE "$BASE_URL/geofences/$geofence_id"
    echo "测试完成"
fi

echo "==================================" 