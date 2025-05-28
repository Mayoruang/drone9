# Docker容器化部署总结

本文档总结了将无人机管理系统后端打包为Docker容器的完整配置。

## 🔧 配置文件修改

### 1. Docker配置

#### `backend/Dockerfile` (新增)
- 多阶段构建，优化镜像大小
- 使用非root用户运行，提高安全性
- 内置健康检查
- JVM参数优化

#### `docker-compose.yml` (修改)
- 启用backend服务
- 配置端口绑定 `0.0.0.0:8080:8080` 允许外部访问
- 添加环境变量配置
- 配置服务依赖关系
- 添加健康检查

#### `backend/src/main/resources/application-production.yml` (新增)
- 生产环境专用配置
- 服务器监听所有接口 `address: 0.0.0.0`
- 数据库连接池优化
- 日志配置优化
- JPA性能调优

### 2. 启动脚本

#### `start-services.sh` (新增)
- 自动构建和启动所有服务
- 服务状态检查
- 完整的使用说明

#### `test-drone-system.sh` (新增)
- 交互式测试脚本
- 服务连通性检查
- 无人机注册和模拟器管理

### 3. 模拟器增强

#### `simulator/drone_simulator.py` (修改)
- 添加自动服务检测功能
- 支持多种后端地址
- 改进错误处理

#### `simulator/existing_drone_simulator.py` (修改)
- 自动检测后端和MQTT服务
- 支持手动指定服务地址
- 增强网络连接检测

## 🌐 网络配置

### 端口映射
```yaml
backend:    0.0.0.0:8080:8080   # 后端API
postgres:   5432:5432           # PostgreSQL
influxdb:   8086:8086           # InfluxDB
emqx:       1883:1883           # MQTT
            18083:18083         # EMQX Dashboard
```

### 外部访问支持
- 绑定到所有网络接口 (`0.0.0.0`)
- 支持同一网络的外部主机访问
- 自动服务发现和连接

## 🚀 部署流程

### 快速启动
```bash
# 1. 启动所有服务
./start-services.sh

# 2. 运行交互式测试
./test-drone-system.sh
```

### 手动启动
```bash
# 1. 构建并启动服务
docker-compose up -d

# 2. 查看服务状态
docker-compose ps

# 3. 查看日志
docker-compose logs -f backend

# 4. 测试连接
curl http://localhost:8080/actuator/health
```

## 📱 无人机操作

### 注册新无人机
```bash
cd simulator
python drone_simulator.py
```

### 启动模拟器
```bash
# 查看可用无人机
python existing_drone_simulator.py --list

# 启动指定无人机
python existing_drone_simulator.py --drone-id <无人机ID>
```

### 外部主机访问
```bash
# 假设主机IP为 192.168.1.100
python drone_simulator.py --backend-url "http://192.168.1.100:8080/api/v1"
python existing_drone_simulator.py --backend-url "http://192.168.1.100:8080" --mqtt-host "192.168.1.100"
```

## 🔍 服务端点

### 核心服务
- **后端API**: http://localhost:8080
- **健康检查**: http://localhost:8080/actuator/health
- **MQTT Broker**: localhost:1883
- **EMQX Dashboard**: http://localhost:18083 (admin/public)

### 数据库
- **PostgreSQL**: localhost:5432
- **InfluxDB**: http://localhost:8086

## 🛡️ 安全配置

### 生产环境建议
1. 修改端口绑定为 `127.0.0.1:8080:8080` (仅本地访问)
2. 配置反向代理 (Nginx)
3. 启用HTTPS
4. 配置防火墙规则
5. 设置CORS白名单

### 开发环境
- 当前配置适合开发和测试
- 允许同一网络的设备访问
- 支持无人机远程注册

## 📊 功能特性

### Docker容器化
- [x] 多阶段构建优化
- [x] 非root用户运行
- [x] 健康检查配置
- [x] 环境变量配置
- [x] 数据持久化

### 网络功能
- [x] 外部主机访问支持
- [x] 自动服务发现
- [x] 端口绑定配置
- [x] CORS配置

### 模拟器增强
- [x] 自动服务检测
- [x] 连接失败重试
- [x] 多地址支持
- [x] 改进的错误处理

### 运维工具
- [x] 启动脚本
- [x] 测试脚本
- [x] 服务监控
- [x] 日志配置

## 🔧 故障排除

### 常见问题
1. **连接被拒绝**: 检查防火墙设置
2. **服务启动失败**: 查看 `docker-compose logs`
3. **端口冲突**: 修改docker-compose.yml中的端口映射
4. **权限问题**: 确保Docker有足够权限

### 调试命令
```bash
# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend

# 测试连接
curl -v http://localhost:8080/actuator/health

# 检查端口占用
netstat -tlnp | grep 8080

# 进入容器调试
docker-compose exec backend bash
```

## 📝 文档结构

```
drone9/
├── docker-compose.yml           # Docker编排配置
├── start-services.sh            # 服务启动脚本
├── test-drone-system.sh         # 测试脚本
├── NETWORK_CONFIG.md            # 网络配置说明
├── DOCKER_DEPLOYMENT.md         # 本文档
├── backend/
│   ├── Dockerfile               # 生产环境Dockerfile
│   ├── Dockerfile.dev           # 开发环境Dockerfile
│   └── src/main/resources/
│       └── application-production.yml  # 生产环境配置
└── simulator/
    ├── README.md                # 更新的使用说明
    ├── drone_simulator.py       # 增强的注册脚本
    └── existing_drone_simulator.py  # 增强的模拟器
```

## ✅ 验收标准

- [x] 后端服务可通过Docker容器启动
- [x] 同一网络的外部主机可访问后端API
- [x] 无人机可从外部主机注册
- [x] 模拟器支持自动服务发现
- [x] 提供完整的部署和使用文档
- [x] 支持生产和开发环境配置
- [x] 包含完整的故障排除指南

系统现在已经完全容器化，支持外部访问，并提供了完整的工具链用于无人机注册和管理。 