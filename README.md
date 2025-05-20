# Drone9 - Drone Management System

A comprehensive management system for drone registration, approval, and monitoring.

## Project Structure

- **Backend**: Spring Boot application with REST API endpoints for drone registration and management
  - Controllers, services, and repositories for handling drone data
  - WebSocket integration for real-time updates
  - JWT authentication for secure access

- **Frontend**: Vue.js application built on vue-vben-admin framework
  - Registration management interface with statistics and filtering
  - Approval/rejection functionality with detailed information
  - Real-time updates via WebSocket

## Features

- **Drone Registration Flow**: Complete workflow for drone registration, approval, and management
- **Real-time Updates**: WebSocket integration for immediate status changes
- **System Monitoring**: Dashboard for monitoring system components and overall health
- **User Authentication**: Secure login and role-based access control

## Technologies

- **Backend**: Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, WebSocket
- **Frontend**: Vue.js, Ant Design, WebSocket
- **Infrastructure**: Docker services for PostgreSQL, InfluxDB, and EMQX

## Getting Started

1. Clone the repository
2. Run the backend Spring Boot application
3. Start the frontend Vue.js application
4. Access the system at http://localhost:3100

## License

This project is proprietary software. 

# 无人机系统数据链路测试模拟器

这是一个用于测试无人机管理系统数据流的模拟器。它可以模拟多台无人机设备，并完成以下流程：

1. 向后端系统注册无人机
2. 等待管理员审批
3. 获取MQTT凭证
4. 通过MQTT连接到EMQX消息代理
5. 持续发送遥测数据
6. 响应从系统发来的命令

## 系统架构

系统由以下部分组成：

- **Spring Boot后端**：处理无人机注册、管理、命令发送等业务逻辑
- **PostgreSQL数据库**：存储无人机注册信息、状态等结构化数据
- **InfluxDB数据库**：存储无人机遥测时序数据
- **EMQX消息代理**：处理无人机和后端系统之间的MQTT通信
- **Vue前端**：可视化显示无人机状态和位置信息
- **本模拟器**：模拟多个无人机设备的行为

## 运行说明

### 1. 启动支持服务

使用Docker Compose启动PostgreSQL、InfluxDB和EMQX：

```bash
cd /Users/mayorhuang/abc/abc1/drone9
docker-compose up -d postgres influxdb emqx
```

### 2. 启动后端服务

后端可以直接本地运行（不使用Docker）：

```bash
cd /Users/mayorhuang/abc/abc1/drone9/backend
./mvnw spring-boot:run
```

### 3. 启动前端服务

前端也直接本地运行：

```bash
cd /Users/mayorhuang/abc/abc1/drone9/vue-vben-admin/apps/web-antd
npm run dev
```

### 4. 运行模拟器

模拟器需要安装Python 3.7+和必要的依赖：

```bash
# 安装依赖
pip install requests paho-mqtt

# 运行模拟器
python drone9_simulator.py
```

## 模拟器参数

模拟器支持以下命令行参数：

```
--drones N        模拟的无人机数量，默认为5
--backend URL     后端API基础URL，默认为http://localhost:8080/api/v1
--mqtt-host HOST  MQTT代理主机名，默认为localhost
--mqtt-port PORT  MQTT代理端口，默认为1883
--log-level LEVEL 日志级别(DEBUG, INFO, WARNING, ERROR)，默认为INFO
```

示例：

```bash
# 模拟10台无人机，使用详细日志
python drone9_simulator.py --drones 10 --log-level DEBUG
```

## 使用流程

1. 启动支持服务（PostgreSQL、InfluxDB、EMQX）
2. 启动后端服务
3. 启动前端服务
4. 运行模拟器
5. 登录到前端系统管理界面（通常在http://localhost:5666）
6. 在管理后台批准无人机注册请求
7. 观察前端地图上无人机的实时位置和状态更新

## 注意事项

1. 确保所有服务的端口没有被其他应用占用
2. 模拟器需要管理员手动批准无人机注册请求
3. 如果模拟器长时间等待批准但无人机不出现，检查管理后台是否已批准注册请求
4. 可以使用`Ctrl+C`终止模拟器运行

## 数据流说明

1. 模拟器通过API向后端注册无人机
2. 后端将注册信息存入PostgreSQL
3. 管理员通过前端批准注册请求
4. 模拟器获取MQTT连接凭证
5. 模拟器连接到EMQX并开始发送遥测数据
6. 后端通过MQTT接收遥测数据并存入InfluxDB
7. 前端获取实时数据并在地图上显示无人机位置和状态
8. 前端可以向无人机发送命令（如返航、降落等）
9. 无人机（模拟器）执行命令并更新状态

## 故障排除

- **无法注册无人机**：检查后端服务是否正常运行，网络连接是否正常
- **无法连接MQTT**：检查EMQX服务是否正常运行，凭证是否正确
- **地图上看不到无人机**：确认注册请求已被批准，检查遥测数据是否正常发送
- **遥测数据未存储**：检查InfluxDB连接配置是否正确

## 进一步开发

如需改进模拟器，可以：

1. 添加更多的无人机状态和行为模式
2. 实现地理围栏功能的响应
3. 模拟更多传感器数据
4. 添加自定义飞行路径
5. 实现更复杂的无人机群控制 