# 多阶段构建 Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 复制 Maven 配置文件和源代码
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src src

# 打包应用，跳过测试以加快构建速度
RUN ./mvnw clean package -DskipTests

# 运行阶段 - 使用更兼容的镜像
FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装wget用于健康检查
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 从构建阶段复制jar文件
COPY --from=build /app/target/*.jar app.jar

# 更改文件所有者
RUN chown appuser:appgroup app.jar

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"] 