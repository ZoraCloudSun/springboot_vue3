# Windows 保留端口导致 Spring Boot 8080 启动失败

## 问题概述

**日期**: 2026-06-19
**影响**: 本地开发环境下 Spring Boot 应用无法在端口 8080 启动，应用日志报告 "Port 8080 was already in use"

## 错误症状

### 应用层表现

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Web server failed to start. Port 8080 was already in use.

Action:

Identify and stop the process that's listening on port 8080 or configure this application to listen on another port.
```

### 完整异常栈（DEBUG 级别可见）

```
Caused by: java.net.BindException: Address already in use: bind
    at java.base/sun.nio.ch.Net.bind0(Native Method)
    at java.base/sun.nio.ch.Net.bind(Net.java:511)
    at java.base/sun.nio.ch.ServerSocketChannelImpl.netBind(...)
    at java.base/sun.nio.ch.ServerSocketChannelImpl.bind(...)
    at org.apache.tomcat.util.net.NioEndpoint.initServerSocket(NioEndpoint.java:264)
    at org.apache.tomcat.util.net.NioEndpoint.bind(NioEndpoint.java:219)
    ...
    at org.apache.catalina.connector.Connector.startInternal(Connector.java:1112)
```

### 关键特征

- `netstat -ano | findstr :8080` **查不到任何进程**占用该端口
- 应用换到 8081、8082 等端口同样失败
- 换到远离 8080 的端口（如 1514、9090）可以正常启动
- 无论 JDK 21 还是 JDK 24 都会出现

## 根因分析

### 第一层：数据库连接误导（干扰排查）

最初的应用崩溃并非端口问题，而是 **连错了 MySQL 实例**：

| 配置项 | 本地 MySQL（错误目标） | Docker MySQL（正确目标） |
|--------|----------------------|------------------------|
| 端口 | `localhost:3306` | `localhost:13307` |
| 用户/密码 | `root / 123456` | `springuser / springpass` |
| RAG 表 | ❌ 不存在 | ✅ 存在 |

`application.yml` 的默认值 `${MYSQL_PORT:3306}` 在没有设置 `MYSQL_PORT` 环境变量时回退到 3306，恰好对上了本地另一个老 MySQL 实例——它能连接成功，但 `kb_document` 等 RAG 表不存在，导致 `RagProcessingServiceImpl.@PostConstruct rebuildEmbeddingStore()` 执行失败，应用退出。

**修复方式**：本地开发时必须设置环境变量指向 Docker 基础设施：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=13307
export MYSQL_USERNAME=springuser
export MYSQL_PASSWORD=springpass
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

VSCode 用户：`.vscode/launch.json` 中已配置 `"envFile": "${workspaceFolder}/.env"`，使用 F5 或 Spring Boot Dashboard 启动即可自动加载 `.env` 中的环境变量。**直接在终端运行 `mvn spring-boot:run` 不会加载 `.env`。**

### 第二层：Windows 端口保留（真正的障碍）

修正数据库连接后，应用启动时遭遇真正的端口绑定失败。

Windows 系统会为某些服务（主要是 **WinNAT**，用于 Docker/Hyper-V 网络地址转换）**预留端口范围**。通过以下命令查看：

```cmd
netsh interface ipv4 show excludedportrange protocol=tcp
```

本次案例中的输出：

```
开始端口    结束端口
----------    --------
     8057        8156    ← 8080 落在此范围内！
      ...         ...
```

**8080（以及 8081-8156）被 Windows 系统级保留**，任何应用程序（包括 JVM）的 `bind()` 调用都会直接返回 `BindException`，就像端口真的被占用了一样——但在 `netstat` 中完全看不到任何进程持有该端口。

### WinNAT 为什么保留这些端口

WinNAT（Windows Network Address Translation）是 Docker Desktop 和 Hyper-V 使用的网络组件。当它启动时，会从系统的动态端口范围中随机划出一段作为 NAT 转换使用。常见的触发条件：

1. **Docker Desktop 启动/重启**后 WinNAT 重新分配了端口范围
2. **Windows Update** 后系统重启，WinNAT 自动启动并抢占端口
3. **Hyper-V** 虚拟机创建或网络配置变更

### 为什么昨天正常今天不行

最可能的原因：
- 昨天 Docker/WinNAT 保留的端口范围碰巧不包含 8080
- 中间经历了系统重启、Docker 重启或 Windows Update，WinNAT 重新分配保留范围，**这次恰好覆盖了 8080**

WinNAT 保留哪些端口是**不确定的、随机的**，每次重启都可能变化。

## 诊断步骤（快速手册）

若遇到 "Port XXXX was already in use" 但 `netstat` 查不到进程，按以下步骤排查：

```cmd
# 步骤 1：确认端口是否被 Windows 系统保留
netsh interface ipv4 show excludedportrange protocol=tcp

# 步骤 2：查看目标端口是否在输出列表的任一区间内
# 示例：8080 在 8057-8156 区间内 → 被 Windows 保留

# 步骤 3：确认非传统端口占用
netstat -ano | findstr :8080
# 如果无输出 → 进一步确认是保留端口问题
```

## 解决方案

### 方案一：重启 WinNAT 服务（推荐，立即生效）

以**管理员身份**打开 CMD 或 PowerShell，执行：

```cmd
net stop winnat
net start winnat
```

这会强制 WinNAT 释放当前保留的端口范围并重新分配。新的保留范围大概率不会包含 8080。

> **注意**：这会短暂中断 Docker 容器的网络连接，重新分配后会自动恢复。

### 方案二：排除特定端口（精确控制）

如果反复遇到 8080 被保留，可以主动将 8080 从动态端口范围中排除：

```cmd
# 查看当前动态端口范围
netsh int ipv4 show dynamicport tcp

# 将起始端口设置到 8080 之后（例如 9000）
# 这样 Windows 只会从 9000+ 分配保留端口
netsh int ipv4 set dynamicport tcp start=9000 num=10000
```

> **风险提示**：修改动态端口范围是系统级操作，建议仅在开发机上使用。

### 方案三：更换应用端口（临时规避）

在确认是 Windows 保留端口问题后，如果不想修改系统配置，可以临时换一个端口：

```yaml
# application.yml
server:
  port: 9090   # 避开 8057-8156 等保留区间
```

或启动时指定：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

### 方案四：重启系统（终极方案）

重启 Windows 会让 WinNAT 重新初始化，大概率解决问题。缺点是需要重新启动所有开发环境。

## 涉及的 Windows 保留端口区间（本次案例）

| 起始端口 | 结束端口 | 备注 |
|---------|---------|------|
| 3581 | 3680 | |
| 7657 | 7756 | |
| 7757 | 7856 | |
| 7857 | 7956 | |
| 7957 | 8056 | |
| **8057** | **8156** | ← 8080 在此区间内 |
| 8157 | 8256 | |
| 8257 | 8356 | |
| 8357 | 8456 | |
| 9843 | 9942 | |
| 50000 | 50059 | 管理端口排除（带 * 标记） |

## 环境变量速查表

| 变量名 | 本地开发所需值 | 说明 |
|--------|-------------|------|
| `MYSQL_HOST` | `localhost` | Docker MySQL 在宿主机 |
| `MYSQL_PORT` | `13307` | .env 中配置的映射端口 |
| `MYSQL_USERNAME` | `springuser` | Docker MySQL 用户 |
| `MYSQL_PASSWORD` | `springpass` | Docker MySQL 密码 |
| `REDIS_HOST` | `localhost` | Docker Redis 在宿主机 |
| `REDIS_PORT` | `6379` | Redis 默认端口 |

## 预防措施

1. **使用 VSCode F5/Spring Boot Dashboard 启动**（不要终端直接 `mvn spring-boot:run`），它会自动加载 `.env` 环境变量
2. **固定 WinNAT 端口范围**：执行方案二，将动态端口起点设置到 9000 以上，永绝后患
3. **遇到类似问题先查保留端口**：养成 `netsh int ipv4 show excludedportrange tcp` 条件反射
