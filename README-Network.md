# 🌐 智能网络配置系统

本系统提供了一套完整的解决方案，实现网络切换时的透明化配置更新，让您无需手动修改配置即可在不同网络环境中正常使用无人机管理系统。

## 📋 功能特性

- ✅ **自动IP检测**：智能检测当前活跃的网络IP地址
- ✅ **配置同步**：自动更新前端和后端的网络配置
- ✅ **CORS管理**：动态更新跨域资源共享配置
- ✅ **服务管理**：可选择性重启前端/后端服务
- ✅ **状态监控**：实时检查系统运行状态
- ✅ **一键启动**：全自动系统启动流程

## 🚀 快速开始

### 方式一：完全自动启动
```bash
# 一键启动整个系统（推荐）
./start-system.sh
```

### 方式二：手动更新配置
```bash
# 仅更新配置（不重启服务）
./update-network-config.sh

# 强制更新配置（即使IP未变化）
./update-network-config.sh --force

# 自动模式（更新配置并重启服务）
./update-network-config.sh --auto
```

## 📁 脚本说明

### 核心脚本

| 脚本名称 | 功能描述 | 使用场景 |
|---------|---------|---------|
| `start-system.sh` | 智能启动整个系统 | 首次启动或完整重启 |
| `update-network-config.sh` | 网络配置更新 | 网络切换后更新配置 |
| `stop-system.sh` | 停止所有服务 | 系统维护或关闭 |
| `restart-system.sh` | 重启系统 | 快速重启 |
| `check-status.sh` | 状态检查 | 健康监控 |

### 参数说明

#### `update-network-config.sh` 参数
- `--auto` / `-a`：自动模式，无需确认直接重启服务
- `--force` / `-f`：强制更新配置（即使IP未变化）

## 🔧 工作原理

### 1. IP地址检测
脚本使用多种方法检测当前活跃的IP地址：
1. **路由表分析**：通过系统路由表找到默认网关对应的接口IP
2. **外部服务**：通过网络服务获取公网IP（备用）
3. **网络接口**：直接扫描系统网络接口
4. **连接分析**：分析活跃网络连接的本地IP

### 2. 配置更新
自动更新以下配置文件：
- `backend/src/main/resources/application-external.yml`
  - 更新CORS allowed-origins
  - 更新base-url
- `vue-vben-admin/apps/web-antd/.env.production`
  - 更新API_URL
  - 更新WebSocket URL
- `vue-vben-admin/apps/web-antd/.env.development`

### 3. 服务管理
- 智能检测服务运行状态
- 优雅停止现有服务
- 按需重启前端/后端服务
- 等待服务启动完成

## 🎯 使用场景

### 场景1：日常网络切换
当您在家庭、办公室、咖啡厅等不同网络环境间切换时：
```bash
./update-network-config.sh --auto
```

### 场景2：系统初次部署
在新环境中首次部署系统：
```bash
./start-system.sh
```

### 场景3：开发调试
开发过程中需要测试不同网络配置：
```bash
./update-network-config.sh --force
```

### 场景4：系统维护
需要停止和重启系统：
```bash
./stop-system.sh      # 停止
./restart-system.sh   # 重启
./check-status.sh     # 检查状态
```

## 📊 状态监控

### 实时检查系统状态
```bash
./check-status.sh
```

### 查看系统日志
```bash
# 后端日志
tail -f backend.log

# 前端日志
tail -f frontend.log
```

### 手动测试接口
```bash
# 检查后端状态
curl http://当前IP:8080/api/status

# 测试登录接口
curl -X POST http://当前IP:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "vben", "password": "123456"}'
```

## 🔧 配置文件

### 网络配置缓存
系统会自动创建 `.network-config` 文件来缓存网络配置：
```bash
# 网络配置缓存文件
LAST_IP="192.168.2.171"
LAST_UPDATE="Tue May 28 15:07:59 CST 2025"
```

### 进程ID文件
运行时会创建进程ID文件：
- `.backend.pid`：后端服务进程ID
- `.frontend.pid`：前端服务进程ID

## 🚨 故障排除

### 常见问题

#### 1. IP检测失败
**现象**：脚本无法检测到有效IP地址
**解决**：
```bash
# 手动检查网络接口
ifconfig
# 或检查路由表
netstat -rn
```

#### 2. 端口占用
**现象**：启动时提示端口被占用
**解决**：
```bash
# 检查端口占用
lsof -i :8080
lsof -i :5666

# 强制停止相关进程
./stop-system.sh
```

#### 3. 服务启动超时
**现象**：服务启动超时
**解决**：
```bash
# 查看详细日志
tail -f backend.log
tail -f frontend.log

# 手动启动调试
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=external
```

#### 4. CORS错误持续存在
**现象**：更新配置后仍有跨域错误
**解决**：
```bash
# 强制更新配置并重启
./update-network-config.sh --force
# 然后手动重启服务
./restart-system.sh
```

### 日志分析

#### 后端日志关键信息
```bash
# 查看CORS配置加载
grep -i "cors" backend.log

# 查看服务启动信息
grep -i "started" backend.log
```

#### 前端日志关键信息
```bash
# 查看编译和启动信息
grep -E "(ready|error)" frontend.log
```

## 💡 最佳实践

### 1. 定期备份配置
```bash
# 备份重要配置文件
cp backend/src/main/resources/application-external.yml app-config-backup.yml
```

### 2. 监控网络变化
可以创建定时任务定期检查网络变化：
```bash
# 添加到crontab（每5分钟检查一次）
*/5 * * * * cd /path/to/drone9 && ./update-network-config.sh
```

### 3. 开发环境优化
开发时可以使用固定IP或localhost来避免频繁更新：
```bash
# 开发模式使用localhost
VITE_API_URL=http://localhost:8080
```

## 🎯 高级配置

### 自定义端口
如需使用自定义端口，请修改脚本中的常量：
```bash
# 在脚本开头修改
BACKEND_PORT=8080
FRONTEND_PORT=5666
```

### 自定义IP检测
如果标准IP检测方法不适用，可以自定义检测逻辑：
```bash
# 在get_current_ip函数中添加自定义方法
custom_ip=$(your_custom_ip_detection_method)
```

---

## 📞 技术支持

如果遇到问题，请按以下步骤排查：

1. 运行状态检查：`./check-status.sh`
2. 查看详细日志：`tail -f backend.log frontend.log`
3. 强制更新配置：`./update-network-config.sh --force`
4. 完全重启系统：`./restart-system.sh`

**享受无缝的网络切换体验！** 🎉 