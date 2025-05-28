# 无人机模拟器

这是一个无人机模拟器系统，包含两个主要组件，支持与Docker容器化的后端服务交互。

## 文件说明

- `drone_simulator.py` - 用于注册新无人机到系统
- `existing_drone_simulator.py` - 用于模拟已注册的无人机
- `requirements.txt` - Python依赖包列表

## 系统要求

- Python 3.7+
- Docker 和 Docker Compose（用于运行后端服务）
- curl（用于测试连接）

## 快速开始

### 1. 启动系统（推荐）

在项目根目录运行：

```bash
# 启动完整系统并进入交互式测试
./test-drone-system.sh
```

### 2. 手动启动

#### 启动后端服务

```bash
# 在项目根目录
./start-services.sh
# 或者
docker-compose up -d
```

#### 安装Python依赖

```bash
cd simulator
pip install -r requirements.txt
```

## 使用方法

### 1. 注册新无人机

```bash
# 注册一个新无人机（自动生成序列号，自动检测后端地址）
python drone_simulator.py

# 使用自定义序列号注册
python drone_simulator.py --serial "MY-DRONE-001"

# 手动指定后端地址
python drone_simulator.py --backend-url "http://localhost:8080/api/v1"

# 查看已注册的无人机
python drone_simulator.py --list
```

### 2. 模拟已注册的无人机

```bash
# 查看可用的无人机（自动检测后端地址）
python existing_drone_simulator.py --list

# 启动指定无人机的模拟器（自动检测服务地址）
python existing_drone_simulator.py --drone-id <无人机ID>

# 手动指定服务地址
python existing_drone_simulator.py --drone-id <无人机ID> --backend-url "http://localhost:8080" --mqtt-host "localhost"
```

## 自动服务检测

模拟器现在支持自动检测后端和MQTT服务地址：

- **后端服务**: 自动检测 `localhost:8080` 和 `127.0.0.1:8080`
- **MQTT服务**: 自动检测 `localhost:1883` 和 `127.0.0.1:1883`

如果检测失败，将使用默认地址并显示警告。

## 支持的命令

模拟器支持以下无人机命令：
- `ARM` - 解锁无人机
- `DISARM` - 锁定无人机  
- `TAKEOFF` - 起飞到指定高度
- `LAND` - 降落
- `GOTO` - 前往指定位置
- `HOVER` - 悬停
- `RTL` - 返回起飞点

## 工作流程

1. 启动Docker服务：`./start-services.sh` 或 `docker-compose up -d`
2. 使用 `drone_simulator.py` 注册新无人机
3. 等待管理员审批注册请求（或在开发环境中自动审批）
4. 使用 `existing_drone_simulator.py` 启动无人机模拟器
5. 通过前端控制面板或MQTT消息发送命令控制无人机

## 服务访问地址

- **后端API**: http://localhost:8080
- **后端健康检查**: http://localhost:8080/actuator/health
- **EMQX Dashboard**: http://localhost:18083 (admin/public)
- **MQTT Broker**: localhost:1883
- **PostgreSQL**: localhost:5432
- **InfluxDB**: http://localhost:8086

## 网络配置

### Docker容器环境

后端服务运行在Docker容器中，通过以下端口映射访问：

```yaml
# docker-compose.yml 中的端口配置
backend:
  ports:
    - "0.0.0.0:8080:8080"  # 绑定所有接口，允许外部访问
```

### 同一网络的外部主机访问

要让同一网络的其他设备访问后端服务：

1. 确保防火墙允许8080端口访问
2. 使用主机的IP地址替换localhost：

```bash
# 例如主机IP为 192.168.1.100
python drone_simulator.py --backend-url "http://192.168.1.100:8080/api/v1"
python existing_drone_simulator.py --backend-url "http://192.168.1.100:8080" --mqtt-host "192.168.1.100"
```

## 故障排除

### 连接问题

```bash
# 检查服务状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend

# 测试连接
curl http://localhost:8080/actuator/health
```

### 无人机注册失败

1. 确保后端服务正在运行
2. 检查数据库连接
3. 查看后端日志了解具体错误

### MQTT连接失败

1. 确保EMQX服务正在运行
2. 检查1883端口是否被占用
3. 查看EMQX日志：`docker-compose logs -f emqx`

## 注意事项

- 确保所有Docker服务正常运行后再启动模拟器
- 模拟器会在沈阳市区内随机生成初始位置
- 默认情况下，端口绑定到所有接口(`0.0.0.0`)，允许同一网络的外部设备访问
- 生产环境中应该配置适当的防火墙规则和访问控制 