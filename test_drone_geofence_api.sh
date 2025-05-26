#!/bin/bash

# 测试无人机地理围栏API的脚本

BASE_URL="http://localhost:8080/api/v1/drones"
DRONE_ID="17b6627e-3dd1-4e56-ba2c-80777cf3ef4b"

echo "=== 无人机地理围栏API测试 ==="
echo "Base URL: $BASE_URL"
echo "Test Drone ID: $DRONE_ID"
echo ""

# 1. 测试基础连接
echo "1. 测试基础API连接..."
curl -X GET "$BASE_URL/test" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 2. 测试地理围栏端点连接
echo "2. 测试地理围栏端点连接..."
curl -X GET "$BASE_URL/test-geofence/$DRONE_ID" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 3. 获取所有无人机列表
echo "3. 获取无人机列表..."
curl -X GET "$BASE_URL" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 4. 获取特定无人机详情
echo "4. 获取无人机详情..."
curl -X GET "$BASE_URL/$DRONE_ID" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 5. 获取无人机的地理围栏
echo "5. 获取无人机关联的地理围栏..."
curl -X GET "$BASE_URL/$DRONE_ID/geofences" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 6. 获取可用的地理围栏
echo "6. 获取可分配的地理围栏..."
curl -X GET "$BASE_URL/$DRONE_ID/geofences/available" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n" \
  -s
echo ""

# 7. 测试分配地理围栏（需要先获取地理围栏ID）
echo "7. 获取地理围栏列表以获取测试ID..."
GEOFENCE_RESPONSE=$(curl -X GET "http://localhost:8080/api/v1/geofences" \
  -H "Content-Type: application/json" \
  -s)

echo "Geofence response: $GEOFENCE_RESPONSE"

# 提取第一个地理围栏ID（如果存在）
GEOFENCE_ID=$(echo "$GEOFENCE_RESPONSE" | grep -o '"geofenceId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ ! -z "$GEOFENCE_ID" ]; then
    echo "Found geofence ID: $GEOFENCE_ID"
    echo ""
    
    echo "8. 测试分配地理围栏..."
    curl -X POST "$BASE_URL/$DRONE_ID/geofences" \
      -H "Content-Type: application/json" \
      -d "{\"geofenceIds\":[\"$GEOFENCE_ID\"]}" \
      -w "\nStatus: %{http_code}\n" \
      -s
    echo ""
    
    echo "9. 验证分配结果..."
    curl -X GET "$BASE_URL/$DRONE_ID/geofences" \
      -H "Content-Type: application/json" \
      -w "\nStatus: %{http_code}\n" \
      -s
    echo ""
else
    echo "No geofences found for testing assignment"
fi

echo "=== 测试完成 ===" 