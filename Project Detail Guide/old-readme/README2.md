# Spring Boot + Vue3 AI 智能对话与 RAG 知识库系统 + AI Agent 智能体

> 基于 LangChain4j + DeepSeek 的智能对话系统 + RAG 知识库 + AI Agent 智能体，集成 JWT 双 Token 认证、SSE 流式传输、文档向量检索增强生成、工具调用推理、Markdown 渲染、Docker 一键部署。

---

## 项目简介

在已有的用户认证系统（JWT 双 Token + 微信 OAuth + RBAC）基础上，新增 **AI 智能对话**、**RAG 知识库** 和 **AI Agent 智能体** 三大核心功能。用户登录后可与 DeepSeek 大模型实时对话，支持多轮上下文、对话历史管理、Markdown 代码高亮渲染；亦可上传文档构建专属知识库，让 AI 回答基于用户自己的文档内容；还可启用 AI Agent 智能体，让 AI 自主调用搜索、计算、代码执行等工具完成复杂任务。

### 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 AI 智能对话 | DeepSeek-V3 大模型驱动，中英文对话、代码生成、数据分析 |
| 🤖 AI Agent 智能体 | LangChain4j Tool Calling 框架，AI 可自主调用工具（搜索/计算/代码执行）完成复杂任务 |
| 🧠 多 Agent 协作 | Supervisor 意图分类 → 专家 Agent 执行（研究/数学/代码）→ Summarizer 聚合结果，三层降级保障 |
| 📡 SSE 流式传输 | 逐字输出，打字机效果，120 秒超时支持长回复 |
| 📝 Markdown 渲染 | 完整 Markdown 支持：标题、列表、表格、引用、代码块（190+ 语言语法高亮） |
| 🔒 XSS 安全过滤 | DOMPurify 过滤 AI 输出中的潜在恶意 HTML/JS |
| 💬 多轮上下文 | 自动携带最近 20 条历史消息，支持连续对话 |
| 📂 对话管理 | 新建、切换、删除对话，自动从首条回复生成标题 |
| 🛑 中途停止 | 随时停止 AI 生成，已输出内容保留 |
| 🔐 认证保护 | 所有 AI/Agent 接口需 JWT 登录，复用现有拦截器链 |
| 📚 RAG 知识库 | 上传 PDF/DOCX/TXT/MD → 自动解析分块 → 向量嵌入 → 检索增强生成 |
| 🔍 向量检索 | OpenAI 兼容 Embedding API + 余弦相似度检索，内存向量库 + MySQL 持久化 |
| 📄 文档管理 | 知识库卡片列表、文档上传、处理状态轮询、检索测试面板 |
| ♻️ 两级回收站 | 知识库级回收站（全局）+ 文档级回收站（按知识库），支持恢复（自动重嵌入向量）和永久删除（清理文件/向量/DB） |
| 🔄 启动重建 | 应用重启自动从 MySQL kb_chunk 表重建向量索引，无需额外基础设施 |

---

## 技术栈

### 后端

| 技术 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 3.5.11 | 基础框架 |
| LangChain4j | 1.15.0 | AI 应用框架（Java 原生，对标 Python LangChain），含 Tool Calling |
| DeepSeek Chat | V3 | 大语言模型（兼容 OpenAI API） |
| WebFlux | — | SSE 流式响应（`Flux<String>`） |
| Tavily Search | — | Agent 网页搜索工具（1000次/月免费额度） |
| exp4j | 0.4.8 | Agent 安全数学表达式求值引擎 |
| MyBatis-Plus | 3.5.12 | 对话/消息/知识库持久化 |
| MySQL | 8.x | 存储对话、消息、知识库、文档、文本块 |
| Redis | 7.x | Token 缓存（复用认证系统） |
| Apache Tika | 2.9.2 | 文档文本提取（PDF/DOCX/DOC/TXT/MD） |
| LangChain4j Embedding | 1.15.0 | OpenAI 兼容 Embedding 模型 |

### 前端

| 技术 | 版本 | 作用 |
|------|------|------|
| Vue 3 | 3.5 | 前端框架 |
| marked | — | Markdown → HTML 转换 |
| highlight.js | — | 代码语法高亮（190+ 语言） |
| DOMPurify | — | XSS 防护（过滤 AI 输出） |
| Element Plus | 2.14 | UI 组件库 |
| 原生 fetch | — | SSE 流式读取（Axios 不支持） |

---

## 快速开始

### 1. 获取 DeepSeek API Key

1. 注册 [platform.deepseek.com](https://platform.deepseek.com)
2. 创建 API Key
3. 新用户赠送 500 万 token，足够开发测试

### 2. 配置环境变量

编辑项目根目录的 `.env` 文件：

```env
# ===== AI 对话（DeepSeek）=====
AI_API_KEY=sk-your-deepseek-api-key-here
AI_BASE_URL=https://api.deepseek.com/v1
AI_MODEL_NAME=deepseek-chat
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120

# ===== RAG 嵌入模型（OpenAI 兼容）=====
# 可选：OpenAI / 硅基流动(SiliconFlow) / 本地Ollama
AI_EMBEDDING_API_KEY=sk-your-embedding-key
AI_EMBEDDING_BASE_URL=https://api.openai.com/v1
AI_EMBEDDING_MODEL=text-embedding-3-small

# ===== Agent 智能体（Phase 3）=====
TAVILY_API_KEY=tvly-your-tavily-api-key
# 工具开关（默认已启用搜索和数学，代码执行需手动开启）
# AGENT_TOOL_WEB_SEARCH=true
# AGENT_TOOL_MATH=true
# AGENT_TOOL_CODE_EXEC=false
```

> **嵌入 API 提供商推荐**：DeepSeek 暂不支持 Embedding API。推荐 [硅基流动](https://siliconflow.cn) — 国内直连、OpenAI 兼容、价格便宜。

### 3. Docker Compose 一键启动

```bash
docker compose up -d --build
```

访问：
- **前端**：http://localhost
- **API 文档**：http://localhost/doc.html
- **健康检查**：http://localhost:18080/actuator/health

### 4. 手动启动（开发模式）

```bash
# 后端
cd springboot
mvn spring-boot:run

# 前端
cd web/frontend
npm install
npm run dev
```

访问 http://localhost:3000

---

## 功能截图

### AI 对话界面

```
┌─────────────────────────────────────────────────────────────┐
│ [≡]  返回主页                                                │
├──────────┬──────────────────────────────────────────────────┤
│ [+新对话] │                                                  │
│          │        🤖 你好！我是 DeepSeek AI 助手              │
│ ▸ 对话 1  │           [推荐问题: 用Java写单例...]              │
│   对话 2  │                                                  │
│          │  👤 帮我写一个快速排序                              │
│          │                                                  │
│          │  🤖 好的，以下是 Java 实现的快速排序：              │
│          │  ```java                                         │
│          │  public class QuickSort {                         │
│          │      public void sort(int[] arr) { ... }         │
│          │  }                                               │
│          │  ```                                             │
│          │     [复制]                                        │
│          │                                                  │
│          │  ┌────────────────────────┐ [发送]                │
│          │  │ 输入消息...             │                       │
│          │  └────────────────────────┘                       │
│          │  AI 由 DeepSeek 大模型驱动，内容仅供参考              │
└──────────┴──────────────────────────────────────────────────┘
```

---

## API 接口

### AI 对话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/ai/chat/stream` | SSE 流式对话 | ✅ |
| GET | `/ai/conversations` | 获取对话列表 | ✅ |
| POST | `/ai/conversations` | 新建对话 | ✅ |
| GET | `/ai/conversations/{id}` | 获取对话消息 | ✅ |
| DELETE | `/ai/conversations/{id}` | 删除对话（移至回收站） | ✅ |
| GET | `/ai/conversations/trash` | 获取回收站列表 | ✅ |
| POST | `/ai/conversations/{id}/restore` | 恢复已删除对话 | ✅ |
| DELETE | `/ai/conversations/{id}/permanent` | 永久删除对话 | ✅ |
| POST | `/ai/chat/rag-stream` | SSE RAG 流式对话（需传 knowledgeBaseId） | ✅ |

### AI Agent 智能体

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/agent/chat/stream` | Agent SSE 流式对话（支持工具调用） | ✅ |

> Agent 端点采用**两阶段流式**架构：先用非流式模型完成"思考→工具调用→结果分析"的 ReAct 推理循环，再用流式模型输出最终回答。SSE 事件协议支持 `thinking` / `tool_call` / `tool_result` / `token` / `done` / `error` 六种结构化事件。

### RAG 知识库

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/rag/knowledge-bases` | 创建知识库 | ✅ |
| GET | `/rag/knowledge-bases` | 列出用户的知识库（含文档数） | ✅ |
| GET | `/rag/knowledge-bases/{id}` | 获取知识库详情 + 文档列表 | ✅ |
| PUT | `/rag/knowledge-bases/{id}` | 更新知识库名称/描述 | ✅ |
| DELETE | `/rag/knowledge-bases/{id}` | 软删除知识库 | ✅ |
| POST | `/rag/knowledge-bases/{id}/documents` | 上传文档（MultipartFile） | ✅ |
| GET | `/rag/knowledge-bases/{id}/documents` | 列出文档（含处理状态和块数） | ✅ |
| DELETE | `/rag/knowledge-bases/{id}/documents/{docId}` | 删除文档 | ✅ |
| POST | `/rag/knowledge-bases/{id}/query` | 测试检索（返回相关块+相关度分数） | ✅ |
| GET | `/rag/recycle-bin` | 获取知识库回收站列表 | ✅ |
| PUT | `/rag/recycle-bin/{kbId}/restore` | 恢复知识库及文档 | ✅ |
| DELETE | `/rag/recycle-bin/{kbId}` | 永久删除知识库（不可逆） | ✅ |
| GET | `/rag/knowledge-bases/{kbId}/recycle-bin` | 获取文档回收站列表（按KB） | ✅ |
| PUT | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}/restore` | 恢复文档 | ✅ |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}` | 永久删除文档（不可逆） | ✅ |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin` | 清空文档回收站 | ✅ |

### 认证接口（已有）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/register` | 邮箱验证码注册 |
| POST | `/user/login` | 密码登录（返回双 Token） |
| POST | `/user/refresh` | 刷新 accessToken |
| POST | `/user/logout` | 登出 |
| POST | `/user/wechat/qrcode` | 微信扫码登录 |
| GET | `/user/captcha` | 获取图形验证码 |

---

## 架构设计

### 整体架构

```
┌─────────────┐     SSE/JSON      ┌──────────────┐     OpenAI API     ┌──────────┐
│   Vue 3     │ ←───────────────→ │ Spring Boot  │ ←───────────────→ │ DeepSeek │
│   前端      │    Nginx 代理      │   后端        │    HTTPS          │   API    │
│   Chat.vue  │                   │ AiChatService│                   │          │
│   Knowledge │                   │ RagService   │                   │          │
│   Base.vue  │                   │              │                   │          │
└─────────────┘                   └──────────────┘                   └──────────┘
      │                                 │                                 │
      │ localStorage                    │ MySQL + Redis                   │ Embedding API
      │ (JWT Token)                     │ (对话/消息/知识库/向量块)         │ (OpenAI兼容)
      ▼                                 ▼                                 ▼
┌─────────────┐                   ┌──────────────┐                   ┌──────────┐
│  浏览器      │                   │   数据库      │                   │ Embedding│
└─────────────┘                   └──────────────┘                   │  Service │
                                                                     └──────────┘
```

### RAG 知识库管道

```
上传文档 (PDF/DOCX/TXT/MD)
  → Apache Tika 文本提取
    → TextSplitterUtil 递归分块 (800 chars / 100 overlap)
      → OpenAiEmbeddingModel 向量嵌入
        → SimpleEmbeddingStore 内存向量库 (余弦相似度)
           ↓
RAG 对话 → embed(用户问题) → 检索相关块 → 注入 System Prompt → SSE 流式输出
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

### Agent 两阶段流式架构

```
用户消息 → AgentController → AgentServiceImpl
                                ├── 阶段1：非流式推理循环 (ChatModel)
                                │   ├── thinking 事件 → SSE
                                │   ├── tool_call 事件 → 执行工具 → SSE
                                │   └── tool_result 事件 → SSE
                                ├── 阶段2：流式最终回答 (StreamingChatModel)
                                │   ├── token 事件 → SSE
                                │   └── done 事件 → SSE
                                └── 保存对话记录
```

**为什么两阶段？** DeepSeek（以及大多数模型）不支持在流式模式下同时进行 function calling。Agent 需要先通过非流式 `ChatModel` 完成"思考→工具调用→结果分析"的 ReAct 循环，最后用 `StreamingChatModel` 流式输出最终回答。整个过程中通过结构化 SSE 事件实时推送推理进度，用户无需等待。

### 数据库设计

```sql
-- 用户表（已有）
user(id, email, password, role)

-- AI 对话会话表
chat_conversation(id, user_id → user.id, title, created_at, updated_at)

-- AI 对话消息表
chat_message(id, conversation_id → chat_conversation.id, role, content, created_at)
```

---

## 项目结构

```
springboot/
├── src/main/java/com/zora/
│   ├── config/
│   │   ├── AiConfig.java              # LangChain4j 流式 + 非流式模型配置
│   │   ├── AgentConfig.java           # Agent 智能体配置（工具开关/Tavily/多Agent/记忆）
│   │   ├── RagConfig.java             # Embedding 模型 + 向量存储配置
│   │   ├── Knife4jConfig.java         # OpenAPI 3 / Knife4j 文档配置
│   │   ├── SwaggerCompatController.java # /swagger-resources 兼容端点
│   │   ├── WebConfig.java             # 拦截器配置（已覆盖 /ai/**, /rag/**, /agent/**）
│   │   └── SecurityConfig.java        # Spring Security（仅 BCrypt）
│   ├── agent/                         # 🆕 Agent 智能体（Phase 3）
│   │   ├── AgentService.java          # Agent 服务接口
│   │   ├── impl/AgentServiceImpl.java # 核心实现：两阶段流式 + ReAct + 多 Agent（~950行）
│   │   ├── tool/Tool.java             # 工具标记接口（所有工具实现此接口）
│   │   ├── event/AgentEvent.java      # 结构化 SSE 事件 record + withField 增强
│   │   ├── memory/
│   │   │   └── RedisChatMemoryStore.java # LangChain4j ChatMemoryStore Redis 实现
│   │   └── graph/                     # 🆕 多 Agent 编排（Phase 3.5）
│   │       ├── AgentState.java        # 图状态"黑板"（SpecialistResult record）
│   │       ├── AgentNode.java         # 节点统一接口
│   │       ├── SupervisorAgent.java   # LLM 零样本意图分类器
│   │       ├── ResearchAgent.java     # 研究搜索专家（WebSearchTool）
│   │       ├── MathAgent.java         # 数学计算专家（MathTool）
│   │       ├── CodeAgent.java         # 代码执行专家（CodeExecutionTool）
│   │       └── AgentGraph.java        # 多 Agent 编排器（Supervisor + Summarizer）
│   ├── controller/
│   │   ├── AgentController.java       # 🆕 Agent SSE 流式对话端点（/agent/chat/stream）
│   │   ├── AiChatController.java      # AI 对话 REST API + SSE（含 RAG 对话）
│   │   ├── RagController.java         # RAG 知识库 REST API（16 个端点）
│   │   └── UserController.java        # 用户认证 API
│   ├── service/
│   │   ├── AgentService.java          # （已在 agent/ 包中定义）
│   │   ├── AiChatService.java         # AI 对话核心逻辑（含 RAG 流式对话）
│   │   ├── RagService.java            # 知识库 CRUD + 检索 + 文档管理 + 两级回收站
│   │   ├── RagProcessingService.java  # 文档处理管道 + 启动向量重建
│   │   ├── impl/
│   │   │   ├── AgentServiceImpl.java  # （已在 agent/impl/ 包中）
│   │   │   ├── AiChatServiceImpl.java # AI 对话实现（流式 + RAG 上下文注入）
│   │   │   ├── RagServiceImpl.java    # 知识库业务 + 回收站（~790 行）
│   │   │   ├── RagProcessingServiceImpl.java # Tika 解析 + 分块 + Embedding
│   │   │   └── SimpleEmbeddingStore.java     # 余弦相似度向量存储
│   │   └── UserService.java
│   ├── entity/
│   │   ├── AgentStep.java             # 🆕 瞬态 POJO，记录 Agent 推理步骤
│   │   ├── ChatConversation.java      # 对话会话实体
│   │   ├── ChatMessage.java           # 对话消息实体
│   │   ├── KnowledgeBase.java         # 知识库实体
│   │   ├── KbDocument.java            # 文档实体（含处理状态）
│   │   ├── KbChunk.java               # 文本块实体
│   │   └── User.java                  # 用户实体
│   ├── mapper/
│   │   ├── ChatConversationMapper.java
│   │   ├── ChatMessageMapper.java
│   │   ├── KnowledgeBaseMapper.java
│   │   ├── KbDocumentMapper.java
│   │   ├── KbChunkMapper.java
│   │   └── UserMapper.java
│   └── utils/
│       ├── FileTypeUtil.java          # 文件类型检测 + 大小校验
│       ├── TextSplitterUtil.java      # 递归文本分割器
│       └── ...（认证相关工具类）
├── src/main/resources/
│   ├── application.yml                # 配置文件（含 ai:, rag:, agent: 配置段）
│   └── db/migration/
│       ├── V2__chat_tables.sql        # AI 对话表
│       └── V3__rag_tables.sql         # RAG 知识库/文档/块表
└── pom.xml

web/frontend/
├── src/
│   ├── views/
│   │   ├── Chat.vue                   # AI 对话页面（含 RAG 开关 + Agent 模式 + 知识库选择器）
│   │   ├── KnowledgeBase.vue          # 知识库管理 + 两级回收站（~780 行）
│   │   ├── Home.vue                   # 首页（含 AI 入口 + 知识库入口）
│   │   ├── Login.vue                  # 登录页
│   │   └── Register.vue               # 注册页
│   ├── api/
│   │   ├── ai.js                      # AI API（含 SSE 流式 + RAG 流式）
│   │   ├── agent.js                   # 🆕 Agent SSE 客户端（结构化事件解析）
│   │   ├── rag.js                     # RAG API（知识库 CRUD + 上传 + 检索）
│   │   └── user.js                    # 用户 API
│   ├── router/index.js                # 路由配置（含 /chat, /knowledge）
│   └── utils/token.js                 # Token 管理
├── nginx.conf                         # Nginx 配置（含 /ai/, /rag/, /agent/ 代理）
└── vite.config.js                     # Vite 配置（含 /ai, /rag, /agent 代理）
```

---

## 开发指南

### 添加新的 AI 端点

1. 在 `AiChatController` 中添加方法
2. 在 `AiChatService` 中实现业务逻辑
3. 在 `api/ai.js` 中添加前端调用函数
4. `/ai/**` 路径自动被拦截器保护，无需额外配置

### 添加新的 Agent 工具

1. 创建工具类实现 `com.zora.agent.tool.Tool` 接口
2. 在方法上使用 `@Tool` 注解描述工具功能（LangChain4j 会读取并传给 LLM）
3. 在 `@P` 注解中描述每个参数的用途
4. 在 `application.yml` 的 `agent.tools.*.enabled` 中添加开关配置
5. 创建对应的单元测试类（Mock 外部 API 调用）
6. Spring 自动注入 `List<Tool>`，无需手动注册

### 切换 LLM 提供商

修改 `.env` 中的三个变量即可：

```env
# 切换到 OpenAI
AI_API_KEY=sk-your-openai-key
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL_NAME=gpt-4o

# 切换到本地 Ollama
AI_API_KEY=ollama
AI_BASE_URL=http://localhost:11434/v1
AI_MODEL_NAME=qwen2.5
```

LangChain4j 的 `OpenAiStreamingChatModel` 兼容所有 OpenAI API 格式的服务商。

### 自定义系统提示词

编辑 `AiChatServiceImpl.java` 中的 `SYSTEM_PROMPT` 常量：

```java
private static final String SYSTEM_PROMPT = 
    "你是一个有帮助的 AI 助手。请用中文回答问题。";
```

**RAG 模式**下（传 `knowledgeBaseId`），检索到的文档块会自动注入 System Prompt 前缀，AI 会优先根据知识库内容回答：

```
【知识库参考内容（请优先根据以下内容回答用户问题）】
[来源: spring-guide.pdf (第3段)] Spring Boot 是一个 Java 框架...
【知识库内容结束】

{原有 SYSTEM_PROMPT + 安全规则}
```

---

## 路线图

### ✅ Phase 1：AI 智能对话（已完成）

- LangChain4j + DeepSeek 集成
- SSE 流式传输
- 多轮上下文对话
- Markdown + 代码高亮渲染
- 对话历史管理
- Docker 一键部署

### ✅ Phase 2：RAG 知识库（已完成）

- Apache Tika 文档解析（PDF、DOCX、TXT、MD）
- 递归文本分块（800 字符 / 100 重叠）
- OpenAI 兼容 Embedding 向量化
- 自实现余弦相似度向量存储（SimpleEmbeddingStore）
- RAG 检索增强生成（System Prompt 注入）
- 知识库管理界面（卡片列表 + 文档表格 + 检索测试面板）
- 启动时 MySQL → 向量索引自动重建
- 371 个单元测试（含 RAG 知识库 + Agent 工具 + AgentEvent 事件测试 + 多 Agent 编排）

### ✅ Phase 3：AI Agent 智能体（已完成）

- ✅ **3.1 LangChain4j Tool Calling 基础框架** — 两阶段流式架构、AgentService/AgentController、结构化 SSE 事件协议（thinking/tool_call/tool_result/token/done/error）、33 个新测试
- ✅ **3.2 内置工具** — WebSearchTool（Tavily API）、MathTool（exp4j 安全求值）、CodeExecutionTool（JS ScriptEngine 沙箱执行）、55 个新工具测试
- ✅ **3.3 Agent 可视化推理过程** — Chat.vue 推理面板、Agent 开关、思考指示器、工具调用展示、18 个新测试
- ✅ **3.4 记忆系统** — MessageWindowChatMemory + RedisChatMemoryStore 持久化 + 对话摘要、24 个新测试
- ✅ **3.5 多 Agent 编排** — 自定义 StateGraph：SupervisorAgent 意图分类 + ResearchAgent/MathAgent/CodeAgent 专家 + AgentGraph 编排器 + Summarizer 聚合、36 个新测试

### 🔜 Phase 4：智能搜索与分析

- 全文搜索引擎
- 对话数据分析仪表盘
- 用户行为分析
- 智能推荐

---

## 常见问题

### Q: API Key 无效怎么办？

检查 `.env` 中的 `AI_API_KEY` 是否正确，确保没有多余的空格或换行。可以在 DeepSeek 控制台查看 key 状态。

### Q: AI 回复很慢怎么办？

- 检查网络连接到 `api.deepseek.com` 是否正常
- 减小 `AI_MAX_TOKENS` 值（如 2048）可以加快回复速度
- 使用 `deepseek-chat` 模型（比 `deepseek-reasoner` 快）

### Q: 如何查看 AI 请求日志？

后端启动时 `AiConfig` 中 `logRequests: true` 会打印完整的请求和响应。查看后端日志：

```bash
docker compose logs -f backend
```

### Q: SSE 流式传输中断怎么办？

- 检查 Nginx 配置中 `proxy_buffering off` 是否生效
- 检查 `proxy_read_timeout` 是否足够（建议 120s）
- 前端的 `AbortController` 可以主动取消请求

### Q: 如何添加更多 LLM 模型？

LangChain4j 支持 50+ 模型提供商。添加新依赖后在 `AiConfig` 中创建新的 Bean，使用 `@Qualifier` 注解区分即可。

### Q: RAG 知识库需要什么额外配置？

需要配置 Embedding API（DeepSeek 暂不支持 Embedding，需单独申请）：

```env
# 硅基流动（国内直连，推荐）
AI_EMBEDDING_API_KEY=sk-your-siliconflow-key
AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
AI_EMBEDDING_MODEL=bge-large-zh-v1.5
```

不配置 Embedding API 时，知识库功能不可用（上传后处理失败），但不影响普通 AI 对话。

### Q: 重启后端后知识库数据会丢失吗？

不会。文本块持久化在 MySQL `kb_chunk` 表中，重启后通过 `@PostConstruct` 自动重建向量索引（~500 块约需 10-20 秒）。

---

## 相关文档

- [CLAUDE.md](CLAUDE.md) — 项目架构与开发规范（含 RAG + Agent 完整文档）
- [项目构建教程1.md](Project%20Detail%20Guide/项目构建教程1.md) — 用户认证系统 28 步完整构建过程
- [项目构建教程2.md](Project%20Detail%20Guide/项目构建教程2.md) — AI 智能对话 + RAG 知识库详细实现（Phase 1 + Phase 2 + Phase 3 Agent 概述）
- [WECHAT_SETUP_GUIDE.md](Project%20Detail%20Guide/WECHAT_SETUP_GUIDE.md) — 微信扫码登录配置指南
- [P0_SECURITY_FIX.md](FIX_Document/P0_SECURITY_FIX.md) — P0 安全修复记录
- [README.md](README.md) — 项目主 README（含最新路线图和完整 API 文档）
- [API 文档](http://localhost:8080/doc.html) — Knife4j 在线文档（需启动后端）

---

## 许可证

本项目仅供学习交流使用。
