# 🚀 无人机模拟器快速开始

## ⚡ 一键启动

### 方法1: 使用演示脚本 (推荐)
```bash
cd simulator
chmod +x start_demo.sh
./start_demo.sh
```

### 方法2: 直接命令启动
```bash
cd simulator
pip install -r requirements.txt
python3 drone_simulator.py --auto-patrol --verbose
```

## 📋 前置要求

- ✅ Python 3.7+
- ✅ 后端服务运行中 (http://localhost:8080)
- ✅ MQTT服务器运行中 (localhost:1883)
- ✅ 管理员账户可用 (用于审批注册)

⚠️ **重要提示**: 所有无人机都必须通过完整的注册审批流程！

## 🎮 测试轨迹模式

### 矩形轨迹
```bash
python3 drone_simulator.py --auto-patrol --trajectory-type RECTANGLE --trajectory-size 200 --verbose
```

### 圆形轨迹
```bash
python3 drone_simulator.py --auto-patrol --trajectory-type CIRCLE --trajectory-size 150 --verbose
```

### 三角形轨迹
```bash
python3 drone_simulator.py --auto-patrol --trajectory-type TRIANGLE --trajectory-size 120 --verbose
```

### 直线往复
```bash
python3 drone_simulator.py --auto-patrol --trajectory-type LINE --trajectory-size 300 --verbose
```

## 📝 注册流程说明

1. **启动模拟器** - 自动生成序列号并发送注册请求
2. **等待审批** - 模拟器会显示"等待管理员审批..."
3. **管理员操作** - 登录Web管理后台审批注册请求
4. **获得批准** - 模拟器自动连接MQTT并开始工作
5. **开始巡航** - 如果使用`--auto-patrol`，将自动开始轨迹巡航

## 🔧 控制无人机

### 方法1: 使用命令工具
```bash
# 交互模式
python3 mqtt_commands.py -i

# 快速命令
python3 mqtt_commands.py --command takeoff
python3 mqtt_commands.py --command land
```

### 方法2: 直接发送MQTT (需要mosquitto客户端)
```bash
# 获取无人机ID (从模拟器输出中查看)
DRONE_ID="your-drone-id-here"

# 起飞
mosquitto_pub -h localhost -t "drones/$DRONE_ID/commands" -m '{"commandId":"cmd-001","type":"TAKEOFF","parameters":{"altitude":30}}'

# 矩形巡航
mosquitto_pub -h localhost -t "drones/$DRONE_ID/commands" -m '{"commandId":"cmd-002","type":"PATROL","parameters":{"trajectoryType":"RECTANGLE","size":200,"altitude":25,"speed":5}}'

# 停止巡航
mosquitto_pub -h localhost -t "drones/$DRONE_ID/commands" -m '{"commandId":"cmd-003","type":"STOP_PATROL"}'

# 降落
mosquitto_pub -h localhost -t "drones/$DRONE_ID/commands" -m '{"commandId":"cmd-004","type":"LAND"}'
```

## 📊 监控遥测数据

```bash
# 监听遥测数据
mosquitto_sub -h localhost -t "drones/+/telemetry" -v

# 监听所有无人机
mosquitto_sub -h localhost -t "drones/+/+" -v
```

## 🐛 常见问题

### Q: 提示依赖缺失？
```bash
pip3 install paho-mqtt requests
```

### Q: 无法连接后端服务？
确保后端服务运行正常:
```bash
curl http://localhost:8080/api/v1/health
```

### Q: 注册一直等待？
- 检查管理员是否在线
- 登录Web管理后台审批注册请求
- 确认没有序列号冲突

### Q: 无法连接MQTT？
确保MQTT服务器运行正常:
```bash
# Ubuntu/Debian
sudo apt install mosquitto mosquitto-clients
sudo systemctl start mosquitto

# macOS  
brew install mosquitto
brew services start mosquitto
```

### Q: 轨迹不生成？
检查参数是否合理，size应该 > 0

### Q: 找不到python3？
```bash
# 使用python代替
python drone_simulator.py --help
```

## 🎯 完整演示流程

### 单架无人机演示
1. **启动模拟器**
   ```bash
   python3 drone_simulator.py --verbose
   ```

2. **管理员审批** (在Web管理后台操作)
   - 登录管理系统
   - 进入无人机管理页面
   - 找到待审批的注册请求
   - 点击"批准"

3. **发送命令** (新开终端)
   ```bash
   python3 mqtt_commands.py -i
   ```

4. **输入命令序列**
   ```
   takeoff 30
   patrol RECTANGLE 200 30 5
   stop
   land
   quit
   ```

5. **观察输出** - 查看轨迹生成和飞行过程

### 批量演示
```bash
# 使用演示脚本批量启动
./start_demo.sh
# 选择选项6 - 批量启动多架无人机
```

## 🏭 生产环境部署

### 连接远程服务
```bash
python3 drone_simulator.py \
  --backend-url "https://your-platform.com/api/v1" \
  --mqtt-host "mqtt.your-platform.com" \
  --mqtt-port 8883 \
  --verbose
```

### 指定无人机信息
```bash
python3 drone_simulator.py \
  --serial "PROD-DRONE-001" \
  --model "DJI-Phantom-4" \
  --backend-url "https://production.com/api/v1" \
  --verbose
```

---

🎉 **恭喜！** 你已经成功运行了支持轨迹往复飞行的无人机模拟器！

⚠️ **记住**: 每架无人机都必须经过管理员审批才能开始工作！

💡 更多详细信息请查看 `README_simulator.md` 