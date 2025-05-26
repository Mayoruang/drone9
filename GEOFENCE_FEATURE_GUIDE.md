# 无人机地理围栏权限管理功能

## 功能概述

本功能为无人机详情页面添加了完整的地理围栏权限管理系统，允许管理员查看、分配和管理无人机的地理围栏权限。

## 实现的功能

### 1. 后端API扩展

#### 控制器 (`DroneController`)
- `GET /api/v1/drones/{droneId}/geofences` - 获取无人机关联的地理围栏
- `GET /api/v1/drones/{droneId}/geofences/available` - 获取可分配的地理围栏列表
- `POST /api/v1/drones/{droneId}/geofences` - 为无人机分配地理围栏权限
- `DELETE /api/v1/drones/{droneId}/geofences/{geofenceId}` - 取消单个地理围栏权限
- `PUT /api/v1/drones/{droneId}/geofences` - 批量更新地理围栏权限

#### 服务层 (`DroneStatusService`)
扩展了无人机状态服务，添加了以下方法：
- `getDroneGeofences()` - 获取无人机的地理围栏
- `getAvailableGeofences()` - 获取可用地理围栏
- `assignGeofences()` - 分配地理围栏
- `unassignGeofence()` - 取消分配
- `updateGeofenceAssignments()` - 批量更新

#### 数据库
- 使用现有的 `drone_geofence` 多对多关联表
- 支持地理围栏类型过滤（禁飞区、允飞区、限飞区）
- 支持活跃状态过滤

### 2. 前端界面实现

#### 无人机详情页面 - 地理围栏标签页
- **已分配地理围栏显示**
  - 地理围栏名称、类型、状态
  - 优先级、面积、高度限制信息
  - 移除权限操作

- **可分配地理围栏选择**
  - 类型筛选（禁飞区/允飞区/限飞区）
  - 活跃状态筛选
  - 多选功能
  - 批量分配操作

- **交互功能**
  - 实时数据刷新
  - 响应式UI设计
  - 操作结果通知
  - 加载状态提示

#### API客户端 (`drone.ts`)
添加了完整的地理围栏相关API调用方法：
```typescript
- getDroneGeofences(droneId)
- getAvailableGeofences(droneId, type?, active?)
- assignGeofences(droneId, geofenceIds)
- unassignGeofence(droneId, geofenceId)
- updateGeofenceAssignments(droneId, geofenceIds)
```

## 数据流

1. **用户打开无人机详情页面**
2. **切换到地理围栏标签页**
3. **自动加载已分配和可用的地理围栏**
4. **用户选择要分配的地理围栏**
5. **点击分配按钮，调用后端API**
6. **后端更新数据库关联关系**
7. **前端刷新显示最新状态**

## 技术实现要点

### 响应式数据管理
- 使用Vue 3 Composition API管理状态
- 自动监听无人机选择变化
- 计算属性过滤已分配的地理围栏

### 事务管理
- 后端使用`@Transactional`确保数据一致性
- 多对多关系的正确维护
- 错误处理和回滚机制

### 用户体验优化
- 加载状态提示
- 操作成功/失败通知
- 批量操作支持
- 响应式界面设计

## 使用方法

1. 启动后端服务：
   ```bash
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. 启动前端应用：
   ```bash
   cd vue-vben-admin
   npm run dev
   ```

3. 访问无人机状态监控页面
4. 点击任意无人机打开详情抽屉
5. 切换到"地理围栏权限"标签页
6. 查看和管理地理围栏权限

## 数据库结构

### 相关表
- `geofences` - 地理围栏基础信息
- `drones` - 无人机信息
- `drone_geofence` - 关联关系表

### 支持的地理围栏类型
- `NO_FLY_ZONE` - 禁飞区
- `FLY_ZONE` - 允飞区
- `RESTRICTED_ZONE` - 限飞区

## 错误处理

- API调用失败时显示错误通知
- 无效数据输入验证
- 网络错误重试机制
- 用户友好的错误消息

## 未来扩展

- 地理围栏地图可视化
- 权限历史记录
- 批量导入/导出功能
- 权限模板管理
- 实时违规监控 