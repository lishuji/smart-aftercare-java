# ==================== 构建阶段 ====================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建必要目录
RUN mkdir -p /app/uploads /app/logs /app/config

# 复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 复制前端静态文件（如果存在于 static/ 中，已嵌入 jar）
# 复制数据库初始化脚本
COPY db/ /app/db/

# 暴露端口
EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8000/api/health || exit 1

# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
