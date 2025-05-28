# 🚀 增强版智能启动系统

本文档详细介绍了改进后的无人机管理系统智能启动器，现已完全支持Docker服务管理和网络自适应配置。

## 📋 系统概览

### 完整架构
```
🌐 无人机管理系统
├── 🐳 Docker基础服务层
│   ├── PostgreSQL (端口 5432) - 主数据库
│   ├── InfluxDB (端口 8086) - 时序数据库
│   └── EMQX (端口 1883/18083) - MQTT消息代理
├── 🚀 应用服务层
│   ├── Spring Boot 后端 (端口 8080)
│   └── Vue.js 前端 (端口 5666)
└── ⚙️ 管理脚本层
    ├── 启动管理
    ├── 状态监控
    └── 网络配置
```

## 🔧 核心改进

### 1. **Docker服务集成** 🐳
- ✅ 自动检测和启动Docker基础服务
- ✅ 健康状态监控
- ✅ 端口映射管理
- ✅ 服务依赖检查

### 2. **超时保护机制** ⏱️
- ✅ 网络配置更新超时保护 (30秒)
- ✅ 服务启动超时保护 (后端60秒，前端60秒)
- ✅ 智能fallback机制

### 3. **全面状态监控** 📊
- ✅ Docker服务状态检查
- ✅ 数据库连接测试
- ✅ MQTT连接验证
- ✅ 应用健康检查

## 🚀 启动流程详解

### Phase 1: 环境检查 🔍
```bash
🚀 无人机管理系统智能启动器
=============================
[INFO] 检查系统依赖...
```

**检查项目**：
- Docker & Docker Compose
- Java 环境
- Node.js & pnpm
- curl 工具

**验证文件**：
- `docker-compose.yml`
- `backend/mvnw`
- `vue-vben-admin/package.json`
- `backend/src/main/resources/application-external.yml`
- `update-network-config.sh`

### Phase 2: Docker服务管理 🐳
```bash
[INFO] 检查Docker服务状态...
[INFO] 启动Docker基础服务...
```

**服务启动顺序**：
1. **PostgreSQL** - 主数据库服务
2. **InfluxDB** - 时序数据存储
3. **EMQX** - MQTT消息代理

**端口暴露**：
- PostgreSQL: `5432`
- InfluxDB: `8086` (管理界面)
- EMQX MQTT: `1883` (协议端口)
- EMQX Dashboard: `18083` (管理界面，admin/public)

### Phase 3: 网络配置 🌐
```bash
[SYSTEM] 更新网络配置...
[INFO] 更新网络配置...
```

**智能IP检测**：
- 路由表分析
- 外部服务查询
- 网络接口扫描
- 活跃连接分析

**配置更新**：
- 后端CORS设置
- 前端API地址
- WebSocket连接地址

**超时保护**：
- 30秒超时限制
- Fallback到localhost

### Phase 4: 应用启动 🚀
```bash
[SYSTEM] 启动系统服务...
[INFO] 启动后端服务...
[INFO] 启动前端服务...
```

**后端启动**：
```bash
cd backend
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=external > ../backend.log 2>&1 &
```

**前端启动**：
```bash
cd vue-vben-admin/apps/web-antd
nohup pnpm dev > ../../../frontend.log 2>&1 &
```

**健康检查**：
- 后端：`/api/status` 接口
- 前端：HTTP响应测试

### Phase 5: 管理脚本创建 📝
自动创建管理脚本：
- `stop-system.sh` - 系统停止
- `restart-system.sh` - 系统重启
- `check-status.sh` - 状态检查

## 🛠️ 管理脚本详解

### `start-system.sh` - 智能启动器
```bash
./start-system.sh
```
**功能**：完整的系统启动流程
**特性**：
- 环境检查和依赖验证
- Docker服务自动管理
- 网络配置自动更新
- 应用服务启动和健康检查

### `stop-system.sh` - 智能停止器
```bash
# 交互式停止
./stop-system.sh

# 停止所有服务（包含Docker）
./stop-system.sh --docker

# 仅停止应用服务
./stop-system.sh --app-only
```

### `check-status.sh` - 全面状态检查
```bash
./check-status.sh
```
**检查内容**：
- 🐳 Docker服务状态
- 🚀 应用服务状态
- 💾 数据库连接状态
- ⚙️ 运行进程信息
- 🔌 端口占用情况

### `update-network-config.sh` - 网络配置管理
```bash
# 检测并更新配置
./update-network-config.sh

# 强制更新
./update-network-config.sh --force

# 自动模式（无需确认）
./update-network-config.sh --auto
```

## 🌐 服务访问信息

### 核心服务
| 服务 | 端口 | 访问地址 | 说明 |
|------|------|----------|------|
| 前端界面 | 5666 | http://IP:5666 | 主应用界面 |
| 后端API | 8080 | http://IP:8080 | REST API服务 |
| API文档 | 8080 | http://IP:8080/swagger-ui.html | Swagger文档 |

### Docker服务
| 服务 | 端口 | 访问地址 | 认证信息 |
|------|------|----------|----------|
| PostgreSQL | 5432 | tcp://IP:5432 | drone/dronepassword |
| InfluxDB | 8086 | http://IP:8086 | admin/influxdb123 |
| EMQX Dashboard | 18083 | http://IP:18083 | admin/public |
| MQTT Broker | 1883 | tcp://IP:1883 | - |

## 📋 日志管理

### 应用日志
```bash
# 后端日志
tail -f backend.log

# 前端日志
tail -f frontend.log
```

### Docker日志
```bash
# 所有Docker服务日志
docker-compose logs -f

# 特定服务日志
docker-compose logs -f postgres
docker-compose logs -f influxdb
docker-compose logs -f emqx
```

## 🚨 故障排除

### 常见问题与解决方案

#### 1. 启动卡在"更新网络配置"
**原因**：网络检测脚本执行时间过长
**解决**：已添加30秒超时保护，自动fallback到localhost

#### 2. Docker服务未启动
**检查**：
```bash
docker info  # 检查Docker是否运行
docker-compose ps  # 检查服务状态
```
**解决**：
```bash
docker-compose up -d  # 启动所有服务
```

#### 3. 端口冲突
**检查**：
```bash
lsof -i :端口号  # 检查端口占用
```
**解决**：
```bash
./stop-system.sh --docker  # 完全停止再重启
```

#### 4. 数据库连接失败
**检查**：
```bash
./check-status.sh  # 检查所有服务状态
```
**解决**：
```bash
docker-compose restart postgres  # 重启数据库
```

## 🎯 最佳实践

### 1. 日常使用
```bash
# 启动系统
./start-system.sh

# 检查状态
./check-status.sh

# 停止系统
./stop-system.sh
```

### 2. 网络切换
```bash
# 自动更新网络配置
./update-network-config.sh --auto
```

### 3. 开发调试
```bash
# 仅停止应用服务（保留Docker服务）
./stop-system.sh --app-only

# 重启应用服务
./restart-system.sh
```

### 4. 完全重置
```bash
# 停止所有服务
./stop-system.sh --docker

# 重新启动
./start-system.sh
```

## 🔧 自定义配置

### 端口配置
如需修改端口，请更新以下文件：
- `docker-compose.yml` - Docker服务端口
- `start-system.sh` - 脚本中的端口常量
- `application-external.yml` - 后端配置

### 网络配置
系统会自动检测网络变化，也可手动配置：
- `.network-config` - 网络配置缓存
- `.env.production` - 前端生产环境配置
- `.env.development` - 前端开发环境配置

## 🎉 总结

增强版启动系统提供了：
- 🚀 **一键启动**：完整的系统自动化启动
- 🐳 **Docker集成**：自动管理所有基础服务
- 🌐 **网络自适应**：智能检测和配置更新
- 📊 **全面监控**：完整的状态检查和健康监控
- 🛡️ **故障保护**：超时保护和错误处理
- 🔧 **灵活管理**：多种启停模式和参数选项

**享受无缝的开发和部署体验！** 🎯 