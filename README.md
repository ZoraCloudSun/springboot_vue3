# Spring Boot + Vue3 全栈项目：认证系统 + AI 智能对话 + RAG 知识库

> 前后端分离的全栈项目 | 2026

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED)](https://www.docker.com/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.15.0-00B265)](https://docs.langchain4j.dev/)
[![DeepSeek](https://img.shields.io/badge/DeepSeek-V3-536DFE)](https://platform.deepseek.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![CI](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml/badge.svg)](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml)

---

## 项目简介

一套**生产级**前后端分离的全栈项目，集成了完整的用户认证闭环和基于大模型的 AI 智能对话系统，并支持 RAG（检索增强生成）知识库。

| 系统 | 说明 |
|------|------|
| 🔐 **用户认证系统** | 图形验证码、邮箱验证码注册、JWT 双 Token 鉴权、微信 OAuth 扫码登录、RBAC 角色权限控制、暴力破解防护 |
| 🤖 **AI 智能对话** | DeepSeek-V3 大模型驱动、SSE 流式传输（打字机效果）、多轮上下文对话、Markdown + 代码高亮渲染、对话历史管理 |
| 📚 **RAG 知识库** | 文档上传 → 文本提取 → 分块 → Embedding 向量化 → 检索增强生成，让 AI 基于用户自己的文档回答 |

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus 3.5.12 + MySQL + Redis + LangChain4j 1.15.0 + Apache Tika + JJWT 0.12
- **前端**：Vue 3.5 + Vite 8 + Element Plus 2.14 + Vue Router 4 + Axios + marked + highlight.js + DOMPurify

---

## 核心功能

### 用户认证

| 功能 | 说明 |
|------|------|
| 图形验证码 | Java AWT 绘制，6位大写字母 + 干扰线 + 噪点，Redis TTL 2min |
| 邮箱验证码注册 | 163邮箱 SMTP，6位数字验证码，60秒防刷 |
| 密码登录 | BCrypt 加密 + 5次失败锁定15分钟 + 统一错误提示防用户枚举 |
| 双 Token 鉴权 | accessToken (30min) + refreshToken (7天)，JWT `type` claim 强制类型校验 |
| 无感刷新 | Axios 响应拦截器 + 请求队列，并发 401 只刷新一次 |
| 单设备登录 | Redis 缓存 Token，新登录自动踢掉旧设备 |
| 微信扫码登录 | 真实 OAuth 2.0，已绑定用户扫一扫直接登录，新用户扫码后邮箱绑定 |
| 邮箱找回密码 | 验证码校验 + 重置后强制所有设备重新登录 |
| RBAC 角色权限 | 自定义 `@RequireRole` 注解 + RoleInterceptor，user/admin 双角色 |

### AI 智能对话

| 功能 | 说明 |
|------|------|
| 🤖 大模型驱动 | DeepSeek-V3，中英文对话、代码生成、数据分析 |
| 📡 SSE 流式传输 | 逐字输出，打字机效果，120秒超时支持长回复 |
| 📝 Markdown 渲染 | 标题、列表、表格、引用、代码块（190+ 语言语法高亮） |
| 🔒 XSS 安全过滤 | DOMPurify 过滤 AI 输出中的潜在恶意 HTML/JS |
| 💬 多轮上下文 | 自动携带最近 20 条历史消息，支持连续对话 |
| 📂 对话管理 | 新建、切换、删除、回收站恢复、永久删除，自动生成标题 |
| 🛑 中途停止 | 随时停止 AI 生成，已输出内容保留 |

### RAG 知识库

| 功能 | 说明 |
|------|------|
| 📄 文档解析 | Apache Tika 提取 PDF/DOCX/DOC/TXT/MD |
| ✂️ 智能分块 | 递归分割，800字符/块，100字符重叠 |
| 🔢 向量嵌入 | OpenAI 兼容 Embedding API（支持 OpenAI / 硅基流动 / Ollama） |
| 🔍 向量检索 | 余弦相似度检索，内存向量库 + MySQL 持久化 |
| 🔄 启动重建 | 应用重启自动从 MySQL 重建向量索引 |
| 🗂️ 知识库管理 | 卡片列表、文档表格、处理状态轮询、检索测试面板 |
| ♻️ 两级回收站 | 知识库级回收站（全局）+ 文档级回收站（按知识库），支持恢复（自动重嵌入向量）和永久删除（清理文件/向量/DB） |

---

## 快速开始

### 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | Spring Boot 3.x |
| MySQL | 8.x | 数据持久化 |
| Redis | 7.x | Token 缓存 + 验证码 + 向量状态 |
| Node.js | 18+ | 前端构建 |
| Docker | — | 一键启动（可选，替代手动安装） |
| DeepSeek API Key | — | AI 对话（新用户赠送 500 万 token） |
| Embedding API Key | — | RAG 向量嵌入（硅基流动推荐，国内直连） |
| 163邮箱 | — | 发送验证码（可选，需开启 SMTP） |
| 微信测试号 | — | 微信扫码登录（可选） |

### 1. 克隆项目

```bash
git clone https://github.com/zoracloudsun/springboot_vue3
cd springboot_vue3
```

### 2. 配置环境变量

编辑项目根目录的 `.env` 文件：

```env
# ===== MySQL =====
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=springboot_zyt

# ===== JWT =====
JWT_SECRET=your-jwt-secret-key-change-in-production

# ===== AI 对话（DeepSeek）=====
AI_API_KEY=sk-your-deepseek-api-key-here
AI_BASE_URL=https://api.deepseek.com/v1
AI_MODEL_NAME=deepseek-chat
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120

# ===== RAG 嵌入模型（OpenAI 兼容）=====
# 推荐硅基流动 (SiliconFlow) — 国内直连、价格便宜
AI_EMBEDDING_API_KEY=sk-your-embedding-key
AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
AI_EMBEDDING_MODEL=bge-large-zh-v1.5
```

> **获取 API Key**：
> - DeepSeek：[platform.deepseek.com](https://platform.deepseek.com)（新用户赠送 500 万 token）
> - 硅基流动（Embedding）：[siliconflow.cn](https://siliconflow.cn)（国内直连，推荐）
> - 不配置 Embedding API 不影响普通 AI 对话，但知识库功能不可用

### 3. 一键启动（Docker Compose，推荐）

```bash
# 构建并启动所有服务（MySQL + Redis + Spring Boot + Nginx）
docker compose up -d --build

# 查看服务状态
docker compose ps

# 访问
# 前端:        http://localhost
# API 文档:    http://localhost/doc.html
# 健康检查:    http://localhost:18080/actuator/health

# 可选：数据库管理界面
docker compose --profile debug up -d     # Adminer → http://localhost:18081

# 停止（保留数据卷）
docker compose down

# 停止并重置（删除数据库和 Redis 数据）
docker compose down -v
```

> 首次构建需要下载 Docker 镜像和 Maven 依赖，约 3-5 分钟。后续启动只需 `docker compose up -d`，秒级完成。

### 4. 手动启动（开发模式）

<details>
<summary>点击展开手动启动步骤</summary>

#### 初始化数据库

```bash
mysql -u root -p < springboot/src/main/resources/init.sql
mysql -u root -p springboot_zyt < DB_MIGRATION.sql
```

#### 配置邮箱（可选）

编辑 `springboot/src/main/resources/application.yml`：

```yaml
spring:
  mail:
    username: your_email@163.com
    password: your_smtp_auth_code  # 非登录密码！需开启 SMTP 获取授权码
```

#### 启动后端

```bash
cd springboot
mvn spring-boot:run
# → http://localhost:8080

# 运行单元测试（212 个测试，~10 秒）
mvn test
```

#### 启动前端

```bash
cd web/frontend
npm install
npm run dev
# → http://localhost:3000
```

#### 设置管理员

```bash
mysql -u root -p springboot_zyt -e "UPDATE user SET role = 'admin' WHERE email = 'your_admin@example.com';"
```

</details>

### 5. 开始使用

1. 访问 `http://localhost:3000`（或 `http://localhost` Docker 模式）
2. **注册**：点击「注册账号」→ 输入邮箱 → 发送验证码 → 设置密码
3. **登录**：邮箱 + 密码 + 图形验证码（或切换到微信扫码登录）
4. **AI 对话**：登录后进入「AI 对话」→ 发送消息 → 体验流式对话
5. **RAG 知识库**：进入「知识库」→ 创建知识库 → 上传文档 → 在对话中开启 RAG 开关

---

## 项目结构

```
├── springboot/                              # 后端 Spring Boot 工程
│   ├── pom.xml                              # Maven 配置
│   ├── Dockerfile                           # 多阶段构建（Maven → JRE）
│   └── src/
│       ├── main/
│       │   ├── java/com/zora/
│       │   │   ├── AppStart.java            # 启动类（@MapperScan + @EnableScheduling）
│       │   │   ├── config/                  # 配置类（12 个）
│       │   │   │   ├── AiConfig.java        # LangChain4j 流式模型 + Embedding 配置
│       │   │   │   ├── RagConfig.java       # Embedding 模型 + SimpleEmbeddingStore Bean
│       │   │   │   ├── Knife4jConfig.java   # OpenAPI 3 文档 + SecurityRequirement 注入
│       │   │   │   ├── SecurityConfig.java  # Spring Security（仅 BCrypt，其余禁用）
│       │   │   │   ├── WebConfig.java       # 拦截器注册 + CORS 配置
│       │   │   │   ├── LoginInterceptor.java    # JWT Token 校验 + Redis 单设备验证
│       │   │   │   ├── RoleInterceptor.java     # RBAC 角色校验（@RequireRole）
│       │   │   │   ├── RequireRole.java     # 自定义角色注解（@RequireRole("admin")）
│       │   │   │   ├── MyConfig.java        # MyBatis-Plus 分页插件
│       │   │   │   ├── WechatConfig.java    # 微信 OAuth 配置（@Value 注入）
│       │   │   │   ├── CleanupTask.java     # 定时清理任务（@Scheduled）
│       │   │   │   └── SwaggerCompatController.java  # Swagger JSON 兼容端点
│       │   │   ├── controller/              # REST 控制器（3 个，共 32+ 端点）
│       │   │   │   ├── UserController.java  # 用户认证（16 个端点）
│       │   │   │   ├── AiChatController.java    # AI 对话 + SSE 流式 + RAG 对话
│       │   │   │   └── RagController.java   # RAG 知识库 CRUD（16 个端点）
│       │   │   ├── service/                 # 业务逻辑层
│       │   │   │   ├── UserService.java     # 用户认证接口
│       │   │   │   ├── AiChatService.java   # AI 对话接口
│       │   │   │   ├── RagService.java      # 知识库 CRUD + 检索 + 两级回收站
│       │   │   │   ├── RagProcessingService.java  # 文档处理管道 + 启动重建
│       │   │   │   └── impl/                # 实现类
│       │   │   │       ├── UserServiceImpl.java       # 用户认证完整业务（Redis + 邮件 + 微信）
│       │   │   │       ├── AiChatServiceImpl.java     # AI 对话 + RAG 上下文注入
│       │   │   │       ├── RagServiceImpl.java        # 知识库业务 + 两级回收站（~790 行）
│       │   │   │       ├── RagProcessingServiceImpl.java  # Tika 解析 + 分块 + Embedding + 启动重建
│       │   │   │       └── SimpleEmbeddingStore.java   # 自实现余弦相似度内存向量存储
│       │   │   ├── entity/                  # 实体类（MyBatis-Plus @TableName）
│       │   │   │   ├── User.java            # 用户（id, email, password, openid, role）
│       │   │   │   ├── ChatConversation.java # 对话会话
│       │   │   │   ├── ChatMessage.java     # 对话消息
│       │   │   │   ├── KnowledgeBase.java   # 知识库（支持软删除）
│       │   │   │   ├── KbDocument.java      # 文档（含处理状态机）
│       │   │   │   └── KbChunk.java         # 文本块（持久化用于向量重建）
│       │   │   ├── mapper/                  # MyBatis-Plus Mapper（BaseMapper 免写 SQL）
│       │   │   │   ├── UserMapper.java
│       │   │   │   ├── ChatConversationMapper.java
│       │   │   │   ├── ChatMessageMapper.java
│       │   │   │   ├── KnowledgeBaseMapper.java
│       │   │   │   ├── KbDocumentMapper.java
│       │   │   │   └── KbChunkMapper.java
│       │   │   ├── exception/               # 异常体系（7 个类）
│       │   │   │   ├── BusinessException.java    # 基类（携带 code + msg）
│       │   │   │   ├── BadRequestException.java  # 400 参数校验失败
│       │   │   │   ├── UnauthorizedException.java # 401 未认证
│       │   │   │   ├── ForbiddenException.java   # 403 权限不足
│       │   │   │   ├── NotFoundException.java    # 404 资源不存在
│       │   │   │   ├── RateLimitException.java   # 429 频率限制
│       │   │   │   └── GlobalExceptionHandler.java # @RestControllerAdvice 全局异常捕获
│       │   │   └── utils/                   # 工具类（7 个）
│       │   │       ├── JwtUtil.java         # JWT 生成/解析/类型校验（HMAC-SHA256）
│       │   │       ├── CaptchaUtil.java     # 图形验证码生成（Java AWT）
│       │   │       ├── EmailUtil.java       # 163邮箱 SMTP 异步发送
│       │   │       ├── WechatUtil.java      # 微信 OAuth API 调用 + DTO
│       │   │       ├── ResponseUtil.java    # 统一响应体 { code, msg, data }
│       │   │       ├── FileTypeUtil.java    # 文件类型检测 + 大小校验
│       │   │       └── TextSplitterUtil.java # 递归文本分割器（800 字符/块）
│       │   └── resources/
│       │       ├── application.yml          # 应用配置（数据源/Redis/AI/微信/RAG）
│       │       ├── application-example.yml  # 配置示例模板
│       │       ├── init.sql                 # 数据库初始化（建表 + 索引）
│       │       └── db/migration/            # 数据库迁移脚本
│       │           ├── DB_MIGRATION.sql     # 完整迁移（所有表）
│       │           ├── V2__chat_tables.sql  # AI 对话表（conversation + message）
│       │           └── V3__rag_tables.sql   # RAG 知识库/文档/块表
│       └── test/                            # 单元测试（212 个，JUnit 5 + Mockito + MockMvc）
│           ├── resources/
│           │   └── application.yml          # 测试配置（H2 + 占位凭证）
│           └── java/com/zora/
│               ├── utils/                   # 工具类测试（3 个）
│               │   ├── ResponseUtilTest.java
│               │   ├── CaptchaUtilTest.java
│               │   └── JwtUtilTest.java
│               ├── service/                 # Service 层测试（3 个）
│               │   ├── UserServiceImplTest.java
│               │   ├── RagServiceImplTest.java
│               │   └── EmbeddingDebugTest.java
│               ├── config/                  # 拦截器测试（3 个）
│               │   ├── LoginInterceptorTest.java
│               │   ├── RoleInterceptorTest.java
│               │   └── SwaggerCompatControllerTest.java
│               ├── controller/              # Controller 测试（2 个）
│               │   ├── UserControllerTest.java
│               │   └── RagControllerTest.java
│               └── exception/               # 异常处理测试（1 个）
│                   └── GlobalExceptionHandlerTest.java
├── web/frontend/                            # 前端 Vue 3 + Vite 工程
│   ├── package.json                         # 依赖管理
│   ├── vite.config.js                       # Vite 配置（代理 + Element Plus 按需导入）
│   ├── index.html                           # HTML 入口
│   ├── Dockerfile                           # 多阶段构建（Node → Nginx）
│   ├── nginx.conf                           # Nginx 配置（SPA 路由 + SSE 代理 + API 转发）
│   └── src/
│       ├── main.js                          # Vue 应用入口
│       ├── App.vue                          # 根组件
│       ├── style.css                        # 全局样式 + 响应式布局
│       ├── views/                           # 页面组件（7 个）
│       │   ├── Home.vue                     # 首页（认证系统 + AI + 知识库功能展示）
│       │   ├── Login.vue                    # 登录页（密码登录 + 微信扫码双 Tab）
│       │   ├── Register.vue                 # 注册页（邮箱验证码注册）
│       │   ├── ForgotPassword.vue           # 找回密码页（验证码 + 重置）
│       │   ├── Chat.vue                     # AI 对话页（SSE 流式 + RAG 开关 + 知识库选择）
│       │   ├── KnowledgeBase.vue            # 知识库管理页（卡片 + 文档表格 + 两级回收站）
│       │   └── Admin.vue                    # 管理员页面（用户管理）
│       ├── api/                             # API 封装层
│       │   ├── index.js                     # Axios 实例 + 请求/响应拦截器（自动刷新 Token）
│       │   ├── user.js                      # 用户认证 API（12 个函数）
│       │   ├── ai.js                        # AI 对话 API（含 SSE 流式 + 对话管理）
│       │   └── rag.js                       # RAG 知识库 API（CRUD + 上传 + 回收站）
│       ├── router/
│       │   └── index.js                     # 路由配置 + beforeEach 导航守卫
│       └── utils/
│           └── token.js                     # localStorage Token 存取（双 Token）
├── .github/                                 # GitHub Actions CI/CD 工作流
├── docker-compose.yml                       # Docker 容器编排（MySQL + Redis + Backend + Nginx）
├── .env                                     # 环境变量（数据库密码、JWT 密钥、API Key）
├── CLAUDE.md                                # 项目架构与开发规范（Claude Code 指南）
├── P0_SECURITY_FIX.md                       # P0 安全修复文档（Token 类型校验 + 暴力破解防护）
├── P1_SECURITY_FIX.md                       # P1 安全修复文档
├── WECHAT_SETUP_GUIDE.md                    # 微信扫码登录完整配置指南
├── 项目构建教程1.md                          # 用户认证系统 28 步构建教程
├── 项目构建教程2.md                          # AI 对话 + RAG 知识库详细实现
├── README1.md                               # README 历史版本
└── README2.md                               # README 历史版本
```

---

## API 概览

### 用户认证

| 方法 | 路径 | 认证 | 说明 |
|------|------|:----:|------|
| GET | `/user/captcha` | 否 | 获取图形验证码 |
| POST | `/user/send-code` | 否 | 发送邮箱验证码（注册用） |
| POST | `/user/register` | 否 | 邮箱验证码注册 |
| POST | `/user/login` | 否 | 密码登录（图形验证码 + 5次失败锁定） |
| POST | `/user/logout` | accessToken | 登出 |
| POST | `/user/refresh` | refreshToken | 刷新 accessToken（强制 type=refresh） |
| GET | `/user/info` | accessToken | 鉴权探针 |
| GET | `/user/me` | accessToken | 获取当前用户信息（含角色） |
| POST | `/user/wechat/qrcode` | 否 | 生成微信扫码场景 |
| GET | `/user/wechat/check` | 否 | 轮询扫码状态 |
| GET | `/user/wechat/callback` | 否 | 微信 OAuth 回调 |
| POST | `/user/wechat/bind-email` | 否 | 微信扫码后绑定邮箱 |
| POST | `/user/forgot-password/send-code` | 否 | 发送密码重置验证码 |
| POST | `/user/forgot-password/reset` | 否 | 验证码校验 + 重置密码 |
| GET | `/user/admin/users` | accessToken + admin | 管理员分页查询所有用户 |

### AI 对话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|:----:|
| POST | `/ai/chat/stream` | SSE 流式对话 | ✅ |
| POST | `/ai/chat/rag-stream` | SSE RAG 流式对话（传 knowledgeBaseId） | ✅ |
| GET | `/ai/conversations` | 获取对话列表 | ✅ |
| POST | `/ai/conversations` | 新建对话 | ✅ |
| GET | `/ai/conversations/{id}` | 获取对话消息 | ✅ |
| DELETE | `/ai/conversations/{id}` | 删除对话（移至回收站） | ✅ |
| GET | `/ai/conversations/trash` | 获取回收站列表 | ✅ |
| POST | `/ai/conversations/{id}/restore` | 恢复已删除对话 | ✅ |
| DELETE | `/ai/conversations/{id}/permanent` | 永久删除对话 | ✅ |

### RAG 知识库

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|:----:|
| POST | `/rag/knowledge-bases` | 创建知识库 | ✅ |
| GET | `/rag/knowledge-bases` | 列出用户的知识库 | ✅ |
| GET | `/rag/knowledge-bases/{id}` | 获取知识库详情 + 文档列表 | ✅ |
| PUT | `/rag/knowledge-bases/{id}` | 更新知识库名称/描述 | ✅ |
| DELETE | `/rag/knowledge-bases/{id}` | 软删除知识库 | ✅ |
| POST | `/rag/knowledge-bases/{id}/documents` | 上传文档（multipart，≤10MB） | ✅ |
| GET | `/rag/knowledge-bases/{id}/documents` | 列出文档（含处理状态和块数） | ✅ |
| DELETE | `/rag/knowledge-bases/{id}/documents/{docId}` | 删除文档 | ✅ |
| POST | `/rag/knowledge-bases/{id}/query` | 测试检索（返回相关块+分数） | ✅ |
| GET | `/rag/recycle-bin` | 获取知识库回收站列表 | ✅ |
| PUT | `/rag/recycle-bin/{kbId}/restore` | 恢复知识库及文档 | ✅ |
| DELETE | `/rag/recycle-bin/{kbId}` | 永久删除知识库（不可逆） | ✅ |
| GET | `/rag/knowledge-bases/{kbId}/recycle-bin` | 获取文档回收站列表（按KB） | ✅ |
| PUT | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}/restore` | 恢复文档（重新嵌入向量） | ✅ |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}` | 永久删除文档（不可逆） | ✅ |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin` | 清空文档回收站 | ✅ |

> 📖 在线调试：启动后端后访问 `http://localhost:8080/doc.html`（Knife4j 接口文档，支持在线发送请求）

---

## 架构设计

### 整体架构

```
┌────────────────────┐     SSE/JSON/HTTP    ┌───────────────────┐     OpenAI API   ┌────────────┐
│    Vue 3 前端      │ ←───────────── ────→ │  Spring Boot 后端 │ ←───────────────→ │  DeepSeek │
│                    │     Nginx 代理       │                   │     HTTPS         │    API    │
│  • Login/Register  │                      │  • 用户认证 (JWT) │                   │           │
│  • Chat.vue        │                      │  • AI 对话 (LLM)  │                   │           │
│  • KnowledgeBase   │                      │  • RAG 知识库     │                   │           │
│  • Home.vue        │                      │  • 全局异常处理    │                   │           │
└────────┬───────────┘                      └─────────┬─────────┘                   └───────────┘
         │                                            │                                  │
         │ localStorage                               │ MySQL (用户/对话/消息/知识库)      │ Embedding API
         │ (JWT Token)                                │ Redis (Token/验证码/状态)         │ (OpenAI 兼容)
         ▼                                            ▼                                  ▼
┌──────────────────┐                      ┌───────────────────┐                   ┌───────────┐
│    浏览器        │                       │     数据库        │                   │ Embedding │
└──────────────────┘                      └───────────────────┘                   │  Service  │
                                                                                  └───────────┘
```

### 认证鉴权链路

```
浏览器 → Vue Router（前端路由守卫）
       → Axios 请求拦截器（自动注入 accessToken）
       → HTTP 请求 → LoginInterceptor（后端拦截器）
         ├─ 提取 Authorization 头
         ├─ JWT 签名 + 过期校验
         ├─ Redis 比对（单设备登录）
         └─ 放行/拒绝
       → RoleInterceptor（@RequireRole 注解检查）
       → Controller → Service → Mapper → DB
```

### 双 Token 自动刷新

```
accessToken 过期（30min）
  → 后端 401 + "Token 已失效"
  → Axios 响应拦截器捕获
    ├─ isRefreshing 锁（并发请求只刷新一次）
    ├─ POST /user/refresh（携带 refreshToken，校验 type=refresh）
    ├─ 获取新 accessToken → 更新 localStorage
    ├─ 重放 pendingRequests 队列中的所有请求
    └─ 重试原请求
  → 用户完全无感知

refreshToken 也过期（7天）
  → 清除 Token → 跳转登录页
```

### RAG 知识库管道

```
上传文档 (PDF/DOCX/DOC/TXT/MD)
  → Apache Tika 文本提取
    → TextSplitterUtil 递归分块 (800 chars / 100 overlap)
      → OpenAiEmbeddingModel 向量嵌入
        → SimpleEmbeddingStore 内存向量库 (余弦相似度)
           ↓
RAG 对话 → embed(用户问题) → 检索 Top-K 相关块 → 注入 System Prompt → SSE 流式输出
```

### SSE 流式传输链路

```
DeepSeek API (chunked response)
  → OpenAiStreamingChatModel (HTTP client)
    → StreamingChatResponseHandler.onPartialResponse(token)
      → Flux.create() sink.next(token)
        → Spring MVC (text/event-stream)
          → Nginx (proxy_buffering off)
            → 浏览器 fetch ReadableStream
              → 解析 data: 行 → onToken()
                → streamingContent += token
                  → v-html="renderMarkdown(streamingContent)"
                    → 用户看到逐字出现的回复
```

### 全局异常处理

采用 Spring MVC 标准 `@RestControllerAdvice` 统一捕获所有异常：

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
Axios 响应拦截器:
  if (res.code !== 200) → ElMessage.error("邮箱不能为空")
```

**异常层次结构**：

```
RuntimeException
  └── BusinessException（基类，携带 code + msg）
        ├── BadRequestException     → 400 参数校验失败
        ├── UnauthorizedException   → 401 未认证（唯一返回真实 HTTP 401 的异常）
        ├── ForbiddenException      → 403 权限不足
        ├── NotFoundException       → 404 资源不存在
        └── RateLimitException      → 429 频率限制
```

---

## 安全特性

| 层级 | 机制 | 说明 |
|------|------|------|
| 传输层 | HTTPS（生产环境） | ngrok 开发环境自动提供 HTTPS |
| 认证层 | JWT HMAC-SHA256 | 签名防篡改，30min 短时效降低泄露风险 |
| 类型校验 | JWT `type` claim | `/refresh` 强制校验 `type=refresh`，防 accessToken 无限续期 |
| 密码存储 | BCrypt | 自适应哈希，自带随机盐，抗彩虹表 |
| 防刷 | 图形验证码 + 60s 间隔 | 发送验证码前校验，同邮箱 60 秒内不可重复发送 |
| 防暴力破解 | Redis INCR + HTTP 429 | 5 次失败锁定 15 分钟，TTL 仅首次设置防绕过 |
| 防用户枚举 | 统一错误提示 | 所有登录失败统一返回「邮箱或密码错误」 |
| 单设备登录 | Redis Token 缓存 | 新登录自动踢掉旧设备 |
| XSS 防护 | DOMPurify | 过滤 AI 输出中的潜在恶意 HTML/JS |
| AI 注入防护 | System Prompt 规则 | 禁止模型执行敏感操作，限制回答范围 |
| CSRF | 显式禁用 + JWT 无状态 | JWT 不依赖 Cookie，天然免疫 CSRF |
| OAuth 安全 | state 参数（sceneId） | 防 CSRF 攻击，回调时校验 |

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.11 | 基础框架 |
| MyBatis-Plus | 3.5.12 | ORM（BaseMapper 免写 SQL + 分页插件） |
| MySQL | 8.x | 持久化存储 |
| Redis | 7.x | Token 缓存 + 验证码 + 登录失败计数 + 微信状态 |
| LangChain4j | 1.15.0 | AI 应用框架（Java 原生，流式对话 + Embedding） |
| DeepSeek Chat | V3 | 大语言模型（兼容 OpenAI API） |
| Apache Tika | 2.9.2 | 文档文本提取（PDF/DOCX/DOC/TXT/MD） |
| Spring Security | 6.x | BCrypt 密码加密 |
| jjwt | 0.12.6 | JWT 生成/解析/类型校验 |
| JavaMail | 3.5.11 | 163 邮箱 SMTP 验证码发送 |
| Java AWT | JDK 内置 | 图形验证码绘制 |
| Jackson | 2.x | JSON 序列化 |
| Knife4j | 4.5.0 | OpenAPI 3 接口文档 + 在线调试 |
| WebFlux | — | SSE 流式响应（`Flux<String>`） |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 前端框架（Composition API） |
| Vite | 8.x | 构建工具（Rolldown 生产构建） |
| Element Plus | 2.14 | UI 组件库（按需导入） |
| Vue Router | 4.x | 前端路由 + 导航守卫 |
| Axios | 1.16 | HTTP 客户端 + 拦截器 |
| qrcode | 1.5 | 前端 Canvas 二维码生成 |
| marked | — | Markdown → HTML 转换 |
| highlight.js | — | 代码语法高亮（190+ 语言） |
| DOMPurify | — | XSS 防护（过滤 AI 输出） |
| 原生 fetch | — | SSE 流式读取（Axios 不支持 ReadableStream） |

---

## 测试

**框架**：JUnit 5 + Mockito + Spring MockMvc (standalone setup)，212 个测试，纯单元测试 — 无需 MySQL/Redis/网络，CI 就绪。

```bash
cd springboot
mvn test   # ~10 秒，0 failures
```

**三层 Mock 策略**：

| 层级 | 模式 | 测试类 |
|------|------|--------|
| 纯 JUnit 5 | `@Test` only | `ResponseUtilTest`, `CaptchaUtilTest`, `JwtUtilTest` |
| Mockito + ReflectionTestUtils | `@InjectMocks` / `@Mock` / `@Spy` | `UserServiceImplTest`, `LoginInterceptorTest`, `RagServiceImplTest` |
| Standalone MockMvc | `MockMvcBuilders.standaloneSetup()` | `UserControllerTest`, `RagControllerTest`, `GlobalExceptionHandlerTest` |

---

## 切换 LLM 提供商

修改 `.env` 中的三个变量即可，LangChain4j 的 `OpenAiStreamingChatModel` 兼容所有 OpenAI API 格式的服务商：

```env
# OpenAI
AI_API_KEY=sk-your-openai-key
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL_NAME=gpt-4o

# 本地 Ollama
AI_API_KEY=ollama
AI_BASE_URL=http://localhost:11434/v1
AI_MODEL_NAME=qwen2.5

# 其他兼容服务商（通义千问 / 智谱 / Moonshot 等）
AI_API_KEY=sk-your-key
AI_BASE_URL=https://your-provider-api-url/v1
AI_MODEL_NAME=your-model-name
```

---

## 路线图

### ✅ Phase 1：用户认证系统（已完成）

- [x] 图形验证码 + 邮箱验证码注册
- [x] JWT 双 Token 鉴权 + 无感刷新
- [x] 微信 OAuth 扫码登录
- [x] RBAC 角色权限控制
- [x] 暴力破解防护 + 单设备登录
- [x] 邮箱找回密码
- [x] 全局异常处理（`@ControllerAdvice`）
- [x] Knife4j 接口文档
- [x] 171 个单元测试
- [x] Docker 容器化（docker-compose 一键启动）
- [x] GitHub Actions CI/CD

### ✅ Phase 2：AI 智能对话 + RAG 知识库（已完成）

- [x] LangChain4j + DeepSeek 集成
- [x] SSE 流式传输 + Markdown 渲染
- [x] 多轮上下文对话 + 对话历史管理
- [x] Apache Tika 文档解析（PDF/DOCX/TXT/MD）
- [x] 递归文本分块 + OpenAI 兼容 Embedding
- [x] 自实现余弦相似度向量存储
- [x] RAG 检索增强生成 + 知识库管理界面
- [x] 启动时向量索引自动重建
- [x] 212 个单元测试（含 RAG 知识库 + 两级回收站测试）

### 🔜 Phase 3：AI Agent 智能体

- [ ] LangChain4j Tool Calling
- [ ] 内置工具：网页搜索、数学计算、代码执行
- [ ] Agent 可视化推理过程
- [ ] 记忆摘要 + 长期记忆
- [ ] LangGraph4j 多 Agent 编排

### 🔜 Phase 4：智能搜索与分析

- [ ] 全文搜索引擎
- [ ] 对话数据分析仪表盘
- [ ] 用户行为分析
- [ ] 智能推荐

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 项目架构与开发规范（含 RAG 完整文档） |
| [项目构建教程.md](项目构建教程.md) | 用户认证系统 28 步完整构建过程 |
| [项目构建教程2.md](项目构建教程2.md) | AI 智能对话 + RAG 知识库详细实现 |
| [WECHAT_SETUP_GUIDE.md](WECHAT_SETUP_GUIDE.md) | 微信扫码登录完整配置指南 |
| [P0_SECURITY_FIX.md](P0_SECURITY_FIX.md) | P0 安全修复记录：Token 类型校验 + 暴力破解防护 |
| [P1_SECURITY_FIX.md](P1_SECURITY_FIX.md) | P1 安全修复记录 |
| [DB_MIGRATION.sql](DB_MIGRATION.sql) | 数据库迁移脚本 |

---

## License

MIT
