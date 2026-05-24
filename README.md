# Spring Boot + Vue3 用户认证系统

## 项目简介

前后端分离的用户注册、登录、鉴权系统，支持邮箱验证码注册和 JWT 双 Token 无感刷新。

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus + MySQL + Redis + Spring Security + JJWT + JavaMail
- **前端**：Vue 3 + Vite + Element Plus + Vue Router + Axios

## 核心功能

| 功能 | 说明 |
|------|------|
| 邮箱验证码注册 | 163邮箱 SMTP 发送6位验证码，5分钟有效，60秒防刷 |
| 双 Token 鉴权 | accessToken（30min）+ refreshToken（7天），自动无感刷新 |
| 单设备登录 | Redis 缓存 Token，同一账号新登录踢掉旧设备 |
| BCrypt 密码加密 | 每次加密结果不同，抗彩虹表攻击 |
| 微信扫码登录 | 预留 UI 入口（选项卡 + 图标），后续接入 OAuth 2.0 |

## 项目结构

```
├── springboot/                  # 后端 Spring Boot 工程
│   ├── src/main/java/com/zyt/
│   │   ├── AppStart.java        # 启动类
│   │   ├── controller/          # 控制器层
│   │   ├── service/             # 业务逻辑层
│   │   ├── mapper/              # 数据访问层
│   │   ├── entity/              # 实体类
│   │   ├── config/              # 配置类（安全、拦截器、CORS）
│   │   └── utils/               # 工具类（JWT、邮件、响应）
│   └── src/main/resources/
│       ├── application.yml      # 应用配置
│       └── init.sql             # 数据库初始化脚本
├── web/frontend/                # 前端 Vue 3 + Vite 工程
│   └── src/
│       ├── views/               # 页面组件（Login、Register、Home）
│       ├── api/                 # Axios 封装 + 拦截器（自动刷新）
│       ├── router/              # 路由 + 导航守卫
│       └── utils/               # Token 管理工具
└── 项目构建教程.md               # 详细构建教程（28步完整文档）
```

## 快速启动

### 环境要求

- JDK 21+
- MySQL 8.x
- Redis 7.x
- Node.js 18+
- 163邮箱账号（需开启 SMTP 服务并获取授权码）

### 1. 初始化数据库

执行 `springboot/src/main/resources/init.sql`

### 2. 配置邮箱

编辑 `springboot/src/main/resources/application.yml`：

```yaml
spring:
  mail:
    username: your_email@163.com   # 替换为你的163邮箱
    password: your_smtp_auth_code  # 替换为SMTP授权码（非登录密码）
```

> 获取163授权码：登录 mail.163.com → 设置 → POP3/SMTP/IMAP → 开启SMTP服务

### 3. 启动后端

```bash
cd springboot
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`

### 4. 启动前端

```bash
cd web/frontend
npm install
npm run dev
```

前端运行在 `http://localhost:3000`

### 5. 测试

打开浏览器访问 `http://localhost:3000`，使用邮箱注册账号。

## API 接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/user/send-code` | 否 | 发送邮箱验证码 |
| POST | `/user/register` | 否 | 邮箱验证码注册 |
| POST | `/user/login` | 否 | 登录（返回双 Token） |
| POST | `/user/refresh` | 否 | 刷新 accessToken |
| POST | `/user/logout` | accessToken | 登出 |
| GET | `/user/info` | accessToken | 鉴权探针 |

## 技术亮点

### 双 Token 自动刷新

```
accessToken 过期（30min）
  → 后端 401
  → Axios 拦截器自动用 refreshToken 换取新 accessToken
  → 重试原请求
  → 用户无感知

refreshToken 也过期（7天）
  → 跳转登录页
```

多请求并发 401 时使用请求队列（`isRefreshing` 锁 + `pendingRequests` 队列），确保只刷新一次。

### 邮箱验证码注册流程

```
输入邮箱 → 点击"发送验证码"
  → 后端生成6位随机码 → 存入 Redis（5min TTL）
  → 163邮箱 SMTP 发送 → 用户收到邮件
  → 输入验证码 + 密码 → 提交注册
  → 后端从 Redis 比对验证码 → 创建用户 → 注册成功
```

### 单设备登录

```
Redis key: token:{email} = accessToken (30min TTL)
Redis key: refresh_token:{email} = refreshToken (7天 TTL)

新登录 → 删除旧 key → 生成新双 Token → 旧设备请求 → Redis 无匹配 → 401
```

## 详细文档

完整的分步构建教程（含设计原理和选型理由）请参阅 [项目构建教程.md](项目构建教程.md)
