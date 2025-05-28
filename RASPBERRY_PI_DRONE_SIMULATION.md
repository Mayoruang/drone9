# 树莓派无人机模拟完整指南
立即开始使用

  ssh huang@192.168.139.101
   # 密码: 2345

进入工作目录并激活环境
cd /home/huang/drone-simulator
   source venv/bin/activate


   注册新无人机
   python3 drone_simulator.py --backend-url 'http://192.168.2.171:8080/api/v1'
   启动无人机模拟器
     # 查看可用无人机
   python3 existing_drone_simulator.py --list --backend-url 'http://192.168.2.171:8080'
   
   # 启动模拟器
   python3 existing_drone_simulator.py --drone-id <ID> --backend-url 'http://192.168.139.136:8080' --mqtt-host '192.168.139.136'
## 📖 概述

本指南将帮助您在树莓派上设置和运行无人机模拟器，实现虚拟无人机与后端管理系统的完整交互。树莓派将模拟真实无人机的行为，包括注册、状态上报、命令执行等功能。

## 🎯 应用场景

- **开发测试**: 在没有真实无人机的情况下测试系统功能
- **教学演示**: 展示无人机管理系统的完整工作流程
- **压力测试**: 模拟多架无人机同时工作的场景
- **远程部署**: 在边缘设备上部署无人机节点
- **成本控制**: 降低测试和开发成本

## 🛠️ 硬件要求

### 推荐配置
- **树莓派 4B (4GB RAM)** - 最佳性能
- **32GB MicroSD卡 (Class 10)** - 充足存储空间
- **稳定的网络连接** - WiFi或以太网

### 最低配置
- **树莓派 3B+** - 基本功能
- **16GB MicroSD卡** - 最小存储需求
- **网络连接** - 连接到后端服务

## 💻 软件环境

### 操作系统
- **Raspberry Pi OS (Bullseye)** - 推荐版本
- **Ubuntu 22.04 LTS for ARM** - 替代选择

### 必需软件
- **Python 3.8+** - 运行模拟器
- **pip** - Python包管理器
- **git** - 代码下载
- **curl** - 网络测试工具

## 🚀 快速部署

### 1. 准备树莓派

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装必要工具
sudo apt install -y python3 python3-pip git curl

# 检查Python版本
python3 --version  # 应该 >= 3.8
```

### 2. 下载模拟器代码

```bash
# 克隆项目（如果有Git仓库）
git clone https://github.com/your-repo/drone-system.git
cd drone-system/simulator

# 或者手动创建目录并复制文件
mkdir -p ~/drone-simulator
cd ~/drone-simulator

# 将以下文件复制到树莓派：
# - drone_simulator.py
# - existing_drone_simulator.py  
# - requirements.txt
```

### 3. 安装Python依赖

```bash
# 创建虚拟环境（推荐）
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 或者手动安装
pip install paho-mqtt>=1.6.0 requests>=2.25.0
```

### 4. 配置网络连接

```bash
# 确定后端服务器地址
# 例如：后端服务运行在 192.168.1.100:8080
BACKEND_IP="192.168.1.100"
BACKEND_URL="http://${BACKEND_IP}:8080/api/v1"
MQTT_HOST="${BACKEND_IP}"

# 测试连接
curl http://${BACKEND_IP}:8080/actuator/health
```

## 📝 使用说明

### 方式一: 注册新无人机

```bash
# 1. 激活虚拟环境
source venv/bin/activate

# 2. 注册新无人机（自动检测后端地址）
python3 drone_simulator.py

# 3. 或者手动指定后端地址
python3 drone_simulator.py --backend-url "http://192.168.1.100:8080/api/v1"

# 4. 使用自定义序列号
python3 drone_simulator.py --serial "PI-DRONE-001"

# 5. 查看帮助
python3 drone_simulator.py --help
```

**注册流程:**
1. 脚本自动生成唯一序列号
2. 向后端提交注册请求
3. 等待管理员审批（开发环境可能自动审批）
4. 显示注册结果和无人机ID

### 方式二: 模拟已注册无人机

```bash
# 1. 查看可用的无人机
python3 existing_drone_simulator.py --list

# 2. 启动指定无人机的模拟器
python3 existing_drone_simulator.py --drone-id <无人机ID>

# 3. 手动指定服务地址
python3 existing_drone_simulator.py \
  --drone-id <无人机ID> \
  --backend-url "http://192.168.1.100:8080" \
  --mqtt-host "192.168.1.100"
```

**模拟功能:**
- 定期发送遥测数据（位置、电池、状态等）
- 接收并执行飞行命令
- 模拟真实的飞行轨迹
- 响应控制台消息

## 🎮 支持的命令

模拟器支持以下无人机操作命令：

### 基础命令
- `ARM` - 解锁无人机，准备飞行
- `DISARM` - 锁定无人机，停止所有电机
- `TAKEOFF` - 起飞到指定高度
- `LAND` - 降落到地面
- `HOVER` - 悬停在当前位置

### 导航命令  
- `GOTO` - 前往指定GPS坐标
- `RTL` - 返回起飞点（Return to Launch）
- `SET_SPEED` - 设置飞行速度
- `SET_ALTITUDE` - 设置飞行高度

### 高级命令
- `EMERGENCY_STOP` - 紧急停止
- `GEOFENCE_CHECK` - 地理围栏检查

## 📡 MQTT通信

### 主题结构
```
drones/{drone_id}/telemetry     # 遥测数据上报
drones/{drone_id}/commands      # 接收飞行命令  
drones/{drone_id}/console       # 控制台消息
drones/{drone_id}/responses     # 命令响应
```

### 遥测数据格式
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "droneId": "drone-123",
  "latitude": 41.8057,
  "longitude": 123.4315,
  "altitude": 50.0,
  "battery": 85.5,
  "status": "FLYING",
  "speed": 10.5,
  "heading": 90.0,
  "armed": true
}
```

### 命令格式
```json
{
  "commandId": "cmd-456",
  "commandType": "TAKEOFF",
  "parameters": {
    "altitude": 30.0
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

## 🔧 高级配置

### 1. 多无人机部署

在一台树莓派上运行多个无人机模拟器：

```bash
# 创建多个配置目录
mkdir -p ~/drones/{drone1,drone2,drone3}

# 为每个无人机分别运行模拟器
cd ~/drones/drone1
python3 ~/drone-simulator/existing_drone_simulator.py --drone-id DRONE-001 &

cd ~/drones/drone2  
python3 ~/drone-simulator/existing_drone_simulator.py --drone-id DRONE-002 &

cd ~/drones/drone3
python3 ~/drone-simulator/existing_drone_simulator.py --drone-id DRONE-003 &
```

### 2. 服务化部署

创建systemd服务，实现开机自启动：

```bash
# 创建服务文件
sudo nano /etc/systemd/system/drone-simulator.service
```

```ini
[Unit]
Description=Drone Simulator
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/drone-simulator
Environment=PATH=/home/pi/drone-simulator/venv/bin
ExecStart=/home/pi/drone-simulator/venv/bin/python3 existing_drone_simulator.py --drone-id YOUR-DRONE-ID
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启用并启动服务
sudo systemctl enable drone-simulator.service
sudo systemctl start drone-simulator.service

# 查看服务状态
sudo systemctl status drone-simulator.service
```

### 3. 配置文件

创建配置文件简化部署：

```bash
# 创建配置文件
nano ~/drone-simulator/config.json
```

```json
{
  "backend_url": "http://192.168.1.100:8080",
  "mqtt_host": "192.168.1.100",
  "mqtt_port": 1883,
  "drone_id": "PI-DRONE-001",
  "telemetry_interval": 5,
  "initial_position": {
    "latitude": 41.8057,
    "longitude": 123.4315
  }
}
```

### 4. 启动脚本

创建便捷的启动脚本：

```bash
# 创建启动脚本
nano ~/start-drone-simulator.sh
```

```bash
#!/bin/bash
cd ~/drone-simulator
source venv/bin/activate

# 检查配置
if [ -f "config.json" ]; then
    echo "使用配置文件启动..."
    python3 existing_drone_simulator.py --config config.json
else
    echo "使用默认配置启动..."
    python3 existing_drone_simulator.py --drone-id PI-DRONE-001
fi
```

```bash
# 设置执行权限
chmod +x ~/start-drone-simulator.sh

# 运行
./start-drone-simulator.sh
```

## 🌐 网络配置

### 1. 静态IP配置

为树莓派设置静态IP地址：

```bash
# 编辑配置文件
sudo nano /etc/dhcpcd.conf

# 添加以下内容
interface wlan0
static ip_address=192.168.1.200/24
static routers=192.168.1.1
static domain_name_servers=8.8.8.8
```

### 2. 防火墙配置

确保网络连接正常：

```bash
# 检查防火墙状态
sudo ufw status

# 如果启用了防火墙，允许必要端口
sudo ufw allow out 8080  # 后端API
sudo ufw allow out 1883  # MQTT
```

### 3. 跨网段访问

如果树莓派和后端服务在不同网段：

```bash
# 检查路由表
ip route

# 添加路由（如果需要）
sudo ip route add 192.168.2.0/24 via 192.168.1.1

# 测试连接
ping 192.168.2.171
telnet 192.168.2.171 8080
```

## 🔍 故障排除

### 常见问题

#### 1. 无法连接到后端服务
```bash
# 检查网络连通性
ping 192.168.1.100

# 检查端口
telnet 192.168.1.100 8080

# 测试API
curl http://192.168.1.100:8080/actuator/health
```

#### 2. MQTT连接失败
```bash
# 检查MQTT服务
telnet 192.168.1.100 1883

# 使用MQTT客户端测试
mosquitto_pub -h 192.168.1.100 -t "test" -m "hello"
```

#### 3. Python依赖问题
```bash
# 重新创建虚拟环境
rm -rf venv
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

#### 4. 权限问题
```bash
# 修复权限
chmod +x *.py
chown -R pi:pi ~/drone-simulator
```

### 日志调试

```bash
# 运行时启用详细输出
python3 existing_drone_simulator.py --drone-id DRONE-001 --verbose

# 查看系统日志
sudo journalctl -u drone-simulator.service -f

# 创建调试日志
python3 existing_drone_simulator.py --drone-id DRONE-001 > ~/drone.log 2>&1 &
```

## 📊 性能优化

### 1. 树莓派优化

```bash
# 增加swap空间
sudo dphys-swapfile swapoff
sudo nano /etc/dphys-swapfile  # 修改 CONF_SWAPSIZE=1024
sudo dphys-swapfile setup
sudo dphys-swapfile swapon

# 启用GPU内存分割
sudo raspi-config  # Advanced Options > Memory Split > 64

# 更新固件
sudo rpi-update
```

### 2. 应用优化

```bash
# 使用更高效的JSON库
pip install ujson

# 减少遥测频率（在代码中修改）
TELEMETRY_INTERVAL = 10  # 从5秒改为10秒

# 使用连接池
pip install urllib3[secure]
```

## 🎓 使用示例

### 示例1: 单机测试

```bash
# 1. 启动后端服务（在主机上）
./start-system.sh

# 2. 在树莓派上注册无人机
python3 drone_simulator.py --serial "PI-TEST-001"

# 3. 启动模拟器
python3 existing_drone_simulator.py --drone-id <返回的ID>

# 4. 在Web界面中发送命令测试
```

### 示例2: 批量部署

```bash
# 创建批量注册脚本
for i in {1..5}; do
    python3 drone_simulator.py --serial "PI-FLEET-$(printf "%03d" $i)"
    sleep 2
done

# 创建批量启动脚本  
for drone_id in DRONE-001 DRONE-002 DRONE-003; do
    python3 existing_drone_simulator.py --drone-id $drone_id &
    sleep 1
done
```

### 示例3: 地理围栏测试

```bash
# 设置特定的初始位置进行地理围栏测试
python3 existing_drone_simulator.py \
  --drone-id DRONE-001 \
  --initial-lat 41.8057 \
  --initial-lon 123.4315
```

## 📚 进阶应用

### 1. 传感器数据模拟

扩展模拟器以包含更多传感器数据：
- GPS精度模拟
- 风速影响
- 温度传感器
- 摄像头状态

### 2. 故障模拟

模拟各种故障场景：
- 低电量告警
- GPS信号丢失
- 通信中断
- 电机故障

### 3. 集群管理

实现多机协调：
- 编队飞行
- 任务分配
- 负载均衡
- 冗余备份

## 🔐 安全考虑

### 1. 网络安全
- 使用VPN连接
- 启用MQTT认证
- 定期更新系统

### 2. 访问控制
- 限制SSH访问
- 使用密钥认证
- 定期更换密码

### 3. 数据保护
- 加密敏感数据
- 备份配置文件
- 监控异常行为

---

## 📞 技术支持

如遇到问题，请参考：
1. 项目README文档
2. API文档
3. 故障排除指南
4. 社区论坛或提交Issue

**祝您使用愉快！** 🚁✨ 