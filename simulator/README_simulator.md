# 无人机模拟器使用文档

## 概述

这是一个功能完整的无人机模拟器，支持：
- 无人机注册流程
- MQTT连接和命令处理
- 实时遥测数据上报
- **轨迹往复飞行** (矩形、圆形、三角形、直线等)
- 自动巡航模式
- 电池和环境参数模拟

## 功能特性

### 🛸 飞行模式
- **IDLE**: 地面待机
- **TAKEOFF**: 起飞模式
- **FLYING**: 单点飞行
- **PATROL**: 轨迹巡航 (新增)
- **LANDING**: 降落模式
- **RTL**: 返航模式

### 📐 支持的轨迹类型
- **RECTANGLE**: 矩形轨迹往复飞行
- **CIRCLE**: 圆形轨迹循环飞行
- **TRIANGLE**: 三角形轨迹飞行
- **LINE**: 直线往复飞行

## 前置要求

⚠️ **重要**: 使用前请确保：
1. **后端服务正在运行** (http://localhost:8080)
2. **MQTT服务器正在运行** (localhost:1883)
3. **管理员账户可用** (用于审批无人机注册)

## 安装依赖

```bash
pip install paho-mqtt requests
```

## 基本使用

### 1. 标准启动 (完整注册流程)
```bash
python drone_simulator.py --verbose
```

### 2. 指定后端地址
```bash
python drone_simulator.py --backend-url "http://your-server:8080/api/v1" --verbose
```

### 3. 自动轨迹演示 (注册后启动)
```bash
# 矩形轨迹演示
python drone_simulator.py --auto-patrol --trajectory-type RECTANGLE --trajectory-size 200 --verbose

# 圆形轨迹演示
python drone_simulator.py --auto-patrol --trajectory-type CIRCLE --trajectory-size 150 --verbose

# 三角形轨迹演示
python drone_simulator.py --auto-patrol --trajectory-type TRIANGLE --trajectory-size 100 --verbose
```

## 命令行参数

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `--serial` | 无人机序列号 | 自动生成 |
| `--model` | 无人机型号 | SimDrone-X2 |
| `--backend-url` | 后端API地址 | http://localhost:8080/api/v1 |
| `--mqtt-host` | MQTT代理地址 | localhost |
| `--mqtt-port` | MQTT端口 | 1883 |
| `--telemetry-interval` | 遥测间隔(秒) | 5 |
| `--poll-interval` | 注册状态查询间隔(秒) | 10 |
| `--auto-patrol` | 注册成功后自动开始巡航 | false |
| `--trajectory-type` | 轨迹类型 | RECTANGLE |
| `--trajectory-size` | 轨迹大小(米) | 100 |
| `--verbose` | 详细输出 | false |

## 注册流程

### 1. 自动注册
模拟器启动时会自动：
1. 生成唯一序列号
2. 向后端发送注册请求
3. 等待管理员审批
4. 获取MQTT凭证
5. 连接MQTT并开始遥测

### 2. 管理员审批
注册请求提交后，需要管理员在Web界面中审批：
1. 登录管理后台
2. 进入无人机管理页面
3. 查看待审批的注册请求
4. 点击"批准"完成注册

### 3. 注册状态查询
模拟器会自动轮询注册状态，直到：
- ✅ 获得批准并继续运行
- ❌ 被拒绝并退出
- ⏰ 超时退出 (默认10分钟)

## MQTT命令接口

### 基本飞行命令

#### 1. 起飞 (TAKEOFF)
```json
{
  "commandId": "cmd-001",
  "type": "TAKEOFF",
  "parameters": {
    "altitude": 30
  }
}
```

#### 2. 单点飞行 (GOTO)
```json
{
  "commandId": "cmd-002",
  "type": "GOTO",
  "parameters": {
    "latitude": 41.7962,
    "longitude": 123.4309,
    "altitude": 25,
    "speed": 8
  }
}
```

#### 3. 降落 (LAND)
```json
{
  "commandId": "cmd-003",
  "type": "LAND"
}
```

### 轨迹巡航命令

#### 1. 矩形轨迹巡航
```json
{
  "commandId": "cmd-004",
  "type": "PATROL",
  "parameters": {
    "trajectoryType": "RECTANGLE",
    "width": 200,
    "height": 150,
    "altitude": 30,
    "speed": 5
  }
}
```

#### 2. 圆形轨迹巡航
```json
{
  "commandId": "cmd-005",
  "type": "PATROL",
  "parameters": {
    "trajectoryType": "CIRCLE",
    "radius": 100,
    "altitude": 25,
    "speed": 6,
    "num_points": 12
  }
}
```

#### 3. 三角形轨迹巡航
```json
{
  "commandId": "cmd-006",
  "type": "PATROL",
  "parameters": {
    "trajectoryType": "TRIANGLE",
    "size": 120,
    "altitude": 35,
    "speed": 4
  }
}
```

#### 4. 直线往复巡航
```json
{
  "commandId": "cmd-007",
  "type": "PATROL",
  "parameters": {
    "trajectoryType": "LINE",
    "end_lat": 41.7972,
    "end_lon": 123.4319,
    "altitude": 20,
    "speed": 7,
    "num_points": 5
  }
}
```

#### 5. 停止巡航
```json
{
  "commandId": "cmd-008",
  "type": "STOP_PATROL"
}
```

## 遥测数据格式

模拟器会定期发送包含轨迹信息的遥测数据：

```json
{
  "droneId": "drone-id",
  "timestamp": 1703123456.789,
  "latitude": 41.796200,
  "longitude": 123.430900,
  "altitude": 25.5,
  "batteryLevel": 85.2,
  "speed": 5.2,
  "heading": 45.0,
  "flightMode": "PATROL_RECTANGLE",
  "status": "PATROL",
  "trajectoryInfo": {
    "type": "RECTANGLE",
    "active": true,
    "currentIndex": 2,
    "totalPoints": 4,
    "direction": 1,
    "cycles": 1.5
  }
}
```

### 轨迹信息字段说明
- `type`: 轨迹类型
- `active`: 巡航是否激活
- `currentIndex`: 当前轨迹点索引
- `totalPoints`: 总轨迹点数
- `direction`: 飞行方向 (1正向/-1反向)
- `cycles`: 完成的循环次数

## 使用场景

### 1. 开发测试
```bash
# 启动测试环境 (需要后端服务运行)
python drone_simulator.py --verbose
```

### 2. 巡检任务模拟
```bash
# 模拟矩形区域巡检
python drone_simulator.py --auto-patrol \
  --trajectory-type RECTANGLE --trajectory-size 300 \
  --telemetry-interval 3 --verbose
```

### 3. 边界巡航模拟
```bash
# 模拟圆形边界巡航
python drone_simulator.py --auto-patrol \
  --trajectory-type CIRCLE --trajectory-size 200 \
  --telemetry-interval 2 --verbose
```

### 4. 航线往复模拟
```bash
# 模拟直线航线往复
python drone_simulator.py --auto-patrol \
  --trajectory-type LINE --trajectory-size 500 \
  --telemetry-interval 5 --verbose
```

## 实际部署示例

### 1. 连接生产环境
```bash
python drone_simulator.py \
  --backend-url "https://your-drone-platform.com/api/v1" \
  --mqtt-host "mqtt.your-platform.com" \
  --mqtt-port 8883 \
  --serial "REAL-DRONE-001" \
  --model "DJI-Phantom-4" \
  --verbose
```

### 2. 批量启动多架无人机
```bash
# 启动第一架 (不同序列号避免冲突)
python drone_simulator.py --serial "SIM-001" --verbose &

# 启动第二架  
python drone_simulator.py --serial "SIM-002" --verbose &

# 启动第三架
python drone_simulator.py --serial "SIM-003" --verbose &
```

## 故障排除

### 1. 注册失败
- ✅ 确认后端服务运行正常 (http://localhost:8080)
- ✅ 检查网络连接
- ✅ 验证API地址正确
- ✅ 确保没有序列号冲突

### 2. MQTT连接失败
```bash
# 检查MQTT服务状态
mosquitto_pub -h localhost -t test -m "hello"
```

### 3. 注册等待超时
- 确保管理员及时审批注册请求
- 可以增加 `--poll-interval` 参数调整查询频率

### 4. 轨迹不生成
- 检查轨迹参数是否合理
- 确认中心坐标有效
- 查看详细日志输出

## 环境配置

### 1. 启动后端服务
```bash
# 确保后端服务运行在 localhost:8080
# 如果使用其他地址，需要通过 --backend-url 指定
```

### 2. 启动MQTT服务
```bash
# Ubuntu/Debian
sudo apt install mosquitto mosquitto-clients
sudo systemctl start mosquitto

# macOS  
brew install mosquitto
brew services start mosquitto

# Docker (可选)
docker run -it -p 1883:1883 eclipse-mosquitto
```

### 3. 环境变量配置
```bash
export DRONE_BACKEND_URL="http://production-server:8080/api/v1"
export DRONE_MQTT_HOST="production-mqtt.server.com"
export DRONE_MQTT_PORT="8883"
```

## 日志输出示例

```
[2024-01-15 10:30:00] INFO: 启动无人机模拟器 - 支持轨迹往复飞行
[2024-01-15 10:30:00] INFO: 序列号: SIM-A1B2C3D4E5F6
[2024-01-15 10:30:01] INFO: 尝试注册无人机 - 序列号: SIM-A1B2C3D4E5F6, 型号: SimDrone-X2
[2024-01-15 10:30:02] INFO: 注册请求已提交，请求ID: reg-12345
[2024-01-15 10:30:02] INFO: 等待管理员审批...
[2024-01-15 10:30:12] INFO: 注册状态: PENDING_APPROVAL
[2024-01-15 10:30:22] INFO: 注册状态: APPROVED
[2024-01-15 10:30:22] INFO: 注册已批准！无人机ID: drone-67890
[2024-01-15 10:30:23] INFO: 成功连接到MQTT代理，无人机ID: drone-67890
[2024-01-15 10:30:53] INFO: 生成矩形轨迹: 中心(41.796200, 123.430900), 尺寸200x150米
[2024-01-15 10:30:58] INFO: 开始RECTANGLE轨迹巡航，共4个点
```

## 注意事项

1. **注册流程**: 必须等待管理员审批，不能跳过
2. **服务依赖**: 需要后端API和MQTT服务都正常运行
3. **序列号唯一性**: 每个模拟器实例需要不同的序列号
4. **坐标系统**: 使用WGS84坐标系统
5. **距离单位**: 所有距离参数使用米为单位
6. **速度限制**: 建议速度设置在1-20 m/s之间
7. **电池模拟**: 巡航模式下电池消耗较快
8. **网络要求**: 需要稳定的网络连接到后端和MQTT服务器

---

更多技术支持，请参考源代码注释或联系开发团队。