# Spring Boot + Vue3 用户认证系统

> 前后端分离的完整用户认证系统 | 2026 

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![CI](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml/badge.svg)](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml)

---

## 项目简介

一套**生产级**前后端分离的用户注册、登录、鉴权系统，涵盖从图形验证码、邮箱验证、JWT 双 Token、暴力破解防护到真实微信 OAuth 扫码登录的完整认证闭环。适合作为 Spring Boot + Vue3 全栈学习项目的基础框架。

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus 3.5.12 + MySQL + Redis + Spring Security + JJWT 0.12 + JavaMail
- **前端**：Vue 3.5 + Vite 8 + Element Plus 2.14 + Vue Router 4 + Axios + qrcode

---

## 核心功能

| 功能 | 状态 | 说明 |
|------|:----:|------|
| 图形验证码 | ✅ | Java AWT 绘制，6位大写字母 + 干扰线 + 噪点 + 字符旋转，Redis 2min TTL |
| 邮箱验证码注册 | ✅ | 163邮箱 SMTP，6位数字验证码，Redis 5min TTL，60秒防刷 |
| 密码登录 | ✅ | BCrypt 加密 + 图形验证码 + 5次失败锁定15分钟 + 统一错误提示防用户枚举 |
| 双 Token 鉴权 | ✅ | accessToken (30min) + refreshToken (7天)，JWT `type` claim 强制类型校验 |
| 无感刷新 | ✅ | Axios 响应拦截器 + 请求队列，并发 401 只刷新一次 |
| 单设备登录 | ✅ | Redis 缓存双 Token，新登录自动踢掉旧设备 |
| 暴力破解防护 | ✅ | Redis INCR 原子计数，TTL 仅首次设置防绕过，HTTP 429 响应 |
| 微信扫码登录 | ✅ | 真实 OAuth 2.0（测试号），已绑定用户扫码直接登录，新用户扫码后邮箱绑定 |
| 微信账号关联 | ✅ | 支持已绑定直接登录 / 新注册 / 已有账号绑定 / 已绑定拒绝四种场景 |
| 邮箱找回密码 | ✅ | 邮箱验证码 + 重置后强制所有设备重新登录 |
| 角色权限控制（RBAC） | ✅ | 自定义 @RequireRole 注解 + RoleInterceptor，user/admin 双角色，JWT role claim |

---

## 项目结构

```
├── springboot/                          # 后端 Spring Boot 工程
│   ├── pom.xml                          # Maven 配置
│   ├── Dockerfile                       # 多阶段构建（Maven → JRE）
│   ├── .dockerignore                    # Docker 构建排除
│   └── src/main/
│       ├── java/com/zora/
│       │   ├── AppStart.java            # 启动类
│       │   ├── controller/              # 控制器（11 个 REST 端点）
│       │   │   └── UserController.java
│       │   ├── service/                 # 业务逻辑接口 + 实现
│       │   │   ├── UserService.java
│       │   │   └── impl/UserServiceImpl.java
│       │   ├── mapper/                  # MyBatis-Plus Mapper
│       │   │   └── UserMapper.java
│       │   ├── entity/                  # 实体类
│       │   │   └── User.java            # id, email, password, openid, nickname, avatar
│       │   ├── config/                  # 配置类
│       │   │   ├── SecurityConfig.java  # Spring Security（仅 BCrypt）
│       │   │   ├── WebConfig.java       # 拦截器注册 + CORS
│       │   │   ├── LoginInterceptor.java # 自定义 Token 校验
│       │   │   ├── MyConfig.java        # MyBatis-Plus 分页插件
│       │   │   └── WechatConfig.java    # 微信 OAuth 配置
│       │   └── utils/                   # 工具类
│       │       ├── JwtUtil.java         # JWT 生成/解析/类型校验
│       │       ├── CaptchaUtil.java     # 图形验证码生成（AWT）
│       │       ├── EmailUtil.java       # 163邮箱 SMTP 异步发送
│       │       ├── WechatUtil.java      # 微信 API 调用（code→token→用户信息）
│       │       └── ResponseUtil.java    # 统一响应体
│       └── resources/
│           ├── application.yml          # 应用配置（含微信凭证）
│           └── init.sql                 # 数据库初始化
│   └── src/test/                        # 单元测试（JUnit 5 + Mockito + MockMvc）
│       ├── java/com/zora/
│       │   ├── utils/                   # ResponseUtil / CaptchaUtil / JwtUtil（纯 JUnit 5）
│       │   ├── service/                 # UserServiceImpl（Mockito + @InjectMocks）
│       │   ├── config/                  # LoginInterceptor / RoleInterceptor（Mockito）
│       │   ├── controller/              # UserController（Standalone MockMvc）
│       │   └── exception/               # GlobalExceptionHandler（MockMvc + 内嵌 Controller）
│       └── resources/
│           └── application.yml          # 测试环境配置（H2 内存数据库）
├── web/frontend/                        # 前端 Vue 3 + Vite 工程
│   ├── Dockerfile                        # 多阶段构建（Node → Nginx）
│   ├── .dockerignore                     # Docker 构建排除
│   ├── nginx.conf                        # Nginx 配置（SPA + API 代理）
│   ├── vite.config.js                    # Vite 配置（代理 + 按需导入）
│   ├── package.json                     # 依赖清单
│   └── src/
│       ├── main.js                      # 入口
│       ├── App.vue                      # 根组件
│       ├── style.css                    # 全局样式 + 响应式
│       ├── views/                       # 页面组件
│       │   ├── Login.vue                # 登录页（密码 + 微信双Tab）
│       │   ├── Register.vue             # 注册页
│       │   └── Home.vue                 # 首页（鉴权通过展示）
│       ├── api/                         # API 封装
│       │   ├── index.js                 # Axios 实例 + 拦截器（自动刷新）
│       │   └── user.js                  # 用户接口（12 个 API 函数）
│       ├── router/                      # 路由
│       │   └── index.js                 # 路由配置 + 导航守卫
│       └── utils/                       # 工具
│           └── token.js                 # localStorage Token 存取
├── docker-compose.yml                     # Docker 容器编排（5 服务一键启动）
├── .env                                    # Docker 环境变量（端口、密码、密钥）
├── DB_MIGRATION.sql                        # 数据库迁移脚本（微信字段）
├── P0_SECURITY_FIX.md                   # P0 安全修复文档
├── WECHAT_SETUP_GUIDE.md                # 微信扫码登录完整配置指南
├── 项目构建教程.md                       # 28 步详细构建教程（含设计原理）
└── README.md                            # 本文件
```

---

## 快速开始

### 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | Spring Boot 3.x 运行环境 |
| MySQL | 8.x | 用户数据持久化 |
| Redis | 7.x | Token 缓存 + 验证码 + 登录失败计数 + 微信状态 |
| Node.js | 18+ | 前端构建 |
| Docker | — | 一键启动全部服务（可选，替代 JDK/MySQL/Redis/Node 手动安装） |
| 163邮箱 | — | 发送注册/绑定验证码（免费，需开启 SMTP） |
| 微信测试号 | — | 微信扫码登录（免费，无需企业资质） |
| ngrok | — | 本地开发微信回调公网穿透（免费） |

### 1. 克隆项目

```bash
git clone https://github.com/ZoraCloudSun/springboot_vue3
cd your_project_name
```

### 2. 一键启动（Docker Compose，推荐）

```bash
# 构建并启动所有服务（MySQL + Redis + Spring Boot + Nginx）
docker compose up -d --build

# 查看服务状态
docker compose ps

# 访问
# 前端:      http://localhost
# API 文档:   http://localhost/doc.html
# 健康检查:   http://localhost:18080/actuator/health

# 可选：数据库管理界面
docker compose --profile debug up -d     # Adminer → http://localhost:18081

# 停止
docker compose down
```

> 首次构建需要下载 Docker 镜像和 Maven 依赖，约 3-5 分钟。后续启动只需 `docker compose up -d`，秒级完成。
> 详细配置（端口、密码、JWT 密钥等）见 `.env` 文件。如果不需要 Docker，也可以手动启动。

### 3. 手动启动（不使用 Docker）

> 如果你不使用 Docker，请按以下步骤手动启动各个服务。

#### 初始化数据库

```bash
# 创建数据库和基础表
mysql -u root -p < springboot/src/main/resources/init.sql

# 执行微信功能迁移（添加 openid/nickname/avatar 字段）
mysql -u root -p springboot_zyt < DB_MIGRATION.sql
```

#### 配置邮箱

编辑 `springboot/src/main/resources/application.yml`，填入你的 163 邮箱和 SMTP 授权码：

```yaml
spring:
  mail:
    username: your_email@163.com
    password: your_smtp_auth_code  # 非登录密码！
```

> 📖 获取授权码：登录 [mail.163.com](https://mail.163.com) → 设置 → POP3/SMTP/IMAP → 开启 SMTP → 获取授权码

#### （可选）配置微信扫码登录

详见 [WECHAT_SETUP_GUIDE.md](WECHAT_SETUP_GUIDE.md)。如果暂不需要微信登录，跳过此步骤不影响密码登录功能。

#### 启动后端

```bash
cd springboot
mvn spring-boot:run
# → http://localhost:8080

# 运行单元测试（171 个测试，~10 秒）
mvn test
```

#### 启动前端

```bash
cd web/frontend
npm install
npm run dev
# → http://localhost:3000
```

#### 设置管理员（RBAC）

```bash
# 注册一个账号后，用 MySQL 手动将其提升为管理员
mysql -u root -p springboot_zyt -e "UPDATE user SET role = 'admin' WHERE email = 'your_admin@example.com';"
```

#### 开始使用

1. 浏览器访问 `http://localhost:3000`
2. 点击底部「注册账号」→ 输入邮箱 → 发送验证码 → 注册
3. 返回登录页 → 输入邮箱 + 密码 + 图形验证码 → 登录
4. 或切换到「微信登录」Tab → 扫码 → 已绑定直接登录 / 新用户绑定邮箱 → 自动登录
5. 管理员用户登录后，首页会显示「管理后台」入口，可查看所有注册用户

---

## API 概览

### 基础认证

| 方法 | 路径 | 认证 | 说明 |
|------|------|:----:|------|
| GET | `/user/captcha` | 否 | 获取图形验证码 |
| POST | `/user/send-code` | 否 | 发送邮箱验证码（注册用，已注册邮箱拒绝） |
| POST | `/user/send-bind-code` | 否 | 发送邮箱验证码（微信绑定用，允许已注册邮箱） |
| POST | `/user/register` | 否 | 邮箱验证码注册 |
| POST | `/user/login` | 否 | 登录（图形验证码 + 5次失败锁定15分钟） |
| POST | `/user/logout` | accessToken | 登出（清除 Redis Token） |
| POST | `/user/refresh` | refreshToken | 刷新 accessToken（强制 type=refresh） |
| GET | `/user/info` | accessToken | 鉴权探针 |

### 微信扫码登录

| 方法 | 路径 | 认证 | 说明 |
|------|------|:----:|------|
| POST | `/user/wechat/qrcode` | 否 | 生成扫码场景，返回 sceneId + 真实微信 OAuth URL |
| GET | `/user/wechat/check` | 否 | 轮询扫码状态：pending / scanned / confirmed / expired |
| GET | `/user/wechat/callback` | 否 | 微信 OAuth 回调（返回 HTML，手机浏览器访问） |
| POST | `/user/wechat/bind-email` | 否 | 扫码后绑定邮箱，创建/关联用户 + 签发 JWT |

### 邮箱找回密码

| 方法 | 路径 | 认证 | 说明 |
|------|------|:----:|------|
| POST | `/user/forgot-password/send-code` | 否 | 发送密码重置验证码（仅已注册邮箱） |
| POST | `/user/forgot-password/reset` | 否 | 验证码校验 + 重置密码 + 踢掉所有设备 |

### RBAC 角色权限

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/user/me` | accessToken | 获取当前登录用户信息（含角色） |
| GET | `/user/admin/users` | accessToken + admin | 管理员分页查询所有用户（不含密码） |

---

## 架构设计

### 鉴权链路

```
浏览器 → Vue Router（前端路由守卫）
       → Axios 请求拦截器（自动注入 accessToken）
       → HTTP 请求 → LoginInterceptor（后端拦截器）
         ├─ 提取 Authorization 头
         ├─ JWT 签名 + 过期校验
         ├─ Redis 比对（单设备登录）
         └─ 放行/拒绝
       → Controller → Service → Mapper → DB
```

### 双 Token 自动刷新

```
accessToken 过期（30min）
  → 后端 401 + "Token 已失效"
  → Axios 响应拦截器
    ├─ isRefreshing = true
    ├─ POST /user/refresh（携带 refreshToken，校验 type=refresh）
    ├─ 获取新 accessToken → 更新 localStorage
    ├─ 重放 pendingRequests 队列中的所有请求
    └─ 重试原请求
  → 用户完全无感知

refreshToken 也过期（7天）
  → 清除 Token → 跳转登录页
```

### 微信 OAuth 流程

```
                      ┌── openid 已绑定（老用户）
                      │   直接签发 Token → confirmed → 自动登录
                      │   用户零操作，秒进
PC 浏览器   手机微信   后端
  │           │        │
  │ POST /wechat/qrcode
  │ ← sceneId + OAuth URL
  │ 生成二维码 │        │
  │ 开始轮询   │        │
  │           │ 微信扫码 │
  │           │ 确认授权 │
  │           │ ───→ GET /wechat/callback
  │           │      code → openid
  │           │      查 DB: openid?
  │           │      │
  │           │      ├── 已绑定 → confirmed → 直接登录 ✅
  │           │      │
  │ 轮询 confirmed       │
  │ ← Token ────────────│
  │ 自动登录 → /home     │
  │           │      │
  │           │      └── 未绑定 → scanned → 等待邮箱绑定
  │ 轮询 scanned         │
  │ 展示头像+绑定表单     │
  │ POST /wechat/bind-email
  │ ← JWT Token ──────│── 创建/关联用户 → confirmed
  │ 自动登录 → /home     │
```

---

### 全局异常处理

采用 Spring MVC 标准 `@RestControllerAdvice` 统一捕获所有异常，替代 Service 层手动 `return new ResponseUtil(400, ...)` 的旧模式。

**异常层次结构**：

```
RuntimeException
  └── BusinessException（基类，携带 code + msg + data）
        ├── BadRequestException     → 400 参数校验失败
        ├── UnauthorizedException   → 401 未认证
        ├── ForbiddenException      → 403 权限不足
        ├── NotFoundException       → 404 资源不存在
        └── RateLimitException      → 429 频率限制
```

**错误数据流**：

```
Service 层:
  throw new BadRequestException("邮箱不能为空")
       │
       ▼
GlobalExceptionHandler:
  @ExceptionHandler(BusinessException.class)
  → ResponseEntity.ok(body)   ← HTTP 200，body.code = 400
       │
       ▼
Axios 成功拦截器:
  if (res.code !== 200) → ElMessage.error("邮箱不能为空")
```

**关键设计**：
- 只有 401（`UnauthorizedException`）返回真实 HTTP 401，因为前端 Axios 错误拦截器靠 `response.status === 401` 触发 Token 自动刷新
- 其他业务异常统一返回 HTTP 200，body.code 为实际错误码，由 Axios 成功拦截器统一提取 `response.data.msg` 显示
- 拦截器（LoginInterceptor / RoleInterceptor）在 Controller 之前执行，不经过 `@ControllerAdvice`——它们使用 `ResponseUtil.toJsonError()` 安全写入 JSON
- 兜底 `@ExceptionHandler(Exception.class)` 记录完整堆栈到日志，客户端只看到通用 "服务器内部错误"

---

### Knife4j 接口文档

基于 OpenAPI 3 + Knife4j 4.x 自动生成在线调试文档，访问 `http://localhost:8080/doc.html`。

**技术组合**：`knife4j-openapi3-jakarta-spring-boot-starter` 4.5.0（Spring Boot 3.x Jakarta 版本）

**四大功能分组**（通过 `@Tag` 注解实现）：

| 分组 | 端点 | 说明 |
|------|:----:|------|
| 基础认证 | 7 | 验证码、注册、登录、登出、Token 刷新、鉴权探针 |
| 微信扫码登录 | 5 | 生成二维码、轮询状态、回调（隐藏）、邮箱绑定、发送验证码 |
| 邮箱找回密码 | 2 | 发送重置码、重置密码 |
| RBAC 角色权限 | 2 | 管理员用户列表、当前用户信息 |

**全局 Authorize 机制**：右上角填入 accessToken → 所有接口自动携带 `Authorization` header → 在线调试无需每个接口重复填写。使用 `SecurityScheme.Type.APIKEY` 而非 `HTTP (Bearer)`，因为本项目 Token 不需要 "Bearer " 前缀。

**离线文档导出**：Knife4j UI → 文档管理 → 导出 Markdown / HTML / OpenAPI JSON，可提交到项目 Wiki。

---

## 安全特性

| 层级 | 机制 | 说明 |
|------|------|------|
| 传输层 | HTTPS（生产环境） | ngrok 开发环境自动提供 HTTPS |
| 认证层 | JWT HMAC-SHA256 | 签名防篡改，30min 短时效降低泄露风险 |
| 类型校验 | JWT `type` claim | `/refresh` 强制校验 `type=refresh`，防 accessToken 无限续期 |
| 密码存储 | BCrypt | 自适应哈希，自带随机盐，抗彩虹表 |
| 防刷 | 图形验证码 + 60秒间隔 | 发送验证码前校验图形验证码，同邮箱 60 秒内不可重复发送 |
| 防暴力破解 | Redis INCR + HTTP 429 | 5 次失败锁定 15 分钟，TTL 仅首次设置防绕过 |
| 防用户枚举 | 统一错误提示 | 所有登录失败统一返回「邮箱或密码错误」 |
| 单设备登录 | Redis Token 缓存 | 新登录自动踢掉旧设备 |
| CSRF | 显式禁用 + JWT 无状态 | JWT 不依赖 Cookie，天然免疫 CSRF |
| OAuth 安全 | state 参数（sceneId） | 防 CSRF 攻击，回调时校验 |

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [项目构建教程.md](项目构建教程.md) | **28 步完整分步构建教程**，涵盖每个设计决策的原理和选型理由 |
| [WECHAT_SETUP_GUIDE.md](WECHAT_SETUP_GUIDE.md) | 微信扫码登录完整配置指南：测试号获取→ngrok→架构图→测试→FAQ |
| [P0_SECURITY_FIX.md](P0_SECURITY_FIX.md) | P0 安全修复记录：Token 类型校验 + 暴力破解防护 |
| [DB_MIGRATION.sql](DB_MIGRATION.sql) | 数据库迁移脚本：添加微信字段 |
| [APITest.http](springboot/APITest.http) | HTTP Client 测试文件，覆盖全部 16 个端点的 40+ 个场景（含自动 Token 捕获） |

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.11 | 基础框架 |
| MyBatis-Plus | 3.5.12 | ORM（BaseMapper 免写 SQL + 分页插件） |
| MySQL | 8.x | 持久化存储 |
| Redis | 7.x | Token 缓存 + 验证码 + 登录失败计数 + 微信状态 |
| Spring Security | 6.x | BCrypt 密码加密 |
| jjwt | 0.12.6 | JWT 生成/解析/类型校验 |
| JavaMail | 3.5.11 | 163 邮箱 SMTP 验证码发送 |
| Java AWT | JDK 内置 | 图形验证码绘制 |
| Jackson | 2.x | JSON 序列化（微信 API 响应解析） |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 前端框架（Composition API） |
| Vite | 8.x | 构建工具（Rolldown 生产构建） |
| Element Plus | 2.14 | UI 组件库（按需导入） |
| Vue Router | 4.x | 前端路由 + 导航守卫 |
| Axios | 1.16 | HTTP 客户端 + 拦截器 |
| qrcode | 1.5 | 前端 Canvas 二维码生成 |

---

## 后续规划

- [x] 邮箱找回密码
- [x] 角色权限控制（RBAC）
- [x] 全局异常处理（`@ControllerAdvice`）
- [x] Swagger / Knife4j 接口文档
- [x] 单元测试（JUnit 5 + MockMvc）— 171 个测试，8 个测试类，覆盖全链路
- [x] Docker 容器化（docker-compose 一键启动）
- [x] GitHub Actions CI/CD

---

## License

MIT
