# 无人机模拟系统 - 新架构说明

## 🏗️ 系统架构

新的无人机模拟系统采用**分离式架构**，模拟真实的无人机操控环境：

```
┌─────────────────┐    MQTT    ┌─────────────────┐    HTTP    ┌─────────────────┐
│   遥控器脚本     │ ──────────► │  无人机模拟器    │ ──────────► │   后端系统       │
│ mqtt_commands.py│    Commands │ drone_simulator.py│  Telemetry │  Spring Boot    │
└─────────────────┘            └─────────────────┘            └─────────────────┘
                                        │                             │
                                        │ MQTT Telemetry             │ WebSocket
                                        ▼                             ▼
                                ┌─────────────────┐            ┌─────────────────┐
                                │   MQTT Broker   │            │   前端界面       │
                                │   (mosquitto)   │            │   Vue.js        │
                                └─────────────────┘            └─────────────────┘
                                        │
                                        ▼
                                ┌─────────────────┐
                                │   InfluxDB      │
                                │   时序数据库     │
                                └─────────────────┘
```

## 🚁 核心组件

### 1. drone_simulator.py - 无人机本体
- **职责**: 模拟真实无人机的状态和行为
- **初始状态**: 地面待命，等待遥控器命令
- **功能**:
  - 自动注册流程
  - MQTT通信
  - 状态模拟（位置、电池、传感器等）
  - 命令执行（起飞、降落、巡航等）
  - 实时遥测数据发送

### 2. mqtt_commands.py - 遥控器
- **职责**: 模拟飞手的遥控器操作
- **功能**:
  - 交互式控制界面
  - 预定义命令快速发送
  - 完整飞行流程演示

## 🎮 使用流程

### 第一步：启动无人机模拟器
```bash
cd simulator
python drone_simulator.py --verbose
```

**预期结果**:
- 无人机完成注册流程
- 连接到MQTT代理
- 进入地面待命状态
- 前端显示无人机状态为 "IDLE"

### 第二步：使用遥控器操控

#### 方式1：交互模式（推荐）
```bash
# 启动交互式遥控器
python mqtt_commands.py --drone-id "YOUR_DRONE_SERIAL" -i

# 然后按顺序输入命令：
arm          # 解锁无人机
takeoff 30   # 起飞到30米
patrol RECTANGLE 150 25 5  # 矩形巡航
stop         # 停止巡航，悬停
land         # 降落
quit         # 退出遥控器
```

#### 方式2：单条命令模式
```bash
python mqtt_commands.py --drone-id "YOUR_DRONE_SERIAL" --command arm
python mqtt_commands.py --drone-id "YOUR_DRONE_SERIAL" --command takeoff
```

#### 方式3：演示模式（完整流程）
```bash
python mqtt_commands.py --drone-id "YOUR_DRONE_SERIAL"
```

## 📊 状态变化监控

### 前端监控
正确实施后，前端应该能看到以下状态变化：

1. **地面待命**: `IDLE` → 无人机在地面，未解锁
2. **解锁状态**: `ARMED` → 无人机已解锁，可以起飞
3. **起飞过程**: `TAKEOFF` → 无人机正在爬升
4. **悬停状态**: `HOVERING` → 无人机在目标高度悬停
5. **巡航状态**: `PATROL_RECTANGLE` → 无人机执行矩形轨迹
6. **降落过程**: `LANDING` → 无人机正在下降
7. **完成降落**: `IDLE` → 回到地面待命状态

### 数据链路
```
遥控器命令 → MQTT → 无人机模拟器 → 状态更新 → 遥测数据 → MQTT → InfluxDB → 后端 → 前端
```

## 🎯 命令参考

### 基础操作
- `arm` - 解锁无人机（必须先执行）
- `disarm` - 锁定无人机，停止所有任务
- `takeoff [高度]` - 起飞到指定高度（默认30米）
- `land` - 原地降落
- `rtl` - 返回起飞点

### 飞行控制
- `goto <纬度> <经度> [高度] [速度]` - 飞往指定坐标
- `stop` - 停止当前任务，保持悬停

### 轨迹巡航
- `patrol RECTANGLE [大小] [高度] [速度]` - 矩形轨迹
- `patrol CIRCLE [半径] [高度] [速度]` - 圆形轨迹
- `patrol TRIANGLE [边长] [高度] [速度]` - 三角形轨迹
- `patrol LINE [长度] [高度] [速度]` - 直线往复

## 🔧 技术细节

### 状态管理
- 引入 `is_armed` 状态，确保安全操作
- 增加 `STATUS_HOVERING` 状态，区分悬停和地面待命
- 改进状态转换逻辑，符合真实飞行流程

### 通信协议
- MQTT命令格式：
```json
{
  "commandId": "cmd-uuid",
  "type": "TAKEOFF|LAND|PATROL|...",
  "parameters": {
    "altitude": 30,
    "speed": 5,
    ...
  }
}
```

- 遥测数据格式：
```json
{
  "droneId": "uuid",
  "timestamp": 1748254131.88067,
  "latitude": 41.795526,
  "longitude": 123.432106,
  "altitude": 30,
  "flightMode": "PATROL_RECTANGLE",
  "status": "PATROL",
  "isArmed": true,
  "trajectoryInfo": {
    "type": "RECTANGLE",
    "active": true,
    "currentIndex": 2,
    "totalPoints": 4
  }
}
```

## 🎯 预期效果

1. **启动无人机**: 前端显示地面待命状态
2. **发送解锁命令**: 前端状态变为 "ARMED"
3. **发送起飞命令**: 状态变为 "TAKEOFF" → "HOVERING"
4. **发送巡航命令**: 状态变为 "PATROL_RECTANGLE"，开始轨迹飞行
5. **发送停止命令**: 状态变为 "HOVERING"
6. **发送降落命令**: 状态变为 "LANDING" → "IDLE"

## 🐛 故障排查

### 常见问题
1. **MQTT连接失败**: 检查mosquitto服务是否运行
2. **命令无响应**: 确认无人机序列号正确
3. **状态不更新**: 检查InfluxDB连接和后端服务
4. **前端显示异常**: 确认WebSocket连接正常

### 调试技巧
- 使用 `--verbose` 参数查看详细日志
- 检查MQTT主题是否正确
- 验证遥测数据格式
- 监控数据库写入情况

## 📈 性能优化建议

1. **减少遥测频率**: 根据需要调整 `--telemetry-interval`
2. **优化轨迹计算**: 预计算轨迹点，减少实时计算
3. **批量数据写入**: 优化InfluxDB写入性能
4. **前端缓存**: 实现状态缓存，减少不必要的更新

这个新架构确保了无人机模拟系统的状态变化能正确反映在整个数据链路中，从遥控器命令到前端显示形成完整的闭环。 