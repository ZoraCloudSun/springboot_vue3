# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
## 每次发起对话，请务必遵循以下要求：

### 1. 新增功能和修bug：

- 代码注释写好
- 接口文档及时更新
- ​测试用例要编写
- ​项目中相应 md 文件要更新

### 2. 待补充......
---

## Build & Run

### Development Mode (hot reload — recommended for daily coding)

Infrastructure (MySQL + Redis) runs in Docker; backend and frontend run on the host machine with live reload.

```bash
# 1. Start infrastructure only (MySQL + Redis)
docker compose up -d mysql redis

# 2. Backend: run AppStart.java in IDEA (or `mvn spring-boot:run`)
#    With spring-boot-devtools on classpath, save → auto-restart (~3s).
#    IDEA settings required:
#    Settings → Build → Compiler → ☑ Build project automatically
#    Ctrl+Shift+Alt+/ → Registry → ☑ compiler.automake.allow.when.app.running
cd springboot
mvn spring-boot:run          # Terminal alternative to IDEA

# 3. Frontend: Vite dev server with HMR (hot module replacement)
cd web/frontend
npm run dev                  # → http://localhost:3000
#    API requests (/user, /ai, /rag) are proxied to localhost:8080 via vite.config.js

# 4. Access
#    Frontend: http://localhost:3000 (Vite HMR — instant browser update on save)
#    API docs: http://localhost:3000/doc.html (proxied to backend)
#    Backend:  http://localhost:8080
#    MySQL:    localhost:13306  (port from docker-compose)
#    Redis:    localhost:6379

# Run tests
cd springboot && mvn test        # 371 tests, ~20s
```

**How hot reload works**:

| Component | Tool | Behavior |
| --------- | ---- | -------- |
| Backend Java | spring-boot-devtools | Save file → Spring auto-restarts (2-3s). Fat JAR runtime auto-disables devtools. |
| Frontend Vue | Vite HMR | Save file → browser updates in-place (no page reload, no state loss) |
| MySQL/Redis | Docker | No code changes → start once, keep running |

**When to use Docker Compose full build** (`docker compose up -d --build`):

- Merging a PR / switching branches with dependency changes
- Verifying production build before deployment
- First-time setup after cloning

### Docker Compose (full stack, one-click)

```bash
# Build and start all services (MySQL + Redis + Spring Boot + Nginx)
docker compose up -d --build

# Check service status
docker compose ps

# Access: http://localhost (frontend), http://localhost/doc.html (API docs),
#         http://localhost:18080/actuator/health (health check)

# Optional: Adminer DB management UI
docker compose --profile debug up -d   # → http://localhost:8081

# Stop (preserve volumes)
docker compose down

# Stop and reset (delete DB/Redis data)
docker compose down -v

# Rebuild after code changes
docker compose up -d --build

# View logs
docker compose logs -f backend
```

### Manual (without Docker)

```bash
# Backend (Spring Boot, port 8080)
cd springboot
mvn spring-boot:run

# Run tests (371 tests, ~20 seconds)
cd springboot
mvn test

# Frontend (Vite dev server, port 3000)
cd web/frontend
npm install
npm run dev              # Vite dev server with HMR
npm run build            # Production build → dist/

# Database migration
mysql -u root -p springboot_zyt < DB_MIGRATION.sql

# ngrok for WeChat OAuth local dev
ngrok http 8080
# Then update application.yml wechat.callback-url with the new ngrok domain

# Redis CLI (inspect state)
redis-cli
> KEYS wechat:*
> GET token:user@example.com
```

**Prerequisites for non-Docker**: JDK 21, MySQL 8.x (database: `springboot_zyt`), Redis 7.x (port 6379), Node.js 18+. Alternatively, just Docker — no manual prerequisites needed.

**API Documentation**: Knife4j UI at `http://localhost:8080/doc.html` after backend starts. Swagger JSON at `/v3/api-docs`. Knife4j paths are excluded from the interceptor chain in `WebConfig.java`.

⚠️ **Knife4j + Spring Boot 3.5.x compatibility**: `GlobalExceptionHandler` has `@Hidden` annotation to prevent SpringDoc from scanning it — this avoids a `NoSuchMethodError` caused by `ControllerAdviceBean` constructor signature change in Spring Framework 6.2. Also, `Knife4jConfig` has a `GlobalOpenApiCustomizer` bean that injects `SecurityRequirement` into every Operation — Knife4j does not auto-inherit the global security declaration, so this customizer is mandatory for the Authorize button to work. If upgrading Knife4j to a version that natively supports Spring Boot 3.5+, remove both workarounds. See `FIX_Document/P0_SECURITY_FIX.md#P2` for details.

---

## Project Structure (Quick Reference)

```
springboot/src/main/java/com/zora/
├── AppStart.java                          # 启动类
├── config/                                # 配置类（13 个）
│   ├── AiConfig.java                      #   LangChain4j 流式 + 非流式模型（Phase 3 新增 ChatLanguageModel）
│   ├── AgentConfig.java                   #   Agent 智能体配置（Phase 3，@ConfigurationProperties）
│   ├── RagConfig.java                     #   EmbeddingModel + SimpleEmbeddingStore Bean
│   ├── Knife4jConfig.java                 #   OpenAPI 3 文档 + SecurityRequirement 注入
│   ├── SecurityConfig.java                #   Spring Security（仅 BCrypt）
│   ├── WebConfig.java                     #   拦截器注册 + CORS
│   ├── LoginInterceptor.java              #   JWT 校验 + Redis 单设备验证
│   ├── RoleInterceptor.java               #   RBAC 角色校验
│   ├── RequireRole.java                   #   @RequireRole("admin") 自定义注解
│   ├── MyConfig.java                      #   MyBatis-Plus 分页插件
│   ├── WechatConfig.java                  #   微信 OAuth 配置
│   ├── CleanupTask.java                   #   定时清理任务
│   └── SwaggerCompatController.java       #   Swagger JSON 兼容端点
├── agent/                                 # Agent 智能体（Phase 3，5 个子包）
│   ├── AgentService.java                  #   Agent 服务接口
│   ├── impl/AgentServiceImpl.java         #   核心实现：两阶段流式 + ReAct + 多 Agent
│   ├── tool/Tool.java                     #   工具标记接口（Phase 3.2 实现具体工具）
│   ├── event/AgentEvent.java              #   结构化 SSE 事件 record + withField
│   ├── memory/                            #   Phase 3.4 记忆系统
│   │   └── RedisChatMemoryStore.java      #     LangChain4j ChatMemoryStore Redis 实现
│   └── graph/                             #   Phase 3.5 多 Agent 编排
│       ├── AgentState.java                #     图状态"黑板"（SpecialistResult record）
│       ├── AgentNode.java                 #     节点统一接口
│       ├── SupervisorAgent.java           #     LLM 零样本意图分类器
│       ├── ResearchAgent.java             #     研究搜索专家（WebSearchTool）
│       ├── MathAgent.java                 #     数学计算专家（MathTool）
│       ├── CodeAgent.java                 #     代码执行专家（CodeExecutionTool）
│       └── AgentGraph.java                #     多 Agent 编排器（Supervisor + Summarizer）
├── controller/                            # REST 控制器（4 个，33+ 端点）
│   ├── UserController.java                #   用户认证（16 端点）
│   ├── AiChatController.java              #   AI 对话 + SSE + RAG 对话
│   ├── AgentController.java               #   Agent 智能体 SSE 流式对话（Phase 3）
│   └── RagController.java                 #   RAG 知识库 CRUD（16 端点）
├── service/                               # 业务逻辑层
│   ├── UserService.java / impl/UserServiceImpl.java           # 用户认证
│   ├── AiChatService.java / impl/AiChatServiceImpl.java       # AI 对话 + RAG 注入
│   ├── RagService.java / impl/RagServiceImpl.java             # 知识库 CRUD + 回收站
│   ├── RagProcessingService.java / impl/RagProcessingServiceImpl.java  # 文档处理 + 启动重建
│   ├── ConversationSummaryService.java / impl/ConversationSummaryServiceImpl.java  # Phase 3.4 对话摘要
│   └── impl/SimpleEmbeddingStore.java     #   余弦相似度内存向量存储
├── entity/                                # 实体类（8 个）
│   ├── User.java, ChatConversation.java, ChatMessage.java
│   ├── KnowledgeBase.java, KbDocument.java, KbChunk.java
│   ├── AgentStep.java                     #   瞬态 POJO（Phase 3，记录推理步骤）
│   └── ChatConversationSummary.java       #   Phase 3.4 对话摘要实体
├── mapper/                                # MyBatis-Plus Mapper（7 个，BaseMapper 免写 SQL）
│   └── ChatConversationSummaryMapper.java #   Phase 3.4 摘要 Mapper
├── exception/                             # 异常体系（7 个）
│   ├── BusinessException.java             #   基类
│   ├── BadRequestException (400) / UnauthorizedException (401)
│   ├── ForbiddenException (403) / NotFoundException (404) / RateLimitException (429)
│   └── GlobalExceptionHandler.java        #   @RestControllerAdvice 全局捕获
└── utils/                                 # 工具类（7 个）
    ├── JwtUtil.java / CaptchaUtil.java / EmailUtil.java
    ├── WechatUtil.java / ResponseUtil.java
    ├── FileTypeUtil.java / TextSplitterUtil.java

web/frontend/src/
├── views/                                 # 页面组件（7 个）
│   ├── Home.vue / Login.vue / Register.vue / ForgotPassword.vue
│   ├── Chat.vue                           #   AI 对话（SSE + RAG 开关）
│   ├── KnowledgeBase.vue                  #   知识库管理 + 两级回收站
│   └── Admin.vue                          #   管理员页面
├── api/                                   # API 封装
│   ├── index.js                           #   Axios 实例 + 拦截器（自动刷新 Token）
│   ├── user.js / ai.js / rag.js           #   按模块封装
├── router/index.js                        # 路由 + 导航守卫
└── utils/token.js                         # localStorage 双 Token 存取

springboot/src/test/java/com/zora/          # 单元测试（371 个）
├── utils/         ResponseUtilTest, CaptchaUtilTest, JwtUtilTest
├── service/       UserServiceImplTest, RagServiceImplTest, EmbeddingDebugTest
├── config/        LoginInterceptorTest, RoleInterceptorTest, SwaggerCompatControllerTest
├── controller/    UserControllerTest, RagControllerTest
└── exception/     GlobalExceptionHandlerTest
```

---

## Architecture Overview

### Authentication: Custom Interceptor, Not Spring Security

Spring Security is configured but **only** for `BCryptPasswordEncoder` — all auth filters (CSRF, session, form-login) are explicitly disabled in `SecurityConfig.java`. Actual authentication runs through a custom `LoginInterceptor` (Spring MVC `HandlerInterceptor`), which is simpler and more transparent than Spring Security's filter chain.

**Interceptor chain** (for protected endpoints):
1. `LoginInterceptor` → extract `Authorization` header → JWT validation (signature + expiry) → Redis comparison (single-device check) → store `userEmail` + `userRole` as request attributes
2. `RoleInterceptor` → check `@RequireRole` annotation on handler → compare against `request.getAttribute("userRole")` → 403 if insufficient role

Endpoints excluded from interception are listed in `WebConfig.addInterceptors()` — when adding new public endpoints, **always add them to `excludePathPatterns`**.
- **New admin endpoints**: Apply `@RequireRole("admin")` on the controller method — the RoleInterceptor will enforce it. Do NOT add admin endpoints to `excludePathPatterns`.

### Dual JWT Token System

Two tokens with distinct purposes, distinguished by the JWT `type` claim:

| Token | `type` claim | TTL | Storage (Redis) | Usage |
|-------|-------------|-----|-----------------|-------|
| accessToken | `"access"` | 30 min | `token:{email}` | Every API request (Authorization header) |
| refreshToken | `"refresh"` | 7 days | `refresh_token:{email}` | Only at `/user/refresh` when accessToken expires |

**Critical security rule**: `/user/refresh` MUST call `jwtUtil.isRefreshToken(token)` before any other validation. This prevents attackers from using a stolen accessToken for infinite renewal (P0 fix).

### Redis as Central State Store

Redis holds ALL runtime state with TTL-based automatic cleanup:

| Prefix | Key pattern | TTL | Purpose |
|--------|------------|-----|---------|
| `token:` | `token:{email}` | 30 min | Active accessToken (single-device enforcement) |
| `refresh_token:` | `refresh_token:{email}` | 7 days | Active refreshToken |
| `email_code:` | `email_code:{email}` | 5 min | Email verification code |
| `reset_code:` | `reset_code:{email}` | 5 min | Password reset verification code |
| `captcha:` | `captcha:{uuid}` | 1 min | Image captcha code |
| `login_fail:` | `login_fail:{email}` | 15 min | Brute-force counter (INT, locked at ≥5) |
| `wechat_scene:` | `wechat_scene:{sceneId}` | 5 min | WeChat scan status (pending/scanned/confirmed) |
| `wechat_data:` | `wechat_data:{sceneId}` | 5 min | WeChat user info JSON |
| `wechat_token:` | `wechat_token:{sceneId}` | 5 min | JWT tokens for PC polling pickup |

**Key Redis patterns**:
- `INCR` for login failure counting (atomic, TTL set only on first failure to prevent reset-attack bypass)
- Single-device login: new login deletes old `token:{email}` and `refresh_token:{email}` before writing new ones
- WeChat scene TTLs are all 5 minutes — codes, data, and tokens expire together

### WeChat OAuth: Cross-Device Polling Architecture

The WeChat login is a **real OAuth 2.0** implementation using WeChat test accounts (free, no business verification). The key architectural insight is that the OAuth callback happens on the **phone browser** (WeChat in-app), not the PC browser:

1. PC generates QR code → starts polling `/user/wechat/check` every 2 seconds
2. User scans with phone → WeChat redirects to `/user/wechat/callback` (on phone)
3. Backend exchanges code for openid, checks if openid is already bound:
   - **Already bound** → issue JWT, set status `confirmed` → PC auto-login (zero user action)
   - **Not bound** → store WeChat data, set status `scanned` → PC shows email binding form
4. PC polling detects status change and reacts accordingly

### Backend Layering

```
Controller  →  Service (interface)  →  ServiceImpl  →  Mapper (MyBatis-Plus BaseMapper)
    ↑                ↑                      ↑                ↑
  REST endpoints  Business logic      Transactions      Database access
  (thin, no logic)                    Redis ops         (auto-generated CRUD)
```

- `UserMapper` extends `BaseMapper<User>` — all CRUD is auto-generated, zero XML
- `UserServiceImpl` contains ALL business logic including Redis operations, WeChat API calls, and email sending
- `ResponseUtil` is the unified response wrapper: `{ code, msg, data }`, with static factory methods `success()` / `error()` and safe JSON serialization `toJsonError()`

### Error Handling Architecture

**Global exception handling** via `@RestControllerAdvice` (`GlobalExceptionHandler.java`). Exceptions thrown from controllers/services are caught and converted to `ResponseUtil` JSON — no more manual `return new ResponseUtil(code, msg, null)` for error paths.

**Exception hierarchy** (`com.zora.exception`):
- `BusinessException(code, msg)` — base class, carries HTTP status code
- `BadRequestException(msg)` → 400 — validation / business rule failures
- `UnauthorizedException(msg)` → 401 — auth failures
- `ForbiddenException(msg)` → 403 — permission denied
- `NotFoundException(msg)` → 404 — resource not found
- `RateLimitException(msg)` → 429 — rate limiting

**How errors flow to the client**:

```
Service throws BadRequestException("邮箱不能为空")
  → GlobalExceptionHandler @ExceptionHandler(BusinessException.class)
    → ResponseEntity.status(400).body(ResponseUtil(400, "邮箱不能为空", null))
      → HTTP 400 JSON → Axios response interceptor
        → ElMessage.error("邮箱不能为空")
```

```
Interceptor (pre-controller) → writeError() via ResponseUtil.toJsonError()
  → Direct PrintWriter write (bypasses @ControllerAdvice)
```

**Key rules**:
- **Service layer**: Can either throw `BusinessException` subclasses (recommended for validation) or return `ResponseUtil` for complex control flow — both patterns coexist and work correctly
- **Controller layer**: Should remain thin pass-through — never catch exceptions, let `@ControllerAdvice` handle them
- **Interceptors**: Run before controllers, so their errors bypass `@ControllerAdvice` — they use `ResponseUtil.toJsonError(code, msg)` for safe JSON serialization (Jackson-based, properly escapes special characters)
- **Catch-all**: `@ExceptionHandler(Exception.class)` logs full stacktrace, returns generic 500 — never exposes exception details to client
- **Email failures**: Logged via SLF4J `log.error(...)` instead of `e.printStackTrace()`

### Frontend Data Flow

```
Vue Component  →  api/user.js  →  api/index.js (Axios instance)
                                       │
                          Request interceptor: injects accessToken
                          Response interceptor: handles 401 → auto-refresh
                                       │
                                  HTTP → Backend
```

**Auto-refresh mechanism** (`api/index.js`):
- When any request gets 401, the response interceptor catches it
- Uses a `isRefreshing` lock + `pendingRequests` queue to ensure only ONE refresh call happens even if multiple requests fail simultaneously
- Queued requests are replayed with the new token after refresh completes
- `config._retry` flag prevents infinite refresh loops

**Token storage** (`utils/token.js`): Two separate localStorage keys (`zyt_access_token`, `zyt_refresh_token`) — avoids JSON parsing overhead and keeps lifecycles independent.

**Route guard** (`router/index.js`): `beforeEach` checks `meta.requiresAuth` / `meta.guest` against token presence. This is UX-only — real security is in the backend interceptor.

### WeChat Configuration Files

- `WechatConfig.java`: `@Value` injections for app-id, app-secret, callback-url + API endpoint URL constants
- `WechatUtil.java`: HTTP client for WeChat APIs (`code→access_token`, `access_token→userinfo`) with inner DTO classes
- Callback URL format: `https://{ngrok-domain}/user/wechat/callback`
- Test account credentials are in `application.yml` with environment variable fallbacks (`${WECHAT_APP_ID:default}`)

---

## RAG Knowledge Base (Phase 2)

### Architecture

RAG (Retrieval-Augmented Generation) 通过文档上传 → 文本提取 → 分块 → Embedding → 向量检索 → 上下文注入的完整管道，使 AI 回答能够基于用户的知识库内容。

```
Upload → Tika解析 → 递归分块 → Embedding(OpenAI兼容API) → SimpleEmbeddingStore(内存)
                                                           ↓
RAG Chat → 检索相关块 → 注入System Prompt → SSE 流式输出
```

**Key Design Decisions**:
- **Embedding Model**: `OpenAiEmbeddingModel` (langchain4j-open-ai, same adapter pattern as chat), points to OpenAI-compatible API via `rag.embedding.*` config
- **Vector Store**: `SimpleEmbeddingStore` — custom in-memory cosine-similarity store (because `InMemoryEmbeddingStore` is not in langchain4j-core 1.15.0). Rebuilt from MySQL `kb_chunk` table on app restart.
- **Document Processing**: Apache Tika for text extraction (PDF/DOCX/DOC/TXT/MD) + `TextSplitterUtil` recursive splitter (300 char chunks, 50 char overlap)
- **Integration**: Opt-in per conversation — toggle "RAG" switch in chat header, select a knowledge base. Retrieval results injected before the System Prompt security rules.
- **Two-Level Recycle Bin**: Soft-deleted knowledge bases and documents are moved to a recycle bin, not permanently removed. Supports restore (with automatic re-embedding) and permanent deletion (cleans disk files, vectors, chunks, and DB records).

### Database Tables

| Table | Purpose | Key Fields |
|-------|---------|------------|
| `knowledge_base` | User-created document collections | id, user_id, name, description, deleted_at |
| `kb_document` | Uploaded documents with processing status | id, kb_id, filename, file_type, status (PENDING→PROCESSING→COMPLETED/FAILED), chunk_count |
| `kb_chunk` | Text chunks (persistence for embedding rebuild) | id, document_id, chunk_index, content, char_count |

Migration: `springboot/src/main/resources/db/migration/V3__rag_tables.sql`

### API Endpoints (/rag/**)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/rag/knowledge-bases` | Create knowledge base |
| GET | `/rag/knowledge-bases` | List user's knowledge bases |
| GET | `/rag/knowledge-bases/{id}` | Get KB detail + documents |
| PUT | `/rag/knowledge-bases/{id}` | Update name/description |
| DELETE | `/rag/knowledge-bases/{id}` | Soft delete KB |
| POST | `/rag/knowledge-bases/{id}/documents` | Upload document (multipart) |
| GET | `/rag/knowledge-bases/{id}/documents` | List documents |
| DELETE | `/rag/knowledge-bases/{id}/documents/{docId}` | Delete document |
| POST | `/rag/knowledge-bases/{id}/query` | Test retrieval |
| POST | `/ai/chat/rag-stream` | SSE RAG-enhanced streaming chat |
| GET | `/rag/recycle-bin` | List deleted knowledge bases |
| PUT | `/rag/recycle-bin/{kbId}/restore` | Restore KB + all docs |
| DELETE | `/rag/recycle-bin/{kbId}` | Permanently delete KB |
| GET | `/rag/knowledge-bases/{kbId}/recycle-bin` | List deleted docs (per KB) |
| PUT | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}/restore` | Restore doc |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin/{docId}` | Permanently delete doc |
| DELETE | `/rag/knowledge-bases/{kbId}/recycle-bin` | Empty doc recycle bin |

All `/rag/**` endpoints require login. No changes needed to `WebConfig.excludePathPatterns`.

### Key Backend Files

| Layer | File |
|-------|------|
| Config | `config/RagConfig.java` — `EmbeddingModel` + `SimpleEmbeddingStore` beans |
| Entity | `entity/KnowledgeBase.java`, `entity/KbDocument.java`, `entity/KbChunk.java` |
| Mapper | `mapper/KnowledgeBaseMapper.java`, `mapper/KbDocumentMapper.java`, `mapper/KbChunkMapper.java` |
| Service | `service/RagService.java`, `service/RagProcessingService.java` |
| Impl | `service/impl/RagServiceImpl.java`, `service/impl/RagProcessingServiceImpl.java`, `service/impl/SimpleEmbeddingStore.java` |
| Controller | `controller/RagController.java` |
| Utility | `utils/FileTypeUtil.java`, `utils/TextSplitterUtil.java` |

### RAG Chat Integration

`AiChatServiceImpl.streamChatWithRag()` — when `knowledgeBaseId` is not null:
1. `ragService.retrieveContext(kbId, query)` — embed query, search vector store, format results
2. Build System Prompt: `【知识库参考内容】 + chunks + 【知识库内容结束】 + existing SYSTEM_PROMPT`
3. All other logic (rate limiting, injection detection, streaming) identical to `streamChat()`
4. If retrieval fails or returns empty → graceful degradation to standard chat

### Frontend

| File | Purpose |
|------|---------|
| `api/rag.js` | RAG API calls (knowledge base CRUD, upload, query, SSE RAG chat) |
| `views/KnowledgeBase.vue` | Full KB management page: card list, expandable document table, upload, status polling, test query panel, two-level recycle bin (KB + document) |
| `views/Chat.vue` (modified) | RAG toggle switch + KB selector in chat header, RAG-aware send handler, sidebar "知识库" link |
| `router/index.js` (modified) | Added `/knowledge` route (`requiresAuth: true`) |

### Configuration

```yaml
rag:
  embedding:
    api-key: ${AI_EMBEDDING_API_KEY:}
    base-url: ${AI_EMBEDDING_BASE_URL:https://api.openai.com/v1}
    model-name: ${AI_EMBEDDING_MODEL:text-embedding-3-small}
  document:
    max-size: 10485760        # 10MB
    upload-dir: ${RAG_UPLOAD_DIR:./uploads/rag}
    max-chunk-size: 300
    max-chunk-overlap: 50
    max-retrieve-results: 5
    min-relevance-score: 0.3
```

**Embedding API providers**: OpenAI, 硅基流动 (SiliconFlow, cheaper in China), or any OpenAI-compatible endpoint. Set via `.env` variables `AI_EMBEDDING_API_KEY`, `AI_EMBEDDING_BASE_URL`, `AI_EMBEDDING_MODEL`.

### Startup Embedding Rebuild

On application start, `RagProcessingServiceImpl.@PostConstruct rebuildEmbeddingStore()`:
1. Queries all `COMPLETED` non-deleted documents
2. Loads their chunks from `kb_chunk` table
3. Re-embeds each chunk via `EmbeddingModel`
4. Stores embeddings in `SimpleEmbeddingStore`

This ensures vector data survives restarts despite in-memory storage.

---

## AI Agent 智能体 (Phase 3)

### Architecture

Agent 在 Phase 1 流式对话的基础上，增加了工具调用（Tool Calling）能力。
采用"两阶段流式"架构：先用非流式模型完成推理循环，再流式输出最终回答。

```
用户消息 → AgentController → AgentServiceImpl
                                  ├── 非流式推理循环 (ChatLanguageModel)
                                  │   ├── thinking → SSE 事件
                                  │   ├── tool_call → 执行工具 → SSE 事件
                                  │   └── tool_result → SSE 事件
                                  └── 流式最终回答 (StreamingChatModel)
                                      └── token/done → SSE 事件
```

**Key Design Decisions**:
- **两阶段流式**：非流式 `ChatLanguageModel` 用于工具调用推理（DeepSeek 不支持流式 function calling），流式 `StreamingChatModel` 用于最终回答输出
- **手动 Agent 循环**：不使用 LangChain4j `AiServices`（因为无法拦截中间事件），采用 `ChatLanguageModel.generate(messages, toolSpecs)` 手动实现 ReAct 循环
- **结构化 SSE 协议**：`AgentEvent` 类型系统替代 Phase 1 的纯 token JSON 协议，支持 thinking / tool_call / tool_result / token / done / error 六种事件
- **工具标记接口**：所有工具实现 `Tool` 接口，Spring 自动注入 `List<Tool>`，通过 `AgentConfig` 配置开关过滤

### SSE 事件协议

| 事件类型 | 字段 | 用途 |
|---------|------|------|
| `thinking` | `content` | Agent 思考过程描述 |
| `tool_call` | `tool`, `args` | 工具调用请求 |
| `tool_result` | `tool`, `content` | 工具执行结果 |
| `token` | `content` | 最终回答文本片段 |
| `done` | `conversationId` | 对话完成 |
| `error` | `message` | 错误信息（已脱敏） |

### New API Endpoints (/agent/**)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/agent/chat/stream` | Agent SSE 流式对话（支持工具调用） |

All `/agent/**` endpoints require login (same as `/ai/**` — intercepted by `LoginInterceptor`).

### Key Backend Files

| Layer | File |
|-------|------|
| Config | `config/AiConfig.java` — 新增 `OpenAiChatModel`（非流式）bean |
| Config | `config/AgentConfig.java` — `@ConfigurationProperties(prefix="agent")` |
| Agent Service | `agent/AgentService.java` — Agent 服务接口 |
| Agent Impl | `agent/impl/AgentServiceImpl.java` — 核心实现：两阶段流式 + ReAct 循环 |
| Tool SPI | `agent/tool/Tool.java` — 工具标记接口 |
| Web Search Tool | `agent/tool/WebSearchTool.java` — Tavily Search API 网页搜索 |
| Math Tool | `agent/tool/MathTool.java` — exp4j 安全数学表达式求值 |
| Code Execution Tool | `agent/tool/CodeExecutionTool.java` — JS ScriptEngine 沙箱代码执行 |
| Event | `agent/event/AgentEvent.java` — 结构化 SSE 事件 record |
| Controller | `controller/AgentController.java` — `/agent/chat/stream` SSE 端点 |
| Entity | `entity/AgentStep.java` — 瞬态 POJO，记录推理步骤 |

### Configuration

```yaml
agent:
  tools:
    web-search:
      enabled: ${AGENT_TOOL_WEB_SEARCH:true}
    math:
      enabled: ${AGENT_TOOL_MATH:true}
    code-execution:
      enabled: ${AGENT_TOOL_CODE_EXEC:false}  # 默认关闭（安全考量）
      timeout-seconds: 5
      max-output-length: 10000
  tavily:
    api-key: ${TAVILY_API_KEY:}
    base-url: https://api.tavily.com/search
    timeout-seconds: 15
  multi-agent:
    enabled: ${AGENT_MULTI_AGENT:false}       # Phase 3.5
    max-specialist-calls: 3
  memory:
    window-size: 20
    summary-trigger-count: 10
    summary-max-length: 300
```

### Security

- **工具循环限制**：Agent 推理最多 5 次迭代（`MAX_AGENT_ITERATIONS`）
- **代码执行默认关闭**：`agent.tools.code-execution.enabled=false`
- **限流**：Redis ZSET 滑动窗口，10 次/分钟/用户（`agent_rate:` key）
- **注入检测**：18 种中英文 Prompt Injection 模式

### Built-in Tools (Phase 3.2)

Three concrete tool implementations, all discovered by Spring via `List<Tool>` auto-injection:

| Tool | Class | Implementation | Default |
|------|-------|---------------|---------|
| 🌐 Web Search | `agent/tool/WebSearchTool.java` | Tavily Search API (POST, JSON response with title/url/content) | enabled |
| 🧮 Math | `agent/tool/MathTool.java` | exp4j 0.4.8 safe expression evaluation (+, -, *, /, ^, %, sin/cos/tan, log/sqrt, pi/e, !) | enabled |
| 💻 Code Execution | `agent/tool/CodeExecutionTool.java` | JDK `ScriptEngine` JavaScript sandbox (timeout + output cap + audit log) | **disabled** (security) |

**Tool registration mechanism**: Each tool implements the `Tool` marker interface → Spring auto-collects via `@Autowired(required = false) List<Tool>` → `AgentServiceImpl.getEnabledTools()` filters by `agent.tools.*.enabled` config → LangChain4j `ToolSpecifications.toolSpecificationsFrom()` extracts `@Tool` annotations for LLM function calling.

**Adding a new tool**:
1. Create class implementing `com.zora.agent.tool.Tool`
2. Annotate with `@Component` + `@dev.langchain4j.agent.tool.Tool("description")`
3. Add `@P("param description")` on each parameter
4. Add switch config in `AgentConfig.ToolsConfig` and `application.yml`
5. Write unit test extending `@ExtendWith(MockitoExtension.class)`

### Agent 可视化推理过程 (Phase 3.3)

前端 Chat.vue 集成 Agent 推理可视化，用户可以实时观察 Agent 的思考、工具调用和工具返回过程。

**UI 组件**:

| 组件 | 位置 | 功能 |
|------|------|------|
| Agent 模式开关 | chat-header `el-switch` | 开启/关闭 Agent 模式（与 RAG 开关并列） |
| 推理面板 | chat-header 下方 | 可折叠面板，展示思考步骤/工具调用/工具结果的时序流 |
| 思考指示器 | 流式消息中 | 推理过程中显示旋转 CPU 图标 + "思考中..." |
| 收起摘要条 | 推理面板折叠时 | 单行显示 "推理过程 (N 步)"，点击展开 |

**色彩编码**:

| 步骤类型 | 边框颜色 | 背景色 | 图标 |
|---------|---------|--------|------|
| `thinking` | 蓝色 `#1677ff` | `#f0f5ff` | Loading 旋转 |
| `tool_call` | 琥珀色 `#f59e0b` | `#fffbeb` | Tools 工具 |
| `tool_result` | 绿色 `#10b981` | `#f0fdf6` | CircleCheck 对勾 |

**数据流**:
```
streamAgentChat() → SSE 解析 → dispatchEvent()
  ├── onThinking  → reasoningSteps.push({type:'thinking', ...})
  ├── onToolCall  → reasoningSteps.push({type:'tool_call', ...})
  ├── onToolResult → reasoningSteps.push({type:'tool_result', ...})
  ├── onToken     → streamingContent += token
  ├── onDone      → 最终消息推入 messages[]
  └── onError     → ElMessage.error()
```

**关键设计**:
- `reasoningSteps` 独立于 `messages` 数组——推理步骤是元数据，不混入对话历史
- `formatToolArgs()` 将参数对象转为 `key=value` 字符串显示（截断 60 字符）
- `formatToolResult()` 智能解析 JSON 结果，提取摘要信息
- Vue `<Transition name="reasoning-panel">` 实现面板平滑滑入/滑出
- 每个步骤项有 `@keyframes step-enter` 上滑淡入动画
- 收起态与展开态互斥显示，保持页面整洁

**前端文件**:

| File | Purpose |
|------|---------|
| `api/agent.js` | Agent SSE 客户端（`streamAgentChat()` + `dispatchEvent()`） |
| `views/Chat.vue` | Agent 开关、推理面板、思考指示器、三模式分发（Agent/RAG/标准） |

### Phase 3 Sub-phases

| Phase | Description | Status |
|-------|-------------|--------|
| 3.1 | LangChain4j Tool Calling 基础框架 + AgentController + SSE 协议 | ✅ Done |
| 3.2 | 内置工具：WebSearchTool, MathTool, CodeExecutionTool（55 个新测试）| ✅ Done |
| 3.3 | Agent 可视化推理过程（Chat.vue 推理面板，18 个新测试） | ✅ Done |
| 3.4 | 记忆摘要 + 长期记忆（ChatMemory + ConversationSummary，24 个新测试） | ✅ Done |
| 3.5 | 多 Agent 编排（Supervisor + Specialist Agents + AgentGraph） | ✅ Done |

### Frontend

| File | Purpose |
|------|---------|
| `api/agent.js` | Agent SSE 客户端，解析结构化事件并分发到回调函数 |

### Agent 记忆系统 (Phase 3.4)

实现短期记忆窗口（ChatMemory）和长期记忆摘要（ConversationSummary），让 AI 在长对话中"记住"早期讨论内容。

**架构**:

```
每次 AI 回复 → checkAndSummarize(conversationId)
  ├── 消息数不足阈值 → 跳过
  └── 达到阈值 → CompletableFuture.runAsync()
       ├── 加载未覆盖消息
       ├── 调用 LLM 生成 ≤300 字摘要
       ├── 存储到 chat_conversation_summary 表
       └── 更新 chat_conversation.summary_id
```

**数据库变更** (V4__agent_tables.sql):

| 变更 | 说明 |
|------|------|
| 新建 `chat_conversation_summary` 表 | id, conversation_id, summary, message_count, created_at |
| `chat_conversation` 新增 `summary_id` 列 | 指向最新摘要的外键 |

**摘要上下文注入**: 后续对话时，`buildSystemPromptWithMemory()` 将历史摘要注入 System Prompt 之前。

**Redis 记忆缓存**: `RedisChatMemoryStore` 实现 LangChain4j `ChatMemoryStore` 接口，将消息窗口缓存到 Redis（TTL 24h）。Key 格式：`memory:conv:{conversationId}`。

**核心文件**:

| Layer | File |
|-------|------|
| Migration | `db/migration/V4__agent_tables.sql` |
| Entity | `entity/ChatConversationSummary.java` |
| Mapper | `mapper/ChatConversationSummaryMapper.java` |
| Memory Store | `agent/memory/RedisChatMemoryStore.java` |
| Service | `service/ConversationSummaryService.java` |
| Impl | `service/impl/ConversationSummaryServiceImpl.java` |
| Modified | `agent/impl/AgentServiceImpl.java` — 注入摘要上下文 + 触发摘要生成 |
| Modified | `entity/ChatConversation.java` — 新增 summaryId 字段 |
| Modified | `config/AgentConfig.java` — MemoryConfig 已在 Phase 3.1 预定义 |

### 多 Agent 编排 (Phase 3.5)

实现 Supervisor → Specialist → Summarizer 的多 Agent 协作模式，让不同类型的任务由对应领域的专家 Agent 处理。

**架构**:

```
User Message → AgentServiceImpl (multi-agent enabled)
  ├── SupervisorAgent (意图分类: research/math/code/general)
  │     ├── research → ResearchAgent (WebSearchTool)
  │     ├── math     → MathAgent (MathTool)
  │     ├── code     → CodeAgent (CodeExecutionTool)
  │     └── general  → 直接流式回答（无需 Specialist）
  └── Summarizer（聚合所有专家结果）→ 流式最终回复
```

**核心组件**:

| 组件 | 职责 |
|------|------|
| `AgentState` | 图状态"黑板"，各节点通过它共享信息（用户消息、意图、专家结果等） |
| `AgentNode` | 节点统一接口：`execute(state, emitter) → String` |
| `SupervisorAgent` | 使用 LLM 进行零样本意图分类，将任务路由到合适的 Specialist |
| `ResearchAgent` | 研究专家：使用 WebSearchTool 搜索互联网信息 |
| `MathAgent` | 数学专家：使用 MathTool (exp4j) 进行安全数学计算 |
| `CodeAgent` | 代码专家：使用 CodeExecutionTool (ScriptEngine) 在沙箱中执行 JS 代码 |
| `AgentGraph` | 编排器：协调 Supervisor → Specialist → Summarizer 流程，限制最多 3 次 Specialist 调用 |

**关键设计**:
- **"黑板"模式**：Agent 节点通过共享的 `AgentState` 通信，完全解耦
- **降级策略**：多 Agent 异常 → 标准 Agent 循环 → 直接回答，三层降级
- **安全限制**：最多 `maxSpecialistCalls` 次（默认 3），防止无限循环
- **SSE 增强**：`AgentEvent.withField("agent", "research")` 标记事件来源，前端可显示活跃的 Specialist

**配置**:

```yaml
agent:
  multi-agent:
    enabled: ${AGENT_MULTI_AGENT:false}      # 默认关闭
    max-specialist-calls: 3
```

**核心文件**:

| Layer | File |
|-------|------|
| State | `agent/graph/AgentState.java` |
| Node Interface | `agent/graph/AgentNode.java` |
| Supervisor | `agent/graph/SupervisorAgent.java` |
| Research Specialist | `agent/graph/ResearchAgent.java` |
| Math Specialist | `agent/graph/MathAgent.java` |
| Code Specialist | `agent/graph/CodeAgent.java` |
| Orchestrator | `agent/graph/AgentGraph.java` |
| Modified | `agent/impl/AgentServiceImpl.java` — 多 Agent 模式入口 + 工具注入 |
| Modified | `agent/event/AgentEvent.java` — 新增 `withField()` 方法 |
| Config | `config/AgentConfig.java` — MultiAgentConfig 已在 Phase 3.1 预定义 |



**Framework**: JUnit 5 + Mockito + Spring MockMvc (standalone setup). All 371 tests run as pure unit tests — no MySQL/Redis/network dependency, CI-ready. (Including RAG knowledge base tests with two-level recycle bin and Agent tool tests)

**Run**: `cd springboot && mvn test` (~10 seconds, 0 failures).

**Three-tier mock strategy**:

| Tier | Pattern | Used in |
| ---- | ------- | ------- |
| Pure JUnit 5 | `@Test` only, no Spring context | `ResponseUtilTest`, `CaptchaUtilTest`, `JwtUtilTest` |
| Mockito + `ReflectionTestUtils` | `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` / `@Mock` + `@Spy` | `UserServiceImplTest`, `LoginInterceptorTest`, `RoleInterceptorTest` |
| Standalone MockMvc | `MockMvcBuilders.standaloneSetup()` + `.setControllerAdvice()` | `UserControllerTest`, `GlobalExceptionHandlerTest` |

**Why standalone MockMvc instead of `@WebMvcTest`**: `@WebMvcTest` fails to load because `@MapperScan` on `AppStart` creates `UserMapper` bean needing `sqlSessionFactory`. Standalone setup avoids the Spring context entirely while still testing controller request/response contracts.

**Key conventions**:

- `@DisplayName("中文描述")` on every test — consistent Chinese naming
- `@Nested` inner classes for grouping related scenarios (per method or per endpoint)
- `lenient()` on shared `@BeforeEach` stubs to avoid `UnnecessaryStubbingException`
- `ReflectionTestUtils.setField()` for injecting `@Value` fields without Spring context
- Real `BCryptPasswordEncoder` (spied) for service tests — no external dependency
- Standalone MockMvc controllers wired with `ReflectionTestUtils.setField(controller, "userService", mock)` + `.setControllerAdvice(new GlobalExceptionHandler())`

**Test config**: `springboot/src/test/resources/application.yml` provides H2 datasource + placeholder credentials for `@Value` injection — but pure unit tests (all current 371 tests) never load it.

---

## Key Conventions

- **New public endpoints**: Always add to `WebConfig.excludePathPatterns`, or the interceptor will reject them with 401
- **Password storage**: Always use `BCryptPasswordEncoder` (injected, not `new`-ed)
- **JWT claims**: `generateAccessToken(email, role)` writes `"access"` type + `role` claim; `generateRefreshToken(email, role)` writes `"refresh"` type + `role` claim — all callers must pass the user's role. `getRoleFromToken(token)` extracts the role claim for authorization checks.
- **Redis key naming**: `prefix:identifier` (colon-separated), constants defined at top of `UserServiceImpl`
- **Error messages for login**: Always return "邮箱或密码错误" (unified), never distinguish "user not found" vs "wrong password" (prevents user enumeration)
- **HTTP status codes**: 200 (success), 400 (client error), 401 (unauthorized), 403 (forbidden — insufficient role), 429 (rate limited / brute-force locked), 500 (server error)
- **Frontend path alias**: `@` resolves to `src/` (configured in `vite.config.js`)
- **Vite proxy**: All `/user` requests are proxied to `http://localhost:8080` in dev mode — never hardcode `localhost:8080` in frontend API calls
- **Email sending**: Always async (`new Thread(() -> ...).start()`) — SMTP is slow and blocks HTTP response
- **ngrok domain changes**: Free ngrok changes domain on restart; update both `application.yml` (`wechat.callback-url`) AND WeChat test account page (OAuth callback domain)
