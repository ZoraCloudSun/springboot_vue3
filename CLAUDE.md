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

### Docker Compose (recommended, one-click)

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

# Run tests (212 tests, ~10 seconds)
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

**Prerequisites**: JDK 21, MySQL 8.x (database: `springboot_zyt`), Redis 7.x (port 6379), Node.js 18+. Alternatively, just Docker — no manual prerequisites needed.

**API Documentation**: Knife4j UI at `http://localhost:8080/doc.html` after backend starts. Swagger JSON at `/v3/api-docs`. Knife4j paths are excluded from the interceptor chain in `WebConfig.java`.

⚠️ **Knife4j + Spring Boot 3.5.x compatibility**: `GlobalExceptionHandler` has `@Hidden` annotation to prevent SpringDoc from scanning it — this avoids a `NoSuchMethodError` caused by `ControllerAdviceBean` constructor signature change in Spring Framework 6.2. Also, `Knife4jConfig` has a `GlobalOpenApiCustomizer` bean that injects `SecurityRequirement` into every Operation — Knife4j does not auto-inherit the global security declaration, so this customizer is mandatory for the Authorize button to work. If upgrading Knife4j to a version that natively supports Spring Boot 3.5+, remove both workarounds. See `P0_SECURITY_FIX.md#P2` for details.

---

## Project Structure (Quick Reference)

```
springboot/src/main/java/com/zora/
├── AppStart.java                          # 启动类
├── config/                                # 配置类（12 个）
│   ├── AiConfig.java                      #   LangChain4j 流式模型 + Embedding
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
├── controller/                            # REST 控制器（3 个，32+ 端点）
│   ├── UserController.java                #   用户认证（16 端点）
│   ├── AiChatController.java              #   AI 对话 + SSE + RAG 对话
│   └── RagController.java                 #   RAG 知识库 CRUD（16 端点）
├── service/                               # 业务逻辑层
│   ├── UserService.java / impl/UserServiceImpl.java           # 用户认证
│   ├── AiChatService.java / impl/AiChatServiceImpl.java       # AI 对话 + RAG 注入
│   ├── RagService.java / impl/RagServiceImpl.java             # 知识库 CRUD + 回收站
│   ├── RagProcessingService.java / impl/RagProcessingServiceImpl.java  # 文档处理 + 启动重建
│   └── impl/SimpleEmbeddingStore.java     #   余弦相似度内存向量存储
├── entity/                                # 实体类（6 个）
│   ├── User.java, ChatConversation.java, ChatMessage.java
│   └── KnowledgeBase.java, KbDocument.java, KbChunk.java
├── mapper/                                # MyBatis-Plus Mapper（6 个，BaseMapper 免写 SQL）
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

springboot/src/test/java/com/zora/          # 单元测试（212 个）
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
- **Document Processing**: Apache Tika for text extraction (PDF/DOCX/DOC/TXT/MD) + `TextSplitterUtil` recursive splitter (800 char chunks, 100 char overlap)
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
    max-chunk-size: 800
    max-chunk-overlap: 100
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

## Testing

**Framework**: JUnit 5 + Mockito + Spring MockMvc (standalone setup). All 212 tests run as pure unit tests — no MySQL/Redis/network dependency, CI-ready. (Including RAG knowledge base tests with two-level recycle bin)

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

**Test config**: `springboot/src/test/resources/application.yml` provides H2 datasource + placeholder credentials for `@Value` injection — but pure unit tests (all current 212 tests) never load it.

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
