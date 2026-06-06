# ===== 阶段 1：构建 Spring Boot JAR =====
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# 先复制 POM，下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建（跳过测试以加快构建速度）
COPY src ./src
RUN mvn package -DskipTests -B

# 找到 Spring Boot fat JAR（排除 .jar.original 文件），重命名为可预测的文件名
RUN find target -maxdepth 1 -name "*.jar" ! -name "*.original" -exec mv {} app.jar \;

# ===== 阶段 2：最小 JRE 运行环境 =====
FROM eclipse-temurin:21-jre-alpine

# 安装 curl（健康检查用）
RUN apk add --no-cache curl

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app
COPY --from=build /build/app.jar .

# 确保 appuser 拥有 app 目录
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Docker 健康检查：每 30s 检查一次，启动 60s 后开始，连续 3 次失败则判定为 unhealthy
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
