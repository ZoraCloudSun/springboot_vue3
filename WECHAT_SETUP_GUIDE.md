# 微信扫码登录 — 完整配置与使用指南

> 最后更新：2026-05-31

---

## 架构总览

```
┌─────────── PC 浏览器 (localhost:3000) ───────────┐
│                                                   │
│  Login.vue                                        │
│  ┌─────────────────────────────────────────┐      │
│  │ 微信登录 Tab                             │      │
│  │  1. POST /user/wechat/qrcode → 获取     │      │
│  │     sceneId + 真实 OAuth 授权 URL        │      │
│  │  2. qrcode 库生成二维码 (Canvas→base64)  │      │
│  │  3. setInterval 2s 轮询 /user/wechat/check│     │
│  │     ├─ pending: 继续等待                 │      │
│  │     ├─ scanned: 展示头像+昵称+绑定表单   │      │
│  │     ├─ confirmed: 自动登录 → /home       │      │
│  │     └─ expired: 提示刷新                 │      │
│  └─────────────────────────────────────────┘      │
│                                                   │
└───────────────────┬───────────────────────────────┘
                    │ HTTP (Vite proxy → localhost:8080)
                    ▼
┌─────────── Spring Boot (localhost:8080) ──────────┐
│                                                   │
│  UserController                                   │
│  ├─ POST /user/wechat/qrcode                      │
│  │    → UserServiceImpl.wechatQrcode()            │
│  │    → 生成 sceneId (UUID)                       │
│  │    → 拼接真实微信 OAuth URL                     │
│  │    → Redis: wechat:scene:{sceneId} = "pending" │
│  │    → 返回 { sceneId, authUrl }                 │
│  │                                                │
│  ├─ GET /user/wechat/check?sceneId=xxx            │
│  │    → 查 Redis 返回状态 + 微信用户信息            │
│  │                                                │
│  ├─ GET /user/wechat/callback?code=&state=          │
│  │    → 微信重定向到此（手机浏览器）                  │
│  │    → WechatUtil.getAccessToken(code)              │
│  │    → WechatUtil.getUserInfo(token, openid)        │
│  │    → 查 DB: openid 是否已绑定?                    │
│  │       ├─ 已绑定 → 签发 JWT + confirmed            │
│  │       │   → Redis: token = JWT                    │
│  │       │   → 返回 HTML "✅ 授权成功"               │
│  │       │   → PC 轮询 → 直接登录 ✅                  │
│  │       │                                          │
│  │       └─ 未绑定 → scanned + 等待邮箱绑定           │
│  │           → Redis: data = {nickname,avatar,openid}│
│  │           → 返回 HTML "✅ 授权成功"               │
	│  │           → PC 轮询 → 展示邮箱绑定表单              │
	│  │                                                  │
	│  └─ POST /user/wechat/bind-email                    │
	│       → 校验邮箱验证码                                │
	│       → 创建/关联用户 + 签发 JWT                      │
	│       → Redis: scene = "confirmed"                  │
	│       → Redis: wechat:token:{sceneId} = JWT Token   │
│                                                   │
│  Redis Key 设计:                                   │
│  ┌──────────────────────┬──────┬─────────────────┐ │
│  │ Key                  │ TTL  │ 值              │ │
│  ├──────────────────────┼──────┼─────────────────┤ │
│  │ wechat:scene:{id}    │ 5min │ pending/scanned │ │
│  │                      │      │ /confirmed/     │ │
│  │                      │      │ expired         │ │
│  │ wechat:data:{id}     │ 5min │ JSON {nickname, │ │
│  │                      │      │ avatar, openid} │ │
│  │ wechat:token:{id}    │ 5min │ JWT accessToken │ │
│  └──────────────────────┴──────┴─────────────────┘ │
│                                                   │
└───────────────────┬───────────────────────────────┘
                    │ HTTPS
                    ▼
┌─────────── 微信服务器 ────────────────────────────┐
│  api.weixin.qq.com                                │
│  /sns/oauth2/access_token?appid=&secret=&code=    │
│  /sns/userinfo?access_token=&openid=              │
└───────────────────────────────────────────────────┘
```

---

## 前提条件

### 1. 获取微信测试号（免费，无需企业资质）

微信测试号提供完整的 OAuth 2.0 网页授权能力，足以用于学习和开发：

1. 浏览器打开 [微信公众平台测试号](https://mp.weixin.qq.com/debug/cgi-bin/sandbox?t=sandbox/login)
2. 用微信扫码登录
3. 页面顶部获得 **appID** 和 **appsecret**

> 📋 本项目已配置的测试号信息：
> - appID: `xxxxxx`
> - appsecret: `xxxxxx`
> 
> 测试号有调用频率限制（约 2000次/天），足够开发测试使用。

### 2. 内网穿透（本地开发必须）

微信回调需要一个**公网可访问的 HTTPS URL**。推荐 **ngrok**（免费版自带 HTTPS）：

```bash
# 1. 下载安装: https://ngrok.com/download
# 2. 在 ngrok 官网注册（免费），获取 authtoken
ngrok config add-authtoken <你的token>

# 3. 启动穿透（将本地 8080 端口暴露到公网）
ngrok http 8080

# 4. 记录 ngrok 提供的公网域名，例如:
#    https://e0c3-223-86-25-248.ngrok-free.app
```

> ⚠️ 免费版 ngrok 每次重启域名会变化。如果域名变了，需要同步更新：
> 1. 微信测试号页面的回调域名
> 2. `application.yml` 中的 `wechat.callback-url`

### 3. 配置微信测试号回调域名

在微信测试号管理页面，**往下滚动**找到「**网页授权获取用户基本信息**」一栏：

1. 点击右侧「修改」按钮
2. 填入 ngrok 域名（**只填域名，不带协议和路径**）：

   ```
   e0c3-223-86-25-248.ngrok-free.app
   ```

3. 点击确定

> ⚠️ **不要填在顶部的「接口配置信息」里！** 那个是给公众号消息机器人用的，和我们需要的网页授权不是同一个功能。如果填错位置，扫码后会报 `redirect_uri 参数错误`。

---

## 项目配置

### 1. 数据库迁移

微信登录需要在 `user` 表增加 `openid`、`nickname`、`avatar` 三个字段：

```bash
# Windows PowerShell / CMD
mysql -u root -p123456 springboot_zyt < DB_MIGRATION.sql

# 或手动执行 SQL
mysql -u root -p123456
```

```sql
USE springboot_zyt;

ALTER TABLE user
    ADD COLUMN openid   VARCHAR(128) NULL COMMENT '微信 openid' AFTER password,
    ADD COLUMN nickname VARCHAR(64)  NULL COMMENT '微信昵称'   AFTER openid,
    ADD COLUMN avatar   VARCHAR(512) NULL COMMENT '微信头像URL' AFTER nickname;

CREATE UNIQUE INDEX idx_user_openid ON user (openid);
```

验证迁移结果：
```sql
DESCRIBE user;
-- 应看到: id, email, password, openid, nickname, avatar
```

### 2. 配置 application.yml

编辑 `springboot/src/main/resources/application.yml`：

```yaml
wechat:
  app-id: ${WECHAT_APP_ID:xxxxxx}
  app-secret: ${WECHAT_APP_SECRET:xxxxxx}
  callback-url: ${WECHAT_CALLBACK_URL:https://e0c3-223-86-25-248.ngrok-free.app/user/wechat/callback}
```

> 💡 **环境变量方式**（推荐用于团队协作，避免敏感信息提交到 Git）：
> ```bash
> # Windows PowerShell
> $env:WECHAT_APP_ID="xxxxxx"
> $env:WECHAT_APP_SECRET="xxxxxx"
> $env:WECHAT_CALLBACK_URL="https://e0c3-223-86-25-248.ngrok-free.app/user/wechat/callback"
>
> # Linux / macOS
> export WECHAT_APP_ID=xxxxxx
> export WECHAT_APP_SECRET=xxxxxx
> export WECHAT_CALLBACK_URL=https://e0c3-223-86-25-248.ngrok-free.app/user/wechat/callback
> ```

### 3. 启动项目

```bash
# 终端 1：启动后端（端口 8080）
cd springboot
mvn spring-boot:run

# 终端 2：启动前端（端口 3000）
cd web/frontend
npm run dev

# 终端 3：启动 ngrok（如未启动）
ngrok http 8080
```

---

## 使用流程（完整测试步骤）

### 路径一：已绑定微信（老用户直接登录）

> 适用于：该微信之前已经绑定过邮箱

1. 浏览器访问 `http://localhost:3000/login`
2. 点击顶部的「**微信登录**」选项卡
3. 页面显示二维码
4. **拿出手机，打开微信，扫描电脑屏幕上的二维码**
5. 手机微信弹出授权页面 → 点击「**确认登录**」
6. 手机浏览器显示"✅ 授权成功"
7. **电脑页面自动登录**，显示"XX，登录成功" → 自动跳转 `/home`
8. 🎉 全程无需输入邮箱和验证码，秒进！

### 路径二：未绑定微信（新用户邮箱绑定）

> 适用于：第一次使用微信登录

1. 浏览器访问 `http://localhost:3000/login`
2. 点击顶部的「**微信登录**」选项卡
3. 页面显示二维码
4. **拿出手机，打开微信，扫描电脑屏幕上的二维码**
5. 手机微信弹出授权页面 → 点击「**确认登录**」
6. 手机浏览器显示"✅ 授权成功，请返回电脑继续完成邮箱绑定"
7. **电脑页面自动检测到扫码**，展示：
   - 微信头像（圆形）
   - 微信昵称
   - 邮箱输入框 + 验证码输入框 + 发送验证码按钮
8. 输入你的邮箱 → 点击「**发送验证码**」
9. 查看邮箱，输入 6 位验证码
10. 点击「**绑定并登录**」
11. 提示"绑定成功，正在登录..." → 自动跳转 `/home`
12. 🎉 下次再扫码就可以直接登录了！

### 账号关联规则

| 场景 | 触发时机 | 行为 |
|------|----------|------|
| 微信已绑定（老用户） | 扫码确认时 | ✅ 直接登录，更新昵称/头像，PC 自动跳转首页 |
| 邮箱未注册（新用户） | 扫码后绑定邮箱 | ✅ 创建新账号，关联微信 openid、昵称、头像 |
| 邮箱已注册（未绑定微信） | 扫码后绑定邮箱 | ✅ 将微信 openid 关联到已有账号（账号升级） |
| 邮箱已绑定其他微信 | 绑定邮箱时 | ❌ 拒绝："该邮箱已绑定其他微信账号" |
| 微信已绑定其他邮箱 | 绑定邮箱时 | ❌ 拒绝："该微信已绑定其他账号" |

### 二维码过期处理

- 二维码有效期 **5 分钟**
- 过期后页面显示"二维码已过期"，点击「**刷新二维码**」重新生成
- 如果扫码后 5 分钟内未完成邮箱绑定，扫码状态也会过期

---

## 关键文件清单

| 文件 | 职责 |
|------|------|
| [WechatConfig.java](springboot/src/main/java/com/zora/config/WechatConfig.java) | 微信配置类：appId、appSecret、callbackUrl + API 端点常量 |
| [WechatUtil.java](springboot/src/main/java/com/zora/utils/WechatUtil.java) | 微信 API 工具：code→access_token→用户信息 |
| [UserServiceImpl.java](springboot/src/main/java/com/zora/service/impl/UserServiceImpl.java) | 微信登录业务逻辑：qrcode/check/callback/bindEmail |
| [UserController.java](springboot/src/main/java/com/zora/controller/UserController.java) | 微信 REST 端点：qrcode/check/callback/bind-email/send-bind-code |
| [Login.vue](web/frontend/src/views/Login.vue) | 前端页面：四状态 UI + 邮箱绑定表单 + 轮询 |
| [user.js](web/frontend/src/api/user.js) | 前端 API：getWechatQrcode/checkWechatScan/sendBindCode/bindWechatEmail |
| [application.yml](springboot/src/main/resources/application.yml) | 微信凭证配置 |

---

## 常见问题与故障排除

### Q1：扫码后手机显示「redirect_uri 参数错误」

**原因**：微信测试号回调域名配置不正确。

**排查步骤**：
1. 检查测试号页面「网页授权获取用户基本信息」中的域名是否与 ngrok 域名完全一致
2. 确认填的是**纯域名**（如 `e0c3-223-86-25-248.ngrok-free.app`），不含 `https://`、不含路径
3. 确认 `application.yml` 中 `callback-url` 以 `https://` 开头（ngrok 强制 HTTPS）
4. ngrok 免费版如果重启，域名会变化，需同步更新

### Q2：扫码后页面一直显示「等待扫码」，手机已确认授权

**原因**：微信回调没有到达后端。

**排查步骤**：
1. 检查 ngrok 是否正常运行（浏览器访问 `https://你的域名/user/wechat/check?sceneId=test` 看能否通）
2. 检查后端是否运行在 8080 端口
3. 检查后端控制台是否有 `/user/wechat/callback` 的请求日志
4. 确认测试号回调域名配置正确

### Q3：扫码后报「code 已被使用」或「invalid code」

**原因**：微信的授权码（code）**只能使用一次**，且有效期极短（约 5 分钟）。

**解决**：刷新页面重新生成二维码即可。

### Q4：提示「获取微信 access_token 失败」

**原因**：appID 或 appsecret 配置不正确。

**排查步骤**：
1. 检查 `application.yml` 中 app-id 和 app-secret 是否正确
2. 检查微信测试号页面中的 appID 和 appsecret 是否与配置一致
3. 注意 appsecret 区分大小写
4. 检查后端启动日志中是否有 `isConfigured()` 相关日志

### Q5：微信头像不显示

**原因**：部分微信用户未设置头像时，微信 API 返回空字符串。

**处理**：前端已做兼容处理——无头像时显示默认图标（`PictureFilled`）。

### Q6：绑定邮箱时提示「验证码已过期」

**原因**：验证码有效期 5 分钟，或已被使用。

**解决**：重新发送验证码。注意绑定用的验证码和注册用的验证码共用 Redis key，60 秒内不能重复发送。

### Q7：ngrok 显示「too many requests」或 429

**原因**：ngrok 免费版有每分钟请求限制。

**解决**：
1. 升级 ngrok 付费版
2. 或使用替代品：`npx localtunnel --port 8080`、`cloudflared tunnel`
3. 或将项目部署到有公网 IP 的云服务器

### Q8：Vite 代理导致微信回调失败

**说明**：微信回调是微信服务器直接请求后端（不经过浏览器），Vite 代理不影响。只要 ngrok → 后端 8080 端口通畅即可。

---

## 切换到正式微信开放平台

当你获得微信开放平台（网站应用）资质后，只需修改 3 处：

**1. WechatConfig.java** — 修改授权 URL：
```java
// 测试号用的（当前）
public static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";

// 改为开放平台用的
public static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
```

**2. UserServiceImpl.java** — 修改 scope 参数：
```java
// 测试号用的（当前）
+ "&scope=snsapi_userinfo"

// 改为开放平台用的
+ "&scope=snsapi_login"
```

**3. application.yml** — 替换为开放平台凭证：
```yaml
wechat:
  app-id: wx你的开放平台AppID
  app-secret: 你的开放平台AppSecret
  callback-url: https://你的域名/user/wechat/callback
```

其余代码**无需改动**。

---

## 开发调试技巧

### 使用 curl 测试回调

```bash
# 模拟微信服务器回调（前提：先通过 wechatQrcode 获取 sceneId）
curl "http://localhost:8080/user/wechat/callback?code=test_code&state=你的sceneId"
# 会返回错误（因为 code 是假的），但可以验证端点可达性
```

### 查看 Redis 中的微信状态

```bash
redis-cli
> KEYS wechat:*
> GET wechat:scene:你的sceneId
> GET wechat:data:你的sceneId
```

### 前端调试轮询

在浏览器 DevTools → Network 标签中过滤 `wechat/check`，可以看到 2 秒一次的轮询请求。在 Application → Local Storage 中可以看到登录成功后的 Token 存储。

---

## 安全注意事项

1. **appsecret 绝对不能提交到公开仓库**。本项目使用 `application.yml` 中的默认值仅用于本地开发，生产环境必须使用环境变量。
2. **回调 URL 必须是 HTTPS**。微信要求 OAuth 2.0 回调地址为 HTTPS（测试号可例外，但 ngrok 自动提供 HTTPS）。
3. **state 参数防 CSRF**。本项目使用随机 sceneId 作为 state，回调时校验。
4. **code 一次性使用**。每个微信授权码只能使用一次，防止重放攻击。
5. **Token 不在 URL 中传递**。JWT Token 通过 Redis 中转（`wechat:token:{sceneId}`），不暴露在 URL 参数中。
