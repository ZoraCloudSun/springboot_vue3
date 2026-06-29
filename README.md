# Spring Boot + Vue3 全栈项目：认证系统 + AI 智能对话 + RAG 知识库 + AI Agent 智能体 + 智能搜索与分析

> 前后端分离的全栈项目 | 2026

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED)](https://www.docker.com/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.15.0-00B265)](https://docs.langchain4j.dev/)
[![DeepSeek](https://img.shields.io/badge/deepseek-v4-536DFE)](https://platform.deepseek.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![CI](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml/badge.svg)](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml)

---

## 项目简介

一套**生产级**前后端分离的全栈项目，集成了完整的用户认证闭环、基于大模型的 AI 智能对话系统、RAG 知识库，以及具备工具调用和多 Agent 协作能力的 AI Agent 智能体，支持智能搜索与分析。

| 系统                  | 说明                                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 🔐**用户认证系统**    | 图形验证码、邮箱验证码注册、JWT 双 Token 鉴权、微信 OAuth 扫码登录、RBAC 角色权限控制、暴力破解防护                                                     |
| 🤖**AI 智能对话**     | deepseek-v4 大模型驱动、SSE 流式传输（打字机效果）、多轮上下文对话、Markdown + 代码高亮渲染、对话历史管理                                               |
| 📚**RAG 知识库**      | 文档上传 → 文本提取 → 分块 → Embedding 向量化 → 检索增强生成，让 AI 基于用户自己的文档回答                                                              |
| 🧠**AI Agent 智能体** | LangChain4j Tool Calling 框架，AI 可自主调用工具（网页搜索/数学计算/代码执行），推理可视化面板，多 Agent 协作（Supervisor → 专家 → 聚合），长期记忆摘要 |
| 🔍**智能搜索与分析**  | MySQL FULLTEXT 全文搜索（ngram 中文分词）+ ECharts 数据仪表盘 + 用户行为追踪 + 智能推荐                                                                 |

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus 3.5.12 + MySQL + Redis + LangChain4j 1.15.0 + Apache Tika + Tavily Search + exp4j + JJWT 0.12 + Spring AOP
- **前端**：Vue 3.5 + Vite 8 + Element Plus 2.14 + Vue Router 4 + Axios + marked + highlight.js + DOMPurify + ECharts 6

---

## 核心功能

### 用户认证

| 功能           | 说明                                                                   |
| -------------- | ---------------------------------------------------------------------- |
| 图形验证码     | Java AWT 绘制，6位大写字母 + 干扰线 + 噪点，Redis TTL 2min             |
| 邮箱验证码注册 | 163邮箱 SMTP，6位数字验证码，60秒防刷                                  |
| 密码登录       | BCrypt 加密 + 5次失败锁定15分钟 + 统一错误提示防用户枚举               |
| 双 Token 鉴权  | accessToken (30min) + refreshToken (7天)，JWT`type` claim 强制类型校验 |
| 无感刷新       | Axios 响应拦截器 + 请求队列，并发 401 只刷新一次                       |
| 单设备登录     | Redis 缓存 Token，新登录自动踢掉旧设备                                 |
| 微信扫码登录   | 真实 OAuth 2.0，已绑定用户扫一扫直接登录，新用户扫码后邮箱绑定         |
| 邮箱找回密码   | 验证码校验 + 重置后强制所有设备重新登录                                |
| RBAC 角色权限  | 自定义`@RequireRole` 注解 + RoleInterceptor，user/admin 双角色         |

### AI 智能对话

| 功能             | 说明                                                 |
| ---------------- | ---------------------------------------------------- |
| 🤖 大模型驱动    | deepseek-v4，中英文对话、代码生成、数据分析          |
| 📡 SSE 流式传输  | 逐字输出，打字机效果，120秒超时支持长回复            |
| 📝 Markdown 渲染 | 标题、列表、表格、引用、代码块（190+ 语言语法高亮）  |
| 🔒 XSS 安全过滤  | DOMPurify 过滤 AI 输出中的潜在恶意 HTML/JS           |
| 💬 多轮上下文    | 自动携带最近 20 条历史消息，支持连续对话             |
| 📂 对话管理      | 新建、切换、删除、回收站恢复、永久删除，自动生成标题 |
| 🛑 中途停止      | 随时停止 AI 生成，已输出内容保留                     |

### RAG 知识库

| 功能          | 说明                                                                                                       |
| ------------- | ---------------------------------------------------------------------------------------------------------- |
| 📄 文档解析   | Apache Tika 提取 PDF/DOCX/DOC/TXT/MD                                                                       |
| ✂️ 智能分块   | 递归分割，800字符/块，100字符重叠                                                                          |
| 🔢 向量嵌入   | OpenAI 兼容 Embedding API（支持 OpenAI / 硅基流动 / Ollama）                                               |
| 🔍 向量检索   | 余弦相似度检索，内存向量库 + MySQL 持久化                                                                  |
| 🔄 启动重建   | 应用重启自动从 MySQL 重建向量索引                                                                          |
| 🗂️ 知识库管理 | 卡片列表、文档表格、处理状态轮询、检索测试面板                                                             |
| ♻️ 两级回收站 | 知识库级回收站（全局）+ 文档级回收站（按知识库），支持恢复（自动重嵌入向量）和永久删除（清理文件/向量/DB） |

### AI Agent 智能体

| 功能             | 说明                                                                                             |
| ---------------- | ------------------------------------------------------------------------------------------------ |
| 🔧 工具调用      | LangChain4j Tool Calling 框架，AI 可自主判断并调用搜索/计算/代码执行等工具                       |
| 🌐 网页搜索      | WebSearchTool — Tavily Search API，搜索互联网获取实时信息                                        |
| 🧮 数学计算      | MathTool — exp4j 安全表达式求值，支持三角函数/对数/阶乘等 30+ 运算                               |
| 💻 代码执行      | CodeExecutionTool — JDK ScriptEngine JS 沙箱，超时保护 + 输出截断（默认关闭）                    |
| 🧠 多 Agent 协作 | SupervisorAgent 意图分类 → 专家 Agent 执行（Research/Math/Code）→ Summarizer 聚合结果            |
| 📊 推理可视化    | 实时展示 Agent 思考过程、工具调用和结果，颜色编码面板，可折叠/展开                               |
| 💾 长期记忆      | Redis ChatMemory 24h 缓存 + LLM 异步摘要生成（每 10 条消息触发），对话超窗口后仍能"记住"早期内容 |
| 🛡️ 三层降级      | 多 Agent → 标准 Agent → 直接回答，任何异常都不会导致无回答                                       |
| 🔒 安全防护      | 推理循环最多 5 次迭代 + 10 次/分钟限流 + 18 种 Prompt Injection 检测                             |

### 智能搜索与分析

| 功能            | 说明                                                                             |
| --------------- | -------------------------------------------------------------------------------- |
| 🔍 全文搜索     | MySQL 8 InnoDB FULLTEXT + ngram parser 中文分词，跨对话搜索消息内容              |
| 🌐 搜索结果高亮 | Java 层关键词`<mark>` 标签高亮，前端 DOMPurify 安全过滤                          |
| 📊 数据仪表盘   | ECharts 6 可视化图表：消息趋势、活跃时段、对话趋势、消息占比、功能排行           |
| 📈 统计维度     | 总览摘要卡片 + 双线折线图（用户/AI 消息）+ 柱状图（活跃时段）+ 饼图（角色占比）  |
| 🔎 用户行为分析 | AOP 切面 +`@TrackAction` 注解 + `@Async` 异步日志写入，不阻塞主流程              |
| 📝 行为追踪     | 6 种行为类型自动采集：对话创建、消息发送、搜索查询、KB 上传、KB 检索、Agent 调用 |
| 💡 智能推荐     | 关键词提取 + MySQL FULLTEXT 匹配：相关对话、建议问题、热门知识库                 |
| ⚡ Redis 缓存   | 统计数据 TTL 分级缓存（30min~4h）+ 推荐缓存 30min                                |
| 🎨 推荐卡片     | Chat 侧边栏嵌入 RecommendCard，3 个 Tab 展示相关对话/问题/知识库                 |

---

## 快速开始

### 环境要求

| 组件              | 版本 | 用途                                        |
| ----------------- | ---- | ------------------------------------------- |
| JDK               | 21+  | Spring Boot 3.x                             |
| MySQL             | 8.x  | 数据持久化                                  |
| Redis             | 7.x  | Token 缓存 + 验证码 + 向量状态              |
| Node.js           | 18+  | 前端构建                                    |
| Docker            | —    | 一键启动（可选，替代手动安装）              |
| DeepSeek API Key  | —    | AI 对话（新用户赠送 500 万 token）          |
| Embedding API Key | —    | RAG 向量嵌入（硅基流动推荐，国内直连）      |
| 163邮箱           | —    | 发送验证码（可选，需开启 SMTP）             |
| 微信测试号        | —    | 微信扫码登录（可选）                        |
| Tavily API Key    | —    | Agent 网页搜索工具（可选，免费 1000 次/月） |

### 1. 克隆项目

```bash
git clone https://github.com/zoracloudsun/zora_clous
cd zora_clous
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

# ===== Agent 智能体（Phase 3）=====
TAVILY_API_KEY=tvly-your-tavily-api-key
# 工具开关（默认启用搜索和数学，代码执行需手动开启）
# AGENT_TOOL_WEB_SEARCH=true
# AGENT_TOOL_MATH=true
# AGENT_TOOL_CODE_EXEC=false
```

> **获取 API Key**：
>
> - DeepSeek：[platform.deepseek.com](https://platform.deepseek.com)（新用户赠送 500 万 token）
> - 硅基流动（Embedding）：[siliconflow.cn](https://siliconflow.cn)（国内直连，推荐）
> - 不配置 Embedding API 不影响普通 AI 对话，但知识库功能不可用

### 3. 生产模式：Docker Compose 一键启动

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

### 4. 开发模式：Docker + 本地热加载（推荐日常开发）

基础设施（MySQL + Redis）运行在 Docker 中，后端和前端在宿主机运行，支持**修改代码即生效**的热加载体验。

<details>
<summary>点击展开详细步骤</summary>

#### 4.1 启动 Docker 基础设施

```bash
# 只启动 MySQL 和 Redis（不启动 backend 和 frontend 容器）
docker compose up -d mysql redis
```

验证基础设施就绪：

```bash
docker compose ps
# auth-mysql   Up XX minutes (healthy)   0.0.0.0:13307->3306/tcp
# auth-redis   Up XX minutes (healthy)   0.0.0.0:6379->6379/tcp
```

#### 4.2 配置环境变量

**VSCode 用户**（推荐）：项目的 `.vscode/launch.json` 已配置 `"envFile": "${workspaceFolder}/.env"`，使用 `F5` 或 Spring Boot Dashboard 启动即可自动加载 `.env` 中的全部环境变量，无需手动设置。

**终端用户**：在启动前手动导出环境变量：

```bash
# 数据库（指向 Docker 容器映射端口）
export MYSQL_HOST=localhost
export MYSQL_PORT=13307
export MYSQL_USERNAME=springuser
export MYSQL_PASSWORD=springpass

# Redis
export REDIS_HOST=localhost
export REDIS_PORT=6379

# AI / Embedding API（从 .env 复制）
export AI_API_KEY=sk-your-deepseek-key
export AI_BASE_URL=https://api.deepseek.com/v1
export AI_MODEL_NAME=deepseek-chat
export AI_EMBEDDING_API_KEY=sk-your-embedding-key
export AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
export AI_EMBEDDING_MODEL=BAAI/bge-large-zh-v1.5
```

> ⚠️ **常见坑**：终端直接 `mvn spring-boot:run` 不会加载 `.env`。如果忘记 export 环境变量，后端会回退到 `application.yml` 默认值（`localhost:3306`），可能连到本地老 MySQL 实例导致 RAG 表不存在的错误。各变量的正确值请参考项目根目录的 `.env` 文件，或查阅 [FIX_Document/Windows_Reserved_Port_8080_FIX.md](FIX_Document/Windows_Reserved_Port_8080_FIX.md#环境变量速查表)。

#### 4.3 启动后端（热加载）

```bash
cd springboot
mvn spring-boot:run
# → http://localhost:8080
```

后端依赖 `spring-boot-devtools`，保存代码后 Spring Boot 自动重启（~3 秒），无需手动重启。

> **VSCode 用户提示**：打开 `AppStart.java`，点击编辑器右上角的 ▶️ 运行按钮，或按 `F5`，启动时会自动加载 `.env`。

#### 4.4 启动前端（热模块替换 HMR）

```bash
cd web/frontend
npm install        # 首次运行需要
npm run dev
# → http://localhost:3000
```

前端使用 Vite HMR，保存文件后浏览器**原地更新组件**（不刷新页面、不丢失状态）。API 请求通过 Vite 代理转发到 `localhost:8080`。

#### 4.5 热加载原理

| 组件        | 工具                 | 行为                                                       |
| ----------- | -------------------- | ---------------------------------------------------------- |
| 后端 Java   | spring-boot-devtools | 保存文件 → Spring 自动重启（2-3s），Fat JAR 运行时自动禁用 |
| 前端 Vue    | Vite HMR             | 保存文件 → 浏览器原地更新组件（无页面刷新、无状态丢失）    |
| MySQL/Redis | Docker               | 无代码变更 → 启动一次，保持运行                            |

#### 4.6 运行测试

```bash
cd springboot
mvn test   # 403 个测试，~20 秒
```

#### 4.7 设置管理员

```bash
docker exec auth-mysql mysql -u root -proot123 springboot_zyt \
  -e "UPDATE user SET role = 'admin' WHERE email = 'your_admin@example.com';"
```

#### 4.8 Windows 端口 8080 被占用？

Windows 系统有时会将 8080 纳入保留端口范围（通常由 WinNAT / Docker / Hyper-V 触发），导致 `java.net.BindException: Address already in use`，但 `netstat` 查不到任何进程。

**诊断**：

```cmd
netsh interface ipv4 show excludedportrange protocol=tcp
```

如果 `8080` 落在输出的区间内，则被 Windows 系统保留。

**修复**（管理员 CMD）：

```cmd
net stop winnat
net start winnat
```

详细分析见 [Windows_Reserved_Port_8080_FIX.md](FIX_Document/Windows_Reserved_Port_8080_FIX.md)。

</details>

### 5. 手动启动（纯本地，不使用 Docker）

如果不想依赖 Docker，可以手动安装 MySQL 和 Redis 后按以下步骤启动：

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
    password: your_smtp_auth_code # 非登录密码！需开启 SMTP 获取授权码
```

#### 启动后端

```bash
cd springboot
mvn spring-boot:run
# → http://localhost:8080

# 运行单元测试（403 个测试，~20 秒）
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

### 6. 开始使用

1. 访问 `http://localhost:3000`（或 `http://localhost` Docker 模式）
2. **注册**：点击「注册账号」→ 输入邮箱 → 发送验证码 → 设置密码
3. **登录**：邮箱 + 密码 + 图形验证码（或切换到微信扫码登录）
4. **AI 对话**：登录后进入「AI 对话」→ 发送消息 → 体验流式对话（Markdown 渲染 + 代码高亮）
5. **RAG 知识库**：进入「知识库」→ 创建知识库 → 上传文档（PDF/DOCX/TXT/MD）→ 在对话中开启 RAG 开关，AI 将基于你的文档回答
6. **AI Agent 智能体**：在对话页打开「Agent」开关 → 发送需要搜索/计算/代码执行的问题 → 观察推理面板实时展示 Agent 的思考过程和工具调用
7. **多 Agent 协作**：在 `.env` 中设置 `AGENT_MULTI_AGENT=true` → Agent 会自动将任务路由给对应领域的专家（研究/数学/代码）
8. **对话管理**：左侧对话列表支持新建/切换/删除 → 右键"..."菜单可进入批量管理模式（全选 + 批量删除/恢复/永久删除）→ 回收站支持恢复和彻底清除
9. **设置管理员**：执行 `docker exec auth-mysql mysql -u root -proot123 springboot_zyt -e "UPDATE user SET role = 'admin' WHERE email = 'your_admin@example.com';"` → 管理员可查看所有注册用户

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
│       │   │   ├── config/                  # 配置类（17 个）
│       │   │   │   ├── AiConfig.java        # LangChain4j 流式 + 非流式双模型 配置
│       │   │   │   ├── AgentConfig.java     # Agent 智能体配置（工具开关/Tavily/记忆）
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
│       │   │   ├── controller/              # REST 控制器（7 个，共 44+ 端点）
│       │   │   │   ├── UserController.java  # 用户认证（16 个端点）
│       │   │   │   ├── AiChatController.java    # AI 对话 + SSE 流式 + RAG 对话 + 批量操作
│       │   │   │   ├── AgentController.java # Agent SSE 流式对话（/agent/chat/stream）
│       │   │   │   ├── RagController.java   # RAG 知识库 CRUD（16 个端点）
│       │   │   │   ├── SearchController.java    # Phase 4 全文搜索（/search/messages）
│       │   │   │   ├── StatisticsController.java # Phase 4 数据仪表盘（8 个统计端点）
│       │   │   │   └── RecommendController.java  # Phase 4 智能推荐（/recommend/suggestions）
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
│       │   │   ├── entity/                  # 实体类（10 个，MyBatis-Plus @TableName）
│       │   │   │   ├── User.java            # 用户（id, email, password, openid, role）
│       │   │   │   ├── ChatConversation.java # 对话会话
│       │   │   │   ├── ChatMessage.java     # 对话消息
│       │   │   │   ├── KnowledgeBase.java   # 知识库（支持软删除）
│       │   │   │   ├── KbDocument.java      # 文档（含处理状态机）
│       │   │   │   ├── KbChunk.java         # 文本块（持久化用于向量重建）
│       │   │   │   ├── SearchResult.java    # Phase 4 搜索结果 DTO
│       │   │   │   ├── UserActionLog.java   # Phase 4 用户行为日志
│       │   │   │   └── ChatConversationSummary.java  # 对话摘要
│       │   │   ├── mapper/                  # MyBatis-Plus Mapper（8 个，BaseMapper + 自定义 SQL）
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
│       │   │   ├── agent/                   # AI Agent 智能体（Phase 3）
│       │   │   │   ├── AgentService.java    # Agent 服务接口
│       │   │   │   ├── event/
│       │   │   │   │   └── AgentEvent.java  # 结构化 SSE 事件 record（6 种事件类型）
│       │   │   │   ├── tool/
│       │   │   │   │   ├── Tool.java        # 工具标记接口
│       │   │   │   │   ├── WebSearchTool.java   # Tavily Search API 网页搜索
│       │   │   │   │   ├── MathTool.java        # exp4j 安全数学表达式求值
│       │   │   │   │   └── CodeExecutionTool.java # JS ScriptEngine 沙箱代码执行
│       │   │   │   ├── memory/
│       │   │   │   │   └── RedisChatMemoryStore.java # LangChain4j ChatMemoryStore Redis 实现
│       │   │   │   ├── graph/               # 多 Agent 编排（Phase 3.5）
│       │   │   │   │   ├── AgentState.java  # 图状态"黑板"（SpecialistResult record）
│       │   │   │   │   ├── AgentNode.java   # 节点统一接口
│       │   │   │   │   ├── SupervisorAgent.java # LLM 零样本意图分类器
│       │   │   │   │   ├── ResearchAgent.java   # 研究搜索专家（WebSearchTool）
│       │   │   │   │   ├── MathAgent.java       # 数学计算专家（MathTool）
│       │   │   │   │   ├── CodeAgent.java       # 代码执行专家（CodeExecutionTool）
│       │   │   │   │   └── AgentGraph.java      # 多 Agent 编排器（Supervisor+Summarizer）
│       │   │   │   └── impl/
│       │   │   │       └── AgentServiceImpl.java # 核心实现：两阶段流式 + ReAct + 多 Agent
│       │   └── resources/
│       │       ├── application.yml          # 应用配置（数据源/Redis/AI/微信/RAG）
│       │       ├── application-example.yml  # 配置示例模板
│       │       ├── init.sql                 # 数据库初始化（建表 + 索引）
│       │       └── db/migration/            # 数据库迁移脚本
│       │           ├── DB_MIGRATION.sql     # 完整迁移（所有表）
│       │           ├── V2__chat_tables.sql  # AI 对话表（conversation + message）
│       │           └── V3__rag_tables.sql   # RAG 知识库/文档/块表
│       └── test/                            # 单元测试（403 个，JUnit 5 + Mockito + MockMvc）
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
│               ├── agent/                   # Agent 测试（6 个）
│               │   ├── event/
│               │   │   └── AgentEventTest.java
│               │   ├── tool/
│               │   │   ├── WebSearchToolTest.java
│               │   │   ├── MathToolTest.java
│               │   │   └── CodeExecutionToolTest.java
│               │   ├── memory/
│               │   │   └── RedisChatMemoryStoreTest.java
│               │   ├── graph/               # 多 Agent 编排测试
│               │   │   ├── AgentStateTest.java
│               │   │   ├── SupervisorAgentTest.java
│               │   │   └── AgentGraphTest.java
│               │   └── impl/
│               │       └── AgentServiceImplTest.java
│               ├── controller/              # Controller 测试（3 个）
│               │   ├── UserControllerTest.java
│               │   ├── RagControllerTest.java
│               │   └── AgentControllerTest.java
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
├── FIX_Document/                             # 问题修复文档
│   ├── P0_SECURITY_FIX.md                   # P0 安全修复（Token 类型校验 + 暴力破解防护）
│   ├── P1_SECURITY_FIX.md                   # P1 安全修复
│   └── Windows_Reserved_Port_8080_FIX.md    # Windows 保留端口 8080 排查修复
└── Project Detail Guide/                     # 项目构建详细指南
    ├── WECHAT_SETUP_GUIDE.md                # 微信扫码登录完整配置指南
    ├── 01-authentication-system.md            # Phase 1 用户认证系统构建教程
    ├── 02-ai-chat.md                          # Phase 2 AI 智能对话构建教程
    ├── 03-rag-knowledge-base.md               # Phase 3 RAG 知识库构建教程
    ├── 04-ai-agent.md                         # Phase 4 AI Agent 智能体构建教程
    └── 05-smart-search-analytics.md           # Phase 5 智能搜索与分析构建教程
    └── old-readme
        ├── README1.md                               # README 历史版本
		└── README2.md                               # README 历史版本
```

---

## API 概览

### 用户认证

| 方法 | 路径                              |        认证         | 说明                                  |
| ---- | --------------------------------- | :-----------------: | ------------------------------------- |
| GET  | `/user/captcha`                   |         否          | 获取图形验证码                        |
| POST | `/user/send-code`                 |         否          | 发送邮箱验证码（注册用）              |
| POST | `/user/register`                  |         否          | 邮箱验证码注册                        |
| POST | `/user/login`                     |         否          | 密码登录（图形验证码 + 5次失败锁定）  |
| POST | `/user/logout`                    |     accessToken     | 登出                                  |
| POST | `/user/refresh`                   |    refreshToken     | 刷新 accessToken（强制 type=refresh） |
| GET  | `/user/info`                      |     accessToken     | 鉴权探针                              |
| GET  | `/user/me`                        |     accessToken     | 获取当前用户信息（含角色）            |
| POST | `/user/wechat/qrcode`             |         否          | 生成微信扫码场景                      |
| GET  | `/user/wechat/check`              |         否          | 轮询扫码状态                          |
| GET  | `/user/wechat/callback`           |         否          | 微信 OAuth 回调                       |
| POST | `/user/wechat/bind-email`         |         否          | 微信扫码后绑定邮箱                    |
| POST | `/user/forgot-password/send-code` |         否          | 发送密码重置验证码                    |
| POST | `/user/forgot-password/reset`     |         否          | 验证码校验 + 重置密码                 |
| GET  | `/user/admin/users`               | accessToken + admin | 管理员分页查询所有用户                |

### AI 对话

| 方法   | 路径                               | 说明                                   | 认证 |
| ------ | ---------------------------------- | -------------------------------------- | :--: |
| POST   | `/ai/chat/stream`                  | SSE 流式对话                           |  ✅  |
| POST   | `/ai/chat/rag-stream`              | SSE RAG 流式对话（传 knowledgeBaseId） |  ✅  |
| GET    | `/ai/conversations`                | 获取对话列表                           |  ✅  |
| POST   | `/ai/conversations`                | 新建对话                               |  ✅  |
| GET    | `/ai/conversations/{id}`           | 获取对话消息                           |  ✅  |
| DELETE | `/ai/conversations/{id}`           | 删除对话（移至回收站）                 |  ✅  |
| GET    | `/ai/conversations/trash`          | 获取回收站列表                         |  ✅  |
| POST   | `/ai/conversations/{id}/restore`   | 恢复已删除对话                         |  ✅  |
| DELETE | `/ai/conversations/{id}/permanent` | 永久删除对话                           |  ✅  |

### AI Agent 智能体 API

| 方法 | 路径                 | 说明                                           | 认证 |
| ---- | -------------------- | ---------------------------------------------- | :--: |
| POST | `/agent/chat/stream` | Agent SSE 流式对话（支持工具调用，结构化事件） |  ✅  |

> Agent 端点采用**两阶段流式**架构：非流式模型推理循环 + 流式模型最终输出。SSE 事件：`thinking`/`tool_call`/`tool_result`/`token`/`done`/`error`。

### RAG 知识库

| 方法   | 路径                                                      | 说明                         | 认证 |
| ------ | --------------------------------------------------------- | ---------------------------- | :--: |
| POST   | `/rag/knowledge-bases`                                    | 创建知识库                   |  ✅  |
| GET    | `/rag/knowledge-bases`                                    | 列出用户的知识库             |  ✅  |
| GET    | `/rag/knowledge-bases/{id}`                               | 获取知识库详情 + 文档列表    |  ✅  |
| PUT    | `/rag/knowledge-bases/{id}`                               | 更新知识库名称/描述          |  ✅  |
| DELETE | `/rag/knowledge-bases/{id}`                               | 软删除知识库                 |  ✅  |
| POST   | `/rag/knowledge-bases/{id}/documents`                     | 上传文档（multipart，≤10MB） |  ✅  |
| GET    | `/rag/knowledge-bases/{id}/documents`                     | 列出文档（含处理状态和块数） |  ✅  |
| DELETE | `/rag/knowledge-bases/{id}/documents/{docId}`             | 删除文档                     |  ✅  |
| POST   | `/rag/knowledge-bases/{id}/query`                         | 测试检索（返回相关块+分数）  |  ✅  |
| GET    | `/rag/recycle-bin`                                        | 获取知识库回收站列表         |  ✅  |
| PUT    | `/rag/recycle-bin/{kbId}/restore`                         | 恢复知识库及文档             |  ✅  |
| DELETE | `/rag/recycle-bin/{kbId}`                                 | 永久删除知识库（不可逆）     |  ✅  |
| GET    | `/rag/knowledge-bases/{kbId}/recycle-bin`                 | 获取文档回收站列表（按KB）   |  ✅  |
| PUT    | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}/restore` | 恢复文档（重新嵌入向量）     |  ✅  |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}`         | 永久删除文档（不可逆）       |  ✅  |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin`                 | 清空文档回收站               |  ✅  |

> 📖 在线调试：Docker启动后访问 `http://localhost/doc.html`（Knife4j 接口文档，支持在线发送请求）

### 智能搜索与分析

| 方法 | 路径                             | 说明                                                   | 认证 |
| ---- | -------------------------------- | ------------------------------------------------------ | :--: |
| GET  | `/search/messages`               | 全文搜索消息（MySQL FULLTEXT + ngram 中文分词 + 高亮） |  ✅  |
| GET  | `/statistics/overview`           | 数据总览（总会话数/总消息数/活跃天数/AI 使用率）       |  ✅  |
| GET  | `/statistics/message-trend`      | 消息趋势折线图数据（分 user/assistant 统计）           |  ✅  |
| GET  | `/statistics/active-hours`       | 24 小时活跃热力图数据                                  |  ✅  |
| GET  | `/statistics/conversation-trend` | 对话创建趋势                                           |  ✅  |
| GET  | `/statistics/message-ratio`      | 用户 vs AI 消息占比（饼图数据）                        |  ✅  |
| GET  | `/statistics/knowledge-stats`    | 知识库使用统计                                         |  ✅  |
| GET  | `/statistics/action-ranking`     | 功能使用排行（从 user_action_log 聚合）                |  ✅  |
| GET  | `/statistics/weekly-activity`    | 最近 7 天每日活跃操作次数                              |  ✅  |
| GET  | `/recommend/suggestions`         | 智能推荐（相关对话/建议问题/热门知识库）               |  ✅  |

> 行为追踪通过 `@TrackAction` 注解自动采集，无需前端额外调用。统计数据使用 Redis TTL 分级缓存（30min~4h）。

---

## 架构设计

### 整体架构

```
┌────────────────────┐     SSE/JSON/HTTP    ┌───────────────────┐     OpenAI API       ┌────────────┐
│    Vue 3 前端      │ ←─────────────────→ │  Spring Boot 后端 │ ←────────────────────→ │  DeepSeek │
│                    │     Nginx 代理       │                   │     HTTPS             │    API    │
│  • Login/Register  │                      │  • 用户认证 (JWT) │                       │           │
│  • Chat.vue        │                      │  • AI 对话 (LLM)  │                       │           │
│  • KnowledgeBase   │                      │  • RAG 知识库     │                       │           │
│  • Search.vue      │                      │  • Agent 智能体   │                       │           │
│  • Dashboard.vue   │                      │  • 全文搜索       │                       │           │
│  • RecommendCard   │                      │  • 数据统计       │                       │           │
└────────┬───────────┘                      │  • 行为追踪       │                       │           │
         │                                  │  • 智能推荐       │                       │           │
         │ localStorage                     └─────────┬─────────┘                       └───────────┘
         │ (JWT Token)                                │                                       │
         ▼                                            │ MySQL (用户/对话/消息/知识库/行为日志)   │ Embedding API
┌──────────────────┐                                  │ Redis (Token/验证码/状态/统计缓存)      │ (OpenAI 兼容)
│    浏览器        │                                  ▼                                        ▼
└──────────────────┘                      ┌───────────────────┐                          ┌───────────┐
                                          │     数据库         │                          │ Embedding │
                                          └───────────────────┘                          │  Service  │
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

### 全文搜索引擎

```
用户输入关键词（Search.vue, 300ms debounce）
  → GET /search/messages?q=微服务&page=1&size=20
    → SearchService.searchMessages()
      ├─ validateKeyword() (非空 + 200 字限制)
      ├─ escapeFulltextChars() (转义 + - > < 等 FULLTEXT 特殊字符)
      ├─ ChatMessageMapper.fulltextSearch()
      │   └─ SELECT ... MATCH(content) AGAINST('微服务' IN BOOLEAN MODE)
      │       └─ InnoDB FULLTEXT 引擎 (ngram: 微服/服务)
      ├─ highlight() Java 层正则替换 → <mark>微服务</mark>
      └─ 返回 { total, page, list: [{..., highlightContent}] }

前端渲染: v-html="DOMPurify.sanitize(highlightContent)"
  → 关键词黄色高亮 + 点击标题跳转 /chat?conversationId=X
```

### 用户行为追踪

```
用户操作（如发送消息）
  → @TrackAction("message_send")
    → ActionLogAspect.logAction() [主线程 @AfterReturning]
      ├─ email = request.getAttribute("userEmail")  ← Request 回收前提取
      ├─ action = trackAction.value()
      ├─ ip = request.getHeader("X-Forwarded-For")
      └─ actionLogWriter.write(email, action, ip)   ← 跨 Bean 委托
            → @Async 异步线程 (action-log-*)
              ├─ resolveUserId(email) → userId
              └─ INSERT user_action_log(userId, action, ip)
```

### 智能推荐

```
Chat.vue 侧边栏 RecommendCard
  → GET /recommend/suggestions
    → RecommendService.getRecommendations()
      ├─ findRelatedConversations()
      │   ├─ 加载最近 10 条用户消息
      │   ├─ extractKeywords() (Bigram 分词 + 停止词过滤 → Top 5)
      │   ├─ MATCH AGAINST(keywords) → 排除最近对话 → Top 3
      │   └─ 返回 [{conversationId, title, matchCount}]
      ├─ generateSuggestedQuestions()
      │   └─ Bigram 主题匹配模板库 → 返回 3 个建议问题
      └─ findPopularKnowledge()
          └─ 按文档数量降序 → Top 3
```

### AI Agent 推理与编排

```
用户消息 → AgentController → AgentServiceImpl
  ├── 限流（Redis ZSET 滑动窗口，10 次/分钟）
  ├── 注入检测（18 种中英文 Prompt Injection 模式）
  ├── 模式判断：
  │   ├── multi-agent.enabled=true（多 Agent 编排）
  │   │   └── AgentGraph.execute()
  │   │       ├── SupervisorAgent.classify()    ← LLM 零样本意图分类
  │   │       ├── ResearchAgent  (WebSearchTool)   ─┐
  │   │       ├── MathAgent      (MathTool)         ├─ 专家执行
  │   │       ├── CodeAgent      (CodeExecutionTool) ┘
  │   │       └── Summarizer.aggregate() → 流式输出
  │   │
  │   ├── tools 非空（标准 Agent ReAct 循环）
  │   │   └── for (i < 5):
  │   │       ├── 非流式 ChatModel 推理 → thinking 事件
  │   │       ├── tool_call → 执行工具 → tool_result 事件
  │   │       └── LLM 分析结果 → 继续/跳出循环
  │   │       └── 流式 StreamingChatModel 输出最终回答
  │   │
  │   └── tools 为空（降级为增强对话）
  │       └── 流式直接回答
  │
  └── 保存消息 + 触发摘要生成（每 10 条消息异步生成 ≤300 字摘要）

SSE 事件流: thinking → tool_call → tool_result → token → done/error
```

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

| 层级        | 机制                  | 说明                                                        |
| ----------- | --------------------- | ----------------------------------------------------------- |
| 传输层      | HTTPS（生产环境）     | ngrok 开发环境自动提供 HTTPS                                |
| 认证层      | JWT HMAC-SHA256       | 签名防篡改，30min 短时效降低泄露风险                        |
| 类型校验    | JWT`type` claim       | `/refresh` 强制校验 `type=refresh`，防 accessToken 无限续期 |
| 密码存储    | BCrypt                | 自适应哈希，自带随机盐，抗彩虹表                            |
| 防刷        | 图形验证码 + 60s 间隔 | 发送验证码前校验，同邮箱 60 秒内不可重复发送                |
| 防暴力破解  | Redis INCR + HTTP 429 | 5 次失败锁定 15 分钟，TTL 仅首次设置防绕过                  |
| 防用户枚举  | 统一错误提示          | 所有登录失败统一返回「邮箱或密码错误」                      |
| 单设备登录  | Redis Token 缓存      | 新登录自动踢掉旧设备                                        |
| XSS 防护    | DOMPurify             | 过滤 AI 输出中的潜在恶意 HTML/JS                            |
| AI 注入防护 | System Prompt 规则    | 禁止模型执行敏感操作，限制回答范围                          |
| CSRF        | 显式禁用 + JWT 无状态 | JWT 不依赖 Cookie，天然免疫 CSRF                            |
| OAuth 安全  | state 参数（sceneId） | 防 CSRF 攻击，回调时校验                                    |

---

## 技术栈

### 后端

| 技术            | 版本     | 用途                                                                |
| --------------- | -------- | ------------------------------------------------------------------- |
| Spring Boot     | 3.5.11   | 基础框架                                                            |
| MyBatis-Plus    | 3.5.12   | ORM（BaseMapper 免写 SQL + 分页插件）                               |
| MySQL           | 8.x      | 持久化存储                                                          |
| Redis           | 7.x      | Token 缓存 + 验证码 + 登录失败计数 + 微信状态                       |
| LangChain4j     | 1.15.0   | AI 应用框架（Java 原生，流式对话 + Embedding + Tool Calling Agent） |
| DeepSeek Chat   | V3       | 大语言模型（兼容 OpenAI API）                                       |
| Apache Tika     | 2.9.2    | 文档文本提取（PDF/DOCX/DOC/TXT/MD）                                 |
| Tavily Search   | —        | Agent 网页搜索工具 API                                              |
| exp4j           | 0.4.8    | Agent 安全数学表达式求值引擎                                        |
| Spring Security | 6.x      | BCrypt 密码加密                                                     |
| jjwt            | 0.12.6   | JWT 生成/解析/类型校验                                              |
| JavaMail        | 3.5.11   | 163 邮箱 SMTP 验证码发送                                            |
| Java AWT        | JDK 内置 | 图形验证码绘制                                                      |
| Jackson         | 2.x      | JSON 序列化                                                         |
| Knife4j         | 4.5.0    | OpenAPI 3 接口文档 + 在线调试                                       |
| WebFlux         | —        | SSE 流式响应（`Flux<String>`）                                      |
| Spring AOP      | —        | 用户行为追踪切面（@TrackAction + @Async 异步日志）                  |

### 前端

| 技术         | 版本 | 用途                                           |
| ------------ | ---- | ---------------------------------------------- |
| Vue          | 3.5  | 前端框架（Composition API）                    |
| Vite         | 8.x  | 构建工具（Rolldown 生产构建）                  |
| Element Plus | 2.14 | UI 组件库（按需导入）                          |
| Vue Router   | 4.x  | 前端路由 + 导航守卫                            |
| Axios        | 1.16 | HTTP 客户端 + 拦截器                           |
| qrcode       | 1.5  | 前端 Canvas 二维码生成                         |
| marked       | —    | Markdown → HTML 转换                           |
| highlight.js | —    | 代码语法高亮（190+ 语言）                      |
| DOMPurify    | —    | XSS 防护（过滤 AI 输出）                       |
| ECharts      | 6    | 数据可视化图表（折线图/柱状图/饼图，按需导入） |
| 原生 fetch   | —    | SSE 流式读取（Axios 不支持 ReadableStream）    |

---

## 测试

**框架**：JUnit 5 + Mockito + Spring MockMvc (standalone setup)，403 个测试，纯单元测试 — 无需 MySQL/Redis/网络，CI 就绪。

```bash
cd springboot
mvn test   # ~10 秒，0 failures
```

**三层 Mock 策略**：

| 层级                          | 模式                                | 测试类                                                                  |
| ----------------------------- | ----------------------------------- | ----------------------------------------------------------------------- |
| 纯 JUnit 5                    | `@Test` only                        | `ResponseUtilTest`, `CaptchaUtilTest`, `JwtUtilTest`                    |
| Mockito + ReflectionTestUtils | `@InjectMocks` / `@Mock` / `@Spy`   | `UserServiceImplTest`, `LoginInterceptorTest`, `RagServiceImplTest`     |
| Standalone MockMvc            | `MockMvcBuilders.standaloneSetup()` | `UserControllerTest`, `RagControllerTest`, `GlobalExceptionHandlerTest` |

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
- [x] 212 个单元测试（含 RAG 知识库 + Agent 工具 + AgentEvent 事件测试）

### ✅ Phase 3：AI Agent 智能体（已完成）

- [x] **3.1 LangChain4j Tool Calling 基础框架** — 两阶段流式架构（非流式推理 + 流式输出）、AgentService/AgentController、结构化 SSE 事件协议（thinking/tool_call/tool_result/token/done/error）、33 个新测试
- [x] **3.2 内置工具** — WebSearchTool（Tavily API）、MathTool（exp4j 安全求值）、CodeExecutionTool（JS ScriptEngine 沙箱）、55 个新工具测试
- [x] **3.3 Agent 可视化推理过程** — Chat.vue 推理面板、Agent 开关、思考指示器、工具调用展示、18 个新测试
- [x] **3.4 记忆系统** — MessageWindowChatMemory + RedisChatMemoryStore + 对话摘要、24 个新测试
- [x] **3.5 多 Agent 编排** — SupervisorAgent 意图分类 + ResearchAgent/MathAgent/CodeAgent 专家 + AgentGraph 编排器 + Summarizer 聚合 + 36 个新测试

### ✅ Phase 4：智能搜索与分析（已完成）

- [x] 全文搜索引擎（MySQL FULLTEXT + ngram 中文分词）
- [x] 对话数据分析仪表盘（ECharts 6 + Redis 缓存）
- [x] 用户行为分析（AOP + @TrackAction + @Async）
- [x] 智能推荐（关键词提取 + FULLTEXT 匹配）

---

## 文档索引

| 文档                                                                                  | 说明                                                     |
| ------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| [CLAUDE.md](CLAUDE.md)                                                                | 项目架构与开发规范（含 RAG + Agent 完整文档）            |
| [01-authentication-system.md](Project%20Detail%20Guide/01-authentication-system.md)   | 用户认证系统 28 步构建教程（后端+前端）                  |
| [02-ai-chat.md](Project%20Detail%20Guide/02-ai-chat.md)                               | AI 智能对话：DeepSeek + SSE 流式 + Markdown 渲染         |
| [03-rag-knowledge-base.md](Project%20Detail%20Guide/03-rag-knowledge-base.md)         | RAG 知识库：Tika + Embedding + 向量检索 + 两级回收站     |
| [04-ai-agent.md](Project%20Detail%20Guide/04-ai-agent.md)                             | AI Agent 智能体：Tool Calling + 多 Agent 编排 + 记忆系统 |
| [05-smart-search-analytics.md](Project%20Detail%20Guide/05-smart-search-analytics.md) | 智能搜索与分析：FULLTEXT + ECharts + 行为追踪 + 推荐     |
| [WECHAT_SETUP_GUIDE.md](Project%20Detail%20Guide/WECHAT_SETUP_GUIDE.md)               | 微信扫码登录完整配置指南                                 |
| [P0_SECURITY_FIX.md](FIX_Document/P0_SECURITY_FIX.md)                                 | P0 安全修复记录：Token 类型校验 + 暴力破解防护           |
| [P1_SECURITY_FIX.md](FIX_Document/P1_SECURITY_FIX.md)                                 | P1 安全修复记录                                          |
| [PHASE3_AGENT_FIX.md](FIX_Document/PHASE3_AGENT_FIX.md)                               | Phase 3 Agent 智能体修复记录（12 项）                    |
| [LOCAL_VS_DOCKER_PROXY.md](FIX_Document/LOCAL_VS_DOCKER_PROXY.md)                     | 本地开发 vs Docker 代理配置同步指南（Nginx + Vite）      |
| [Windows_Reserved_Port_8080_FIX.md](FIX_Document/Windows_Reserved_Port_8080_FIX.md)   | Windows 保留端口 8080 排查修复                           |
| [DB_MIGRATION.sql](springboot/src/main/resources/db/migration/DB_MIGRATION.sql)       | 数据库完整迁移脚本（所有表 + 索引）                      |

---

## 📄License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
