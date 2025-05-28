# 故障排除指南

## 问题1: Docker镜像构建失败

### 症状
```
ERROR: failed to solve: eclipse-temurin:17-jre-alpine: no match for platform in manifest
```

### 原因
在Apple Silicon (ARM64) macOS上，`eclipse-temurin:17-jre-alpine` 镜像可能不兼容。

### 解决方案
修改 `backend/Dockerfile`，使用更兼容的基础镜像：

```dockerfile
# 原来的配置
FROM eclipse-temurin:17-jre-alpine

# 修改为
FROM eclipse-temurin:17-jre
```

## 问题2: Spring Boot应用启动失败

### 症状
```
Error creating bean with name 'registrationServiceImpl': 
Unsatisfied dependency expressed through constructor parameter 2: 
No qualifying bean of type 'org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder' available
```

### 原因
`RegistrationServiceImpl` 类的构造函数期望具体的 `BCryptPasswordEncoder` 类型，但Spring配置中定义的是 `PasswordEncoder` 接口类型的bean。

### 解决方案
修改 `RegistrationServiceImpl.java`：

```java
// 原来的代码
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
private final BCryptPasswordEncoder passwordEncoder;

// 修改为
import org.springframework.security.crypto.password.PasswordEncoder;
private final PasswordEncoder passwordEncoder;
```

## 问题3: 前端无法连接后端

### 症状
```
AxiosError: Network Error
Failed to load resource: net::ERR_CONNECTION_REFUSED
```

### 原因
后端服务没有正常启动，导致8080端口无法访问。

### 解决方案
1. 确保数据库服务正在运行：
   ```bash
   docker-compose up -d postgres influxdb emqx
   ```

2. 构建并启动后端应用：
   ```bash
   cd backend
   mvn clean package -DskipTests
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

3. 验证服务状态：
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## 快速启动指南

### 方法1: 使用Docker Compose（推荐）
```bash
# 修复Dockerfile后
./start-services.sh
```

### 方法2: 混合模式（数据库用Docker，后端用Java）
```bash
# 启动数据库服务
docker-compose up -d postgres influxdb emqx

# 构建并启动后端
cd backend
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 方法3: 使用本地构建脚本
```bash
./start-services-local.sh
```

## 验证服务状态

### 检查后端健康状态
```bash
curl http://localhost:8080/actuator/health
```

### 检查无人机API
```bash
curl http://localhost:8080/api/v1/drones
```

### 检查Docker服务
```bash
docker-compose ps
```

## 常见端口和服务

- **后端API**: http://localhost:8080
- **PostgreSQL**: localhost:5432
- **InfluxDB**: http://localhost:8086
- **EMQX Dashboard**: http://localhost:18083 (admin/public)
- **MQTT Broker**: localhost:1883

## 日志查看

### 后端日志（Docker模式）
```bash
docker-compose logs -f backend
```

### 后端日志（Java模式）
直接在终端查看输出

### 数据库日志
```bash
docker-compose logs -f postgres
```

## 清理和重置

### 停止所有服务
```bash
docker-compose down
```

### 清理Docker资源
```bash
docker-compose down --volumes --remove-orphans
docker system prune -f
```

### 重新构建
```bash
docker-compose build --no-cache
``` 