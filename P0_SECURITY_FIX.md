# P0 安全漏洞修复记录

> 修复日期：2026-05-31  
> 修复人：hualuoshixiangyui  
> 项目：Spring Boot Auth System（前后端分离项目）

---

## P0-1：Refresh 接口缺少 Token 类型校验

### 漏洞描述

`/user/refresh` 接口接受客户端传入的 `refreshToken` 换取新的 `accessToken`，但只校验了 JWT 签名和过期时间，**没有校验 JWT claims 中的 `type` 字段**。

攻击者可以：
1. 获取一个 `accessToken`（可通过网络抓包、XSS 等方式）
2. 将该 `accessToken` 作为 `refreshToken` 发送到 `/user/refresh`
3. 获得一个新的 `accessToken`，实现**无限续期**，即使原 `accessToken` 在 Redis 中已经被删除（登出/被踢）

### 根本原因

JWT 生成时已经写入了 `type` claim 用于区分令牌用途：

```java
// JwtUtil.java - generateAccessToken
.claim("type", "access")

// JwtUtil.java - generateRefreshToken
.claim("type", "refresh")
```

但 `validateToken()` 只校验签名 + 过期，不读取 `type` 字段。claim 形同虚设。

### 修复方案

1. **JwtUtil.java** — 新增两个方法：

```java
/**
 * 校验 Token 是否为 refresh 类型
 * 只有 type=refresh 的 Token 才能调用 /user/refresh 换取新 accessToken
 */
public boolean isRefreshToken(String token) {
    try {
        Claims claims = parseClaims(token);
        return "refresh".equals(claims.get("type"));
    } catch (Exception e) {
        return false;
    }
}

public boolean isAccessToken(String token) { ... }  // 备用
```

2. **UserServiceImpl.java** — `refresh()` 方法增加类型校验：

```java
// 校验 Token 类型：必须是 refresh token，防止攻击者用 accessToken 无限续期
if (!jwtUtil.isRefreshToken(refreshToken)) {
    return new ResponseUtil(401, "Token 类型错误，请使用 refreshToken", null);
}
```

校验顺序：`类型校验 → 签名校验 → Redis 比对`，类型错误在前可以提前拒绝，减少不必要的签名运算。

### 安全效果

| 攻击场景 | 修复前 | 修复后 |
|----------|--------|--------|
| 用 accessToken 调用 /refresh | ✅ 能成功续期 | ❌ 返回 "Token 类型错误" |
| 用伪造 Token 调用 /refresh | ❌ 签名校验拦截 | ❌ 签名校验拦截 |
| 用过期 refreshToken 调用 /refresh | ❌ 签名校验拦截 | ❌ 类型或签名校验拦截 |

---

## P0-2：登录接口缺少失败次数限制（暴力破解防护）

### 漏洞描述

`/user/login` 接口对同一邮箱的连续登录失败没有任何限制。攻击者可以：
1. 通过社工或泄露数据库获知目标邮箱
2. 使用字典/撞库脚本无限尝试密码
3. 即使有图形验证码保护，攻击者可通过 OCR 或打码平台绕过

### 附带漏洞：用户枚举

修复前登录接口对不同情况返回不同错误信息：
- 用户不存在 → `"用户不存在"`
- 密码错误 → `"密码错误"`

攻击者可以通过返回信息判断哪些邮箱已注册，实现**用户枚举攻击**。

### 修复方案

**UserServiceImpl.java** — `login()` 方法重构：

```java
// 1. 检查是否已被锁定
String failKey = LOGIN_FAIL_PREFIX + email;
String failCountStr = stringRedisTemplate.opsForValue().get(failKey);
if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL_COUNT) {
    // 返回锁定提示 + 剩余时间
    return new ResponseUtil(429, "登录失败次数过多，请" + remainingMinutes + "分钟后再试", null);
}

// 2. 统一错误提示（防用户枚举）
if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
    recordLoginFail(email, failKey);
    return new ResponseUtil(400, "邮箱或密码错误", null);
}

// 3. 登录成功后清除失败记录
stringRedisTemplate.delete(failKey);
```

新增 `recordLoginFail()` 辅助方法：

```java
private void recordLoginFail(String email, String failKey) {
    Long increment = stringRedisTemplate.opsForValue().increment(failKey);
    if (increment != null && increment == 1) {
        // 首次失败设置 15 分钟 TTL，后续失败不重置 TTL
        stringRedisTemplate.expire(failKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
    }
}
```

### 关键设计决策

| 决策 | 说明 |
|------|------|
| 使用 Redis `INCR`（原子操作） | 避免并发竞态导致计数不准确 |
| TTL 仅在首次失败时设置 | 防止攻击者通过控制失败间隔来重置锁定计时器 |
| 锁定时间 = 15 分钟 | 平衡安全性和用户体验 |
| 最大失败次数 = 5 | 留有足够的容错空间 |
| HTTP 状态码 429 | Too Many Requests，语义正确，前端拦截器可识别 |
| 成功登录后清除计数 | 用户记起正确密码后不需要等待锁定过期 |

### 安全效果

| 攻击场景 | 修复前 | 修复后 |
|----------|--------|--------|
| 连续 5 次密码错误 | 可继续尝试 | 锁定 15 分钟 |
| 尝试探测已注册邮箱 | 可区分"不存在"/"密码错" | 统一提示 "邮箱或密码错误" |
| 间隔 14 分钟后重试 | — | 失败计数不重置，仍需等满 15 分钟 |
| 第 5 次输入正确密码 | — | 登录成功，清除失败计数 |

---

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| [`springboot/src/main/java/com/zyt/utils/JwtUtil.java`](springboot/src/main/java/com/zyt/utils/JwtUtil.java) | 新增 `isRefreshToken()` 和 `isAccessToken()` 方法 |
| [`springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | 1. `refresh()` 增加 Token 类型校验<br>2. `login()` 增加失败次数限制 + 统一错误提示<br>3. 新增 `recordLoginFail()` 方法<br>4. 新增常量 `LOGIN_FAIL_PREFIX`、`MAX_LOGIN_FAIL_COUNT`、`LOGIN_LOCK_MINUTES` |

---

## 后续建议（P1）

- [ ] 登录拦截器 `LoginInterceptor` 可考虑增加 `isAccessToken()` 类型校验（双保险）
- [ ] 图形验证码输错后保留 3 次机会再删除 key（当前是输错一次就删）
- [ ] 密码强度校验增加大小写字母 + 数字 + 特殊字符要求
- [ ] 找回密码功能（邮箱验证码 → 重置密码）

---

## P1：微信扫码登录功能完善

> 修复日期：2026-05-31

### 问题描述

[Login.vue](web/frontend/src/views/Login.vue) 的"微信登录"选项卡显示 `微信扫码功能开发中`，是一个硬编码的占位符，面试官看到会直接判定功能未完成。

### 架构设计

真实微信扫码登录基于 OAuth 2.0 协议，流程如下：

```
前端生成二维码 → 用户微信扫码 → 微信回调 redirect_uri?code=xxx
→ 后端用 code 换 access_token → 换 openid/unionid → 创建/关联用户 → 签发 JWT
```

由于微信开放平台需要企业资质才能获取 AppID，当前采用**完整架构 + 模拟演示**方案：
- 后端完整实现场景生成、状态轮询、扫码确认流程
- 前端完整实现二维码渲染、状态展示、轮询检测
- "模拟微信扫码登录"按钮替代真实微信回调，后续有了 AppID 只需改 2 处代码即可切换

### 修改清单

| 文件 | 修改内容 |
|------|----------|
| [`springboot/src/main/java/com/zyt/service/UserService.java`](springboot/src/main/java/com/zyt/service/UserService.java) | 新增 3 个接口方法签名：`wechatQrcode()`、`wechatCheck()`、`wechatSimulate()` |
| [`springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | 新增 3 个微信登录方法实现（约 100 行），含完整注释说明真实 OAuth 流程 |
| [`springboot/src/main/java/com/zyt/controller/UserController.java`](springboot/src/main/java/com/zyt/controller/UserController.java) | 新增 3 个 REST 端点：`POST /user/wechat/qrcode`、`GET /user/wechat/check`、`POST /user/wechat/simulate` |
| [`springboot/src/main/java/com/zyt/config/WebConfig.java`](springboot/src/main/java/com/zyt/config/WebConfig.java) | 拦截器白名单新增 3 个微信端点 |
| [`web/frontend/src/api/user.js`](web/frontend/src/api/user.js) | 新增 `getWechatQrcode()`、`checkWechatScan()`、`simulateWechatScan()` |
| [`web/frontend/src/views/Login.vue`](web/frontend/src/views/Login.vue) | 完全重写微信登录 Tab：二维码渲染 → 状态轮询 → 模拟扫码 → 自动登录 |
| [`web/frontend/package.json`](web/frontend/package.json) | 新增依赖 `qrcode`（前端二维码库） |

### API 设计

| 端点 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/user/wechat/qrcode` | POST | 生成扫码场景，返回 sceneId + authUrl | 无需登录 |
| `/user/wechat/check?sceneId=xxx` | GET | 轮询扫码状态：pending / scanned / confirmed / expired | 无需登录 |
| `/user/wechat/simulate?sceneId=xxx` | POST | 模拟微信扫码确认（演示用） | 无需登录 |

### 前端状态机

```
'' (初始)
  ↓ 切换到微信 Tab
'pending'（二维码已生成，等待扫码）
  ↓ 模拟扫码点击                    ↓ 轮询检测到 confirmed
'confirmed'（签发 Token，自动登录）  →  跳转 /home
  ↓ 二维码过期（5分钟）
'expired'（点击刷新重新生成）
```

### 切换到真实微信 OAuth

只需要 2 处修改：

1. **UserServiceImpl.wechatQrcode()** — 将 `authUrl` 替换为真实微信授权 URL：
```java
data.put("authUrl", "https://open.weixin.qq.com/connect/qrconnect?"
    + "appid=" + wechatAppId
    + "&redirect_uri=" + URLEncoder.encode(callbackUrl, "UTF-8")
    + "&response_type=code"
    + "&scope=snsapi_login"
    + "&state=" + sceneId);
```

2. **新增回调端点** — `GET /user/wechat/callback?code=xxx&state=yyy`：
   - 用 `code` 调用微信 API 换取 `access_token` 和 `openid`
   - 更新 Redis 场景状态为 `confirmed`
   - 创建/关联用户 → 签发 Token
   - 返回一个确认页面（或 JSON）

3. **删除模拟端点** — 移除 `/user/wechat/simulate` 及其前端调用

### 技术要点

- 二维码使用 `qrcode` 库在前端渲染为 Canvas → base64，无需服务端生成图片
- 轮询间隔 2 秒，`confirmed` 后自动停止，组件销毁时 `onUnmounted` 清理定时器
- Redis 场景数据 5 分钟 TTL，过期后自动清除
- 演示用户邮箱格式：`wx_{openid}@wechat.local`，密码随机生成，与正常注册用户隔离
- 微信 Tab 切换时自动触发二维码生成（通过 `watch(activeTab)` 实现）

---

## P2：Knife4j + Spring Boot 3.5.x 兼容性修复

> 修复日期：2026-06-03

### 问题描述

启动后端后访问 `http://localhost:8080/doc.html`，Knife4j UI 主页可以加载，但接口列表不显示（左侧菜单缺少「接口文档」项）。访问 `/v3/api-docs/default` 返回 HTTP 500。

后端控制台报错：

```
java.lang.NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
```

### 根本原因

Spring Boot 3.5.x 使用的 Spring Framework 6.2.x 中，`ControllerAdviceBean` 类的构造方法发生了破坏性变更：

| Spring Boot 版本 | `ControllerAdviceBean` 构造方法 |
|---|---|
| ≤ 3.3.x (Spring 6.0/6.1) | `ControllerAdviceBean(Object bean)` — 单参数 |
| ≥ 3.4.x (Spring 6.2+) | `ControllerAdviceBean(String, BeanFactory, ControllerAdvice)` — 三参数 |

Knife4j 4.5.0 内嵌的 `springdoc-openapi` 版本过低，仍然调用已删除的单参数构造方法，导致 `NoSuchMethodError`。

### 修复方案

在 `GlobalExceptionHandler` 上添加 `@Hidden` 注解，告诉 SpringDoc 跳过该类：

```java
import io.swagger.v3.oas.annotations.Hidden;

@Hidden                    // 告诉 SpringDoc 不扫描此类（避免 ControllerAdviceBean 兼容性问题）
@RestControllerAdvice
public class GlobalExceptionHandler {
    // ...
}
```

同时移除 `UserController` 中所有方法级 `@Tag` 注解（类级 + 方法级 `@Tag` 在 SpringDoc 内部会产生合并冲突），将所有端点合并到单一 `@Tag(name = "用户认证")` 分组下。

### 修改清单

| 文件 | 修改内容 |
|------|----------|
| [`GlobalExceptionHandler.java`](springboot/src/main/java/com/zyt/exception/GlobalExceptionHandler.java) | 添加 `@Hidden` 注解；新增 `NoResourceFoundException` 静默处理（解决 favicon.ico 缺失噪音日志） |
| [`UserController.java`](springboot/src/main/java/com/zyt/controller/UserController.java) | 移除所有方法级 `@Tag`；合并为单一类级 `@Tag(name = "用户认证")` |
| [`Knife4jConfig.java`](springboot/src/main/java/com/zyt/config/Knife4jConfig.java) | 新增 `GlobalOpenApiCustomizer` Bean（修复 Authorize 按钮 Token 不注入问题，见下方补充修复） |

### 补充修复：Authorize 按钮填入 Token 后不生效

**问题**：即使 `/v3/api-docs/default` 正常返回、接口列表正常显示，在右上角 Authorize 按钮填入 Token 后，调试需要鉴权的接口（如 `/user/me`）时 `Authorization` header 没有被注入到请求中，返回 401 "未携带 Token，请先登录"。

而 Knife4j 自身的「文档管理 → 全局参数设置」手动添加 `Authorization` header 则能正常工作。

**根因**：Knife4j 不会自动继承 OpenAPI 根级别的 `security` 声明。`Knife4jConfig` 中的 `addSecurityItem()` 只在 OpenAPI JSON 的根级别添加了 security 字段，但 Knife4j 在发起请求时只检查**每个 Operation 自身的 `security` 字段**。所有 Operation 的 security 字段为空 → Knife4j 不注入 Token。

**修复**：在 `Knife4jConfig.java` 中新增 `GlobalOpenApiCustomizer` Bean，SpringDoc 在生成完所有 Operation 后调用它——遍历所有 path 下的 GET/POST/PUT/DELETE 操作，逐个注入 `SecurityRequirement`：

```java
@Bean
public GlobalOpenApiCustomizer globalSecurityCustomizer() {
    return openApi -> {
        if (openApi.getPaths() == null) return;
        openApi.getPaths().forEach((path, pathItem) -> {
            if (pathItem.getGet() != null)
                pathItem.getGet().addSecurityItem(
                        new SecurityRequirement().addList("Authorization"));
            if (pathItem.getPost() != null)
                pathItem.getPost().addSecurityItem(
                        new SecurityRequirement().addList("Authorization"));
            // ... put, delete 同理
        });
    };
}
```

修复后，每个 Operation 的 OpenAPI JSON 中都会出现 `"security": [{"Authorization": []}]`，Knife4j 识别到后自动注入 Authorize 按钮中保存的 Token。

### 长期方案

等待 Knife4j 官方发布适配 Spring Boot 3.5.x 的新版本（预计 > 4.5.0），或手动升级 springdoc-openapi 到 2.8.x+ 并排除 Knife4j 内置的旧版依赖。届时可以移除 `@Hidden` 注解并恢复方法级 `@Tag` 分组。

---

## P3：Docker 构建失败 — JAR 缺少 Main-Class 清单属性

> 修复日期：2026-06-06

### 问题描述

执行 `docker compose up -d --build` 后，`auth-backend` 容器反复崩溃重启，日志持续输出：

```
no main manifest attribute, in app.jar
```

`java -jar app.jar` 无法启动，因为 JAR 的 `MANIFEST.MF` 文件中没有 `Main-Class` 条目，JVM 不知道该执行哪个类的 `main()` 方法。

### 根本原因

**`pom.xml` 缺少 `spring-boot-maven-plugin` 声明。**

Spring Boot 的可执行 fat JAR 并非 Maven 默认行为——普通的 `mvn package` 只会打出包含本项目 `.class` 文件的**瘦 JAR**，不包含依赖、也没有 Spring Boot 启动器。要让 JAR 可执行，必须由 `spring-boot-maven-plugin` 介入：

| 场景 | Maven 行为 | JAR 内容 |
|------|-----------|---------|
| 无 plugin | `mvn package` → 普通 JAR | 只有本项目 `.class`，无 `Main-Class` |
| 有 plugin | `mvn package` → Spring Boot fat JAR | 本项目 + 所有依赖 + `JarLauncher` + `Start-Class` 清单属性 |

`spring-boot-starter-parent` 只提供**插件版本管理**（`<pluginManagement>`），不会自动激活插件——必须在 `<build><plugins>` 中显式声明。

### 修复方案

**1. [`pom.xml`](springboot/pom.xml) — 添加 `spring-boot-maven-plugin`**

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

该插件为 JAR 注入 Spring Boot 启动器：
- `Main-Class: org.springframework.boot.loader.JarLauncher` — 引导 Spring Boot 类加载器
- `Start-Class: com.zyt.AppStart` — 实际入口类

**2. [`Dockerfile`](springboot/Dockerfile) — 优化 JAR 文件定位**

旧版：
```dockerfile
RUN mv target/*.jar app.jar
```

新版：
```dockerfile
RUN find target -maxdepth 1 -name "*.jar" ! -name "*.original" -exec mv {} app.jar \;
```

`spring-boot-maven-plugin` 打包时除了生成 fat JAR（如 `Springboot_server-1.0-SNAPSHOT.jar`），还会保留原始瘦 JAR 为 `.jar.original` 文件。旧版 `mv target/*.jar app.jar` 在存在多个 `.jar` 文件时行为不确定（可能报错或覆盖错误文件），新版 `find` 命令明确排除 `.original` 文件，只取 fat JAR。

### 修复效果

| 检查项 | 修复前 | 修复后 |
|--------|--------|--------|
| `docker compose up -d --build` | ❌ 容器崩溃重启 | ✅ `auth-backend` 正常启动 |
| `docker logs auth-backend` | `no main manifest attribute` | `Started AppStart in X seconds` |
| JAR 可执行性 | 普通 JAR（无入口） | Spring Boot fat JAR（完整可执行） |

### 技术要点

- **Parent POM ≠ Plugin 声明**：`spring-boot-starter-parent` 通过 `<pluginManagement>` 统一管理插件版本，避免子模块版本冲突，但**不会自动激活插件**。每个需要该插件的模块（或父子工程的父 POM）必须显式在 `<build><plugins>` 中声明。
- **Docker 多阶段构建中的通配符陷阱**：`mvn package` 的 `target/` 目录可能包含多个 `.jar` 文件（主 JAR、源码 JAR、original JAR），通配符 `*.jar` 匹配结果取决于 shell 的 glob 展开顺序，不可靠。`find` + 白名单/黑名单过滤是更稳健的做法。
- **容器无限重启的根因**：`ENTRYPOINT ["java", "-jar", "app.jar"]` 执行失败后进程退出（exit code ≠ 0），Docker 根据默认 `restart: unless-stopped` 策略自动重启，形成崩溃循环。

---

## P4：Docker 部署 — MyBatis 日志噪声 + 邮件发送失败 + 前端误报成功

> 修复日期：2026-06-06

### 问题描述

Docker 部署后出现三个相关联的问题：

1. **`docker logs` 被 MyBatis 调试日志淹没**：日志中出现 `[org.apache.ibatis.session.defaults.DefaultSqlSession@5cdb3421]` 等无意义对象引用，以及每条 SQL 的完整执行信息。
2. **邮件发送失败但看不到异常原因**：日志只有 `邮件发送失败，收件人: 3376705489@qq.com` 一行，没有异常堆栈，无法定位是认证失败、DNS 解析失败还是 SMTP 连接超时。
3. **前端提示"验证码已发送"但用户收不到邮件**：用户在注册/找回密码页面看到绿色成功提示，但邮箱里没有任何邮件，体验割裂。

### 根本原因分析

**问题 1 根因**：`application.yml` 中 MyBatis-Plus 配置了 `StdOutImpl` 日志适配器：

```yaml
# 修复前
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

`StdOutImpl` 将**每条 SQL + 参数 + 结果集元数据**通过 `System.out.println()` 直接输出，完全绕过了 Spring Boot 的日志体系（无法通过 `logging.level` 控制），输出的 `DefaultSqlSession@5cdb3421` 是 MyBatis 内部 `toString()` 的结果，对排查问题毫无帮助。

**问题 2 根因**：`EmailUtil.sendResetCode()` 在 P0 修复时被遗漏，仍使用 `e.printStackTrace()` 而非 `log.error()`：

```java
// sendVerificationCode()（已在 P0 修复）
} catch (Exception e) {
    log.error("邮件发送失败，收件人: {}", to, e);  // ✅ 有完整堆栈
}

// sendResetCode()（遗漏，未修复）
} catch (Exception e) {
    e.printStackTrace();  // ❌ 只有一行消息，没有异常原因
}
```

`printStackTrace()` 输出到 `System.err`，在 Docker 日志收集系统中没有时间戳、日志级别、线程名等结构化信息，且无法被 ELK/Splunk 等日志系统检索。

**问题 3 根因**：邮件发送是异步的（`new Thread().start()`），HTTP 响应在邮件线程执行前就返回了：

```
时间线：
t0 → HTTP 请求进入 → 校验 → 保存验证码到 Redis
t1 → return ResponseUtil(200, "验证码已发送至邮箱", ...)  ← 前端收到成功
t2 → 新线程启动 → 连接 SMTP → 认证失败 → log.error()     ← 用户看不到

.SMTP 认证失败的真实原因：.env 中 MAIL_USERNAME / MAIL_PASSWORD 为空
→ spring.mail.username = "" → JavaMailSender 无法通过 163.com SMTP 认证
→ 异步线程静默失败（修复前甚至没有 log.error）
```

**关键认知**：异步发送邮件**不是 bug**——SMTP 连接可能耗时 3-10 秒，同步等待会让 HTTP 超时，前端直接报"网络错误"。业界标准做法就是"先保存验证码到 Redis → 立即返回成功 → 异步发邮件 → 失败则记录日志"。真正的 bug 是：(a) 失败没有被正确记录，(b) 没有在启动时告知运维人员邮件凭据缺失。

### 修复方案

**1. [`application.yml`](springboot/src/main/resources/application.yml) — MyBatis 日志切换为 Slf4j**

```yaml
# 修复后
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # 纳入 Spring Boot 日志体系
    map-underscore-to-camel-case: true

logging:
  level:
    "[com.zyt.mapper]": WARN    # 生产环境关闭 SQL 日志
    root: INFO
```

切换效果：
- `DefaultSqlSession@5cdb3421` 等调试信息不再出现在 `docker logs` 中
- 开发时如需查看 SQL，只需将 `com.zyt.mapper` 日志级别改为 `DEBUG`
- SQL 日志纳入 Spring Boot 日志体系，有统一的时间戳和日志级别前缀

**2. [`EmailUtil.java`](springboot/src/main/java/com/zyt/utils/EmailUtil.java) — 两处修复**

修复 A：`sendResetCode()` 的 `e.printStackTrace()` → `log.error("邮件发送失败，收件人: {}", to, e)`

修复 B：新增 `@PostConstruct` 启动检查方法：

```java
@PostConstruct
public void checkMailConfig() {
    if (from == null || from.isBlank()) {
        log.warn("================================================");
        log.warn("⚠ 邮件凭据未配置！");
        log.warn("  注册验证码、密码重置等邮件功能将不可用。");
        log.warn("  配置方法：在 .env 文件中设置 MAIL_USERNAME 和 MAIL_PASSWORD");
        log.warn("  - MAIL_USERNAME: 你的163邮箱地址（如 your@163.com）");
        log.warn("  - MAIL_PASSWORD: SMTP 授权码（非登录密码，在 mail.163.com 设置中获取）");
        log.warn("================================================");
    } else {
        log.info("邮件服务已配置，发件人: {}", from);
    }
}
```

这样 `docker compose up` 后立即在日志中看到邮件配置状态，不用等到用户触发发送才发现失败。

**3. [`.env`](.env) — 注释增强**

旧版只写"留空则邮件功能不可用"，新版增加了失败后果说明和配置步骤引导。

### 修复效果

| 问题 | 修复前 | 修复后 |
|------|--------|--------|
| `docker logs` 噪音 | MyBatis 每 SQL 一行 + `DefaultSqlSession` 引用 | 干净，只有业务日志 |
| 邮件失败可诊断性 | `邮件发送失败` 无堆栈 | 完整异常堆栈（auth/ DNS/ timeout 一目了然） |
| 启动时邮件状态 | 静默，不知邮件功能是否可用 | 启动日志明确告知"已配置"或"未配置" |
| 前端误报成功 | 用户看到成功但收不到邮件 | 仍需配置 SMTP 凭证（见下方部署步骤） |

### 部署步骤：启用邮件功能

要使邮件功能在 Docker 中正常工作，编辑 `.env` 文件：

```bash
MAIL_USERNAME=your_email@163.com        # 你的 163 邮箱地址
MAIL_PASSWORD=your_smtp_authorization    # SMTP 授权码（不是登录密码！）
```

然后重建容器：

```bash
docker compose up -d --build
docker logs auth-backend | grep 邮件
# 应该看到: "邮件服务已配置，发件人: your_email@163.com"
```

获取 163 SMTP 授权码：登录 [mail.163.com](https://mail.163.com) → 设置 → POP3/SMTP/IMAP → 开启 SMTP 服务 → 按提示发送短信 → 获取授权码。

### 技术要点

- **`StdOutImpl` vs `Slf4jImpl`**：`StdOutImpl` 是 MyBatis 内置的调试适配器，输出到 `System.out`，完全独立于应用日志体系。`Slf4jImpl` 将 MyBatis 日志桥接到 SLF4J，由 Spring Boot 的日志配置（`logging.level`）统一控制。
- **`@PostConstruct` 在 `@Value` 注入之后执行**：Spring 容器在完成所有依赖注入后调用 `@PostConstruct` 方法，此时 `from` 字段已被 `@Value` 赋值，可以安全读取。
- **异步邮件失败无法实时反馈给用户**：这是异步架构的固有限制。替代方案（同步发送、消息队列 + WebSocket 推送）都显著增加复杂度，对于本项目规模不划算。业界小型项目的标准做法就是：异步 + 完整日志 + 支持用户重试（60 秒后可重新发送验证码）。

---

## 累计修改文件汇总

| 文件 | P0-1 | P0-2 | P1 微信 | P2 Knife4j | P3 Docker | P4 Docker 运维 |
|------|:----:|:----:|:-------:|:----------:|:---------:|:-------------:|
| [`JwtUtil.java`](springboot/src/main/java/com/zyt/utils/JwtUtil.java) | ✅ | — | — | — | — | — |
| [`UserService.java`](springboot/src/main/java/com/zyt/service/UserService.java) | — | — | ✅ | — | — | — |
| [`UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | ✅ | ✅ | ✅ | — | — | — |
| [`UserController.java`](springboot/src/main/java/com/zyt/controller/UserController.java) | — | — | ✅ | ✅ | — | — |
| [`WebConfig.java`](springboot/src/main/java/com/zyt/config/WebConfig.java) | — | — | ✅ | — | — | — |
| [`GlobalExceptionHandler.java`](springboot/src/main/java/com/zyt/exception/GlobalExceptionHandler.java) | — | — | — | ✅ | — | — |
| [`Knife4jConfig.java`](springboot/src/main/java/com/zyt/config/Knife4jConfig.java) | — | — | — | ✅ | — | — |
| [`api/user.js`](web/frontend/src/api/user.js) | — | — | ✅ | — | — | — |
| [`Login.vue`](web/frontend/src/views/Login.vue) | — | — | ✅ | — | — | — |
| [`package.json`](web/frontend/package.json) | — | — | ✅（qrcode） | — | — | — |
| [`pom.xml`](springboot/pom.xml) | — | — | — | — | ✅（plugin） | — |
| [`Dockerfile`](springboot/Dockerfile) | — | — | — | — | ✅（find） | — |
| [`application.yml`](springboot/src/main/resources/application.yml) | — | — | — | — | — | ✅（日志 + MyBatis） |
| [`EmailUtil.java`](springboot/src/main/java/com/zyt/utils/EmailUtil.java) | — | — | — | — | — | ✅（日志 + 启动检查） |
| [`.env`](.env) | — | — | — | — | — | ✅（注释增强） |
