# MQTT功能测试指南

## 功能概述
已完成从前端到后端的MQTT消息发送功能实现，支持：

### 1. 控制台消息 (Console Messages)
- **用途**: 发送简单的控制台消息给无人机
- **特性**: 
  - 优先级设置 (LOW/NORMAL/HIGH)
  - 可选择是否需要确认 (requireAck)
  - 消息长度限制: 1000字符
  - **专用主题**: `drones/{droneId}/console`（区别于通用指令主题）

### 2. 自定义MQTT消息 (Custom MQTT Messages)
- **用途**: 发送到自定义MQTT主题的消息
- **特性**:
  - 自定义主题路径
  - QoS级别选择 (0/1/2)
  - 保留消息选项
  - 消息类型选择 (COMMAND/NOTIFICATION/CUSTOM)
  - 消息长度限制: 2000字符

## ✅ 已解决的问题

### 1. API路径重复问题
- **问题**: 前端API调用中出现重复的`/api`路径，导致请求URL变成`/api/api/v1/...`
- **解决方案**: 修复了前端API路径配置
- **状态**: ✅ 已解决

### 2. 控制台消息主题问题
- **问题**: 控制台消息使用通用的`/commands`主题，不够清晰
- **解决方案**: 为控制台消息创建专用主题`/console`
- **修改前**: `drones/{droneId}/commands`（与通用指令混在一起）
- **修改后**: `drones/{droneId}/console`（专用控制台主题）
- **状态**: ✅ 已解决

### 3. 前端错误处理问题
- **问题**: 成功的API响应被前端当作错误处理，显示"发送MQTT消息失败"
- **原因**: 后端返回了错误的HTTP状态码（400），但响应体中success字段为true
- **解决方案**: 修改控制器返回逻辑，确保HTTP状态码与响应内容一致
  - 成功时返回200状态码
  - 失败时返回500状态码
- **状态**: ✅ 已解决

### 4. 权限认证问题 ⭐ **最终解决**
- **问题**: MQTT API端点返回"Access Denied"错误
- **根本原因**: JWT过滤器配置问题和Spring Security配置冲突
- **详细分析**:
  1. **JWT过滤器排除路径过于宽泛**: `/api/v1/drones/**`被完全排除，导致MQTT端点不进行JWT验证
  2. **安全配置冲突**: 同时使用了`permitAll()`和`@PreAuthorize`注解，导致权限检查失效
  3. **角色名称不匹配**: 数据库中角色为`admin`，但Spring Security需要`ROLE_ADMIN`格式

- **解决方案**:
  1. **修改JWT过滤器**: 移除对所有无人机API的排除，只保留特定不需要认证的端点
  2. **修正安全配置**: 让MQTT端点正确进行认证和授权
  3. **使用正确的角色名称**: `@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER')")`
  4. **确保JWT令牌正确解析**: 修复令牌验证逻辑

- **最终配置**:
  ```java
  // JWT过滤器排除路径（移除了/api/v1/drones/**）
  "/api/auth/**", "/api/test/**", "/api/status", 
  "/api/v1/drones/register", "/api/v1/drones/registration/**", 
  "/api/v1/drones/management/**", "/api/v1/drones/test/**", 
  "/api/v1/geofences/**", "/actuator/**"
  
  // 安全配置
  .requestMatchers("/api/v1/drones/*/console-message").authenticated()
  .requestMatchers("/api/v1/drones/*/mqtt-message").authenticated()
  .requestMatchers("/api/v1/drones/**").permitAll()
  
  // 控制器权限检查
  @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER')")
  ```

- **状态**: ✅ **完全解决**

### 5. WebSocket连接问题 ⭐ **新解决**
- **问题**: 前端WebSocket连接失败，错误代码1002，原因"Cannot connect to server"
- **症状**: 
  - 403 Forbidden错误访问WebSocket资源
  - WebSocket连接失败，无法接收实时数据
- **根本原因**: Spring Security配置中没有允许WebSocket端点访问
- **解决方案**: 在安全配置中添加WebSocket端点的访问权限
  ```java
  .requestMatchers("/ws/**").permitAll()
  .requestMatchers("/websocket-test").permitAll()
  .requestMatchers("/mqtt-test").permitAll()
  ```
- **验证结果**: 
  - ✅ WebSocket端点现在返回"Welcome to SockJS!"
  - ✅ 前端可以正常建立WebSocket连接
  - ✅ 实时数据推送功能恢复正常
- **状态**: ✅ **完全解决**

## 🧪 API测试结果

### 控制台消息API测试
```bash
curl -X POST http://localhost:8080/api/v1/drones/{droneId}/console-message \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -d '{"message":"test console message","priority":"NORMAL","requireAck":false}'
```

**成功响应**:
```json
{
  "success": true,
  "message": "Console message sent successfully",
  "droneId": "9ff622db-4b8f-4724-99c6-ae70be9c313c",
  "topic": "drones/9ff622db-4b8f-4724-99c6-ae70be9c313c/console",
  "timestamp": "2025-05-27T20:03:01.854339+08:00",
  "messageId": "196f28ba-8be6-4a7e-8d1c-dacccd7da2a9",
  "errorDetails": null
}
```

### 自定义MQTT消息API测试
```bash
curl -X POST http://localhost:8080/api/v1/drones/{droneId}/mqtt-message \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -d '{"topic":"drones/test/custom","message":"test custom mqtt message","qos":1,"retained":false,"messageType":"CUSTOM"}'
```

**成功响应**:
```json
{
  "success": true,
  "message": "MQTT message sent successfully",
  "droneId": "9ff622db-4b8f-4724-99c6-ae70be9c313c",
  "topic": "drones/test/custom",
  "timestamp": "2025-05-27T20:01:37.537617+08:00",
  "messageId": "346549ee-71a6-4a4b-9ab6-b74ea2ec7af0",
  "errorDetails": null
}
```

## 📋 使用说明

### 前端使用步骤
1. **登录系统**: 使用具有ADMIN或SUPER角色的用户账号登录
   - 默认管理员账号: `admin` / `123456`
   - 默认超级用户: `vben` / `123456`

2. **选择无人机**: 在无人机管理界面选择目标无人机

3. **发送消息**: 
   - **控制台消息**: 用于简单的指令发送，使用专用控制台主题
   - **自定义MQTT消息**: 用于高级用户，可自定义主题和参数

### 权限要求
- **必需角色**: ADMIN 或 SUPER
- **认证方式**: JWT Bearer Token
- **令牌获取**: 通过`/api/auth/login`端点登录获取

### 故障排除
1. **403 Forbidden错误**: 检查用户是否已登录且具有正确权限
2. **401 Unauthorized错误**: 检查JWT令牌是否有效
3. **500 Internal Server Error**: 检查后端日志，可能是MQTT服务连接问题

## 📊 功能状态总结

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 控制台消息发送 | ✅ 完成 | 使用专用主题`/console` |
| 自定义MQTT消息 | ✅ 完成 | 支持自定义主题和参数 |
| 前端API集成 | ✅ 完成 | 正确的错误处理和状态码 |
| 权限认证 | ✅ 完成 | JWT认证 + 角色授权 |
| 错误处理 | ✅ 完成 | 统一的错误响应格式 |
| WebSocket连接 | ✅ 完成 | 实时数据推送和通信 |

**🎉 所有MQTT消息发送功能和WebSocket实时通信已完全实现并测试通过！** 