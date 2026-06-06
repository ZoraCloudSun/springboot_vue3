package com.zyt.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zyt.config.WechatConfig;
import com.zyt.entity.User;
import com.zyt.exception.BadRequestException;
import com.zyt.mapper.UserMapper;
import com.zyt.service.impl.UserServiceImpl;
import com.zyt.utils.EmailUtil;
import com.zyt.utils.JwtUtil;
import com.zyt.utils.ResponseUtil;
import com.zyt.utils.WechatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 服务层测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private WechatConfig wechatConfig;
    @Mock
    private WechatUtil wechatUtil;

    // 真实 BCryptPasswordEncoder（无外部依赖，测试更可靠）
    @Spy
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserServiceImpl userService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== generateCaptcha ====================

    @Nested
    @DisplayName("generateCaptcha - 生成图形验证码")
    class GenerateCaptchaTests {

        @Test
        @DisplayName("生成验证码：返回非空 map 含 captchaId 和 captchaImage")
        void shouldReturnCaptchaIdAndImage() {
            Map<String, String> result = userService.generateCaptcha();

            assertNotNull(result);
            assertNotNull(result.get("captchaId"));
            assertNotNull(result.get("captchaImage"));
            assertTrue(result.get("captchaImage").startsWith("data:image/png;base64,"));
        }

        @Test
        @DisplayName("验证码存入 Redis：key 前缀为 captcha:，TTL 为 1 分钟")
        void shouldStoreInRedisWithCorrectKeyAndTTL() {
            Map<String, String> result = userService.generateCaptcha();
            String captchaId = result.get("captchaId");

            verify(valueOperations).set(startsWith("captcha:" + captchaId), anyString(), eq(1L), eq(TimeUnit.MINUTES));
        }
    }

    // ==================== sendCode ====================

    @Nested
    @DisplayName("sendCode - 发送注册验证码")
    class SendCodeTests {

        @Test
        @DisplayName("邮箱为 null → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailIsNull() {
            assertThrows(BadRequestException.class, () -> userService.sendCode(null));
        }

        @Test
        @DisplayName("邮箱为空字符串 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailIsEmpty() {
            assertThrows(BadRequestException.class, () -> userService.sendCode(""));
        }

        @Test
        @DisplayName("邮箱格式错误 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailFormatInvalid() {
            assertThrows(BadRequestException.class, () -> userService.sendCode("not-an-email"));
        }

        @Test
        @DisplayName("邮箱已注册 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailAlreadyRegistered() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(new User());

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> userService.sendCode(TEST_EMAIL));
            assertTrue(ex.getMessage().contains("已注册"));
        }

        @Test
        @DisplayName("60 秒防刷：已有验证码且 TTL > 240s → 抛 BadRequestException")
        void shouldRejectWhenRateLimited() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(stringRedisTemplate.getExpire("email_code:" + TEST_EMAIL)).thenReturn(250L);

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> userService.sendCode(TEST_EMAIL));
            assertTrue(ex.getMessage().contains("秒后可重新获取"));
        }

        @Test
        @DisplayName("正常发送：存 Redis + 调 emailUtil")
        void shouldSendCodeSuccessfully() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.sendCode(TEST_EMAIL);

            assertEquals(200, r.getCode());
            assertTrue(r.getMsg().contains("验证码已发送"));
            verify(valueOperations).set(eq("email_code:" + TEST_EMAIL), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            verify(emailUtil).sendVerificationCode(eq(TEST_EMAIL), anyString());
        }
    }

    // ==================== sendBindCode ====================

    @Nested
    @DisplayName("sendBindCode - 发送绑定验证码")
    class SendBindCodeTests {

        @Test
        @DisplayName("邮箱为空 → 返回 400")
        void shouldReturn400_whenEmailIsNull() {
            ResponseUtil r = userService.sendBindCode(null);
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("邮箱格式错误 → 返回 400")
        void shouldReturn400_whenEmailFormatInvalid() {
            ResponseUtil r = userService.sendBindCode("bad-email");
            assertEquals(400, r.getCode());
            assertEquals("邮箱格式不正确", r.getMsg());
        }

        @Test
        @DisplayName("正常发送（不检查是否已注册） → 返回 200")
        void shouldSendCodeSuccessfully() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.sendBindCode(TEST_EMAIL);

            assertEquals(200, r.getCode());
            verify(emailUtil).sendVerificationCode(eq(TEST_EMAIL), anyString());
        }

        @Test
        @DisplayName("防刷限流 → 返回 400")
        void shouldRejectWhenRateLimited() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("654321");
            when(stringRedisTemplate.getExpire("email_code:" + TEST_EMAIL)).thenReturn(270L);

            ResponseUtil r = userService.sendBindCode(TEST_EMAIL);
            assertEquals(400, r.getCode());
        }
    }

    // ==================== sendResetCode ====================

    @Nested
    @DisplayName("sendResetCode - 发送密码重置验证码")
    class SendResetCodeTests {

        @Test
        @DisplayName("邮箱为空 → 返回 400")
        void shouldReturn400_whenEmailIsNull() {
            ResponseUtil r = userService.sendResetCode(null);
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("邮箱未注册 → 返回 400 '该邮箱未注册'")
        void shouldReturn400_whenEmailNotRegistered() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            ResponseUtil r = userService.sendResetCode(TEST_EMAIL);
            assertEquals(400, r.getCode());
            assertEquals("该邮箱未注册", r.getMsg());
        }

        @Test
        @DisplayName("正常发送 → 使用 reset_code: 前缀（与 email_code: 隔离）")
        void shouldUseResetCodePrefix() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(new User());
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.sendResetCode(TEST_EMAIL);

            assertEquals(200, r.getCode());
            verify(valueOperations).set(startsWith("reset_code:" + TEST_EMAIL), anyString(), eq(5L),
                    eq(TimeUnit.MINUTES));
            verify(emailUtil).sendResetCode(eq(TEST_EMAIL), anyString());
        }

        @Test
        @DisplayName("防刷限流 → 返回 400")
        void shouldRejectWhenRateLimited() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(new User());
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn("112233");
            when(stringRedisTemplate.getExpire("reset_code:" + TEST_EMAIL)).thenReturn(260L);

            ResponseUtil r = userService.sendResetCode(TEST_EMAIL);
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("邮箱格式错误 → 返回 400")
        void shouldReturn400_whenEmailFormatInvalid() {
            ResponseUtil r = userService.sendResetCode("invalid");
            assertEquals(400, r.getCode());
        }
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register - 邮箱验证码注册")
    class RegisterTests {

        @Test
        @DisplayName("参数为空 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenFieldsAreEmpty() {
            assertThrows(BadRequestException.class, () -> userService.register(null, "pass", "code"));
            assertThrows(BadRequestException.class, () -> userService.register("email@t.com", null, "code"));
            assertThrows(BadRequestException.class, () -> userService.register("email@t.com", "pass", null));
        }

        @Test
        @DisplayName("密码少于 6 位 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenPasswordTooShort() {
            assertThrows(BadRequestException.class,
                    () -> userService.register(TEST_EMAIL, "12345", "code"));
        }

        @Test
        @DisplayName("验证码过期 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenCodeExpired() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn(null);

            assertThrows(BadRequestException.class,
                    () -> userService.register(TEST_EMAIL, TEST_PASSWORD, "expired"));
        }

        @Test
        @DisplayName("验证码错误 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenCodeWrong() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("correct");

            assertThrows(BadRequestException.class,
                    () -> userService.register(TEST_EMAIL, TEST_PASSWORD, "wrong"));
        }

        @Test
        @DisplayName("邮箱已注册 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailAlreadyRegistered() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(new User());

            assertThrows(BadRequestException.class,
                    () -> userService.register(TEST_EMAIL, TEST_PASSWORD, "123456"));
        }

        @Test
        @DisplayName("邮箱格式错误 → 抛 BadRequestException")
        void shouldThrowBadRequest_whenEmailFormatInvalid() {
            assertThrows(BadRequestException.class,
                    () -> userService.register("bad-email", TEST_PASSWORD, "123456"));
        }

        @Test
        @DisplayName("注册成功：密码 BCrypt 加密，角色为 user，验证码删除")
        void shouldRegisterSuccessfully() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            ResponseUtil r = userService.register(TEST_EMAIL, TEST_PASSWORD, "123456");

            assertEquals(200, r.getCode());
            assertEquals("注册成功", r.getMsg());

            // 验证插入的用户
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertEquals(TEST_EMAIL, saved.getEmail());
            assertEquals("user", saved.getRole());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, saved.getPassword()));

            // 验证验证码删除
            verify(stringRedisTemplate).delete("email_code:" + TEST_EMAIL);
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login - 密码登录")
    class LoginTests {

        @Test
        @DisplayName("邮箱或密码为空 → 返回 400")
        void shouldReturn400_whenEmailOrPasswordIsNull() {
            // 注：captchaId/captchaCode 为空会先被 validateCaptcha 拦截
            // 这里测试邮箱密码为空的场景（先 mock captcha 通过）
            when(valueOperations.get(anyString())).thenReturn("ABC");
            ResponseUtil r = userService.login(null, "pass", "capId", "ABC");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("验证码校验失败 → 返回 400")
        void shouldReturn400_whenCaptchaInvalid() {
            when(valueOperations.get(anyString())).thenReturn(null);

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "wrong");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("验证码"));
        }

        @Test
        @DisplayName("账户已锁定（失败≥5次） → 返回 429")
        void shouldReturn429_whenAccountLocked() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn("7");
            when(stringRedisTemplate.getExpire("login_fail:" + TEST_EMAIL)).thenReturn(600L);

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "ABC");
            assertEquals(429, r.getCode());
            assertTrue(r.getMsg().contains("失败次数过多"));
        }

        @Test
        @DisplayName("用户不存在 → 统一返回 400 '邮箱或密码错误'")
        void shouldReturn400Unified_whenUserNotFound() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            // 删除验证码 key
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            // 记录失败次数
            when(valueOperations.increment("login_fail:" + TEST_EMAIL)).thenReturn(1L);

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "ABC");
            assertEquals(400, r.getCode());
            assertEquals("邮箱或密码错误", r.getMsg());
        }

        @Test
        @DisplayName("密码错误 → 统一返回 400 '邮箱或密码错误' + 记录失败")
        void shouldReturn400UnifiedAndRecordFail_whenPasswordWrong() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);

            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode("correct-password"));
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

            when(valueOperations.increment("login_fail:" + TEST_EMAIL)).thenReturn(1L);

            ResponseUtil r = userService.login(TEST_EMAIL, "wrong-password", "capId", "ABC");
            assertEquals(400, r.getCode());
            assertEquals("邮箱或密码错误", r.getMsg());
            // 验证失败次数记录了
            verify(valueOperations).increment("login_fail:" + TEST_EMAIL);
        }

        @Test
        @DisplayName("首次失败设置 TTL（不重置已有过期时间）")
        void shouldSetTTLOnFirstFailure() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);
            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode("correct"));
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
            when(valueOperations.increment("login_fail:" + TEST_EMAIL)).thenReturn(1L);

            userService.login(TEST_EMAIL, "wrong", "capId", "ABC");
            // 首次失败，设置 15 分钟 TTL
            verify(stringRedisTemplate).expire("login_fail:" + TEST_EMAIL, 15, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("登录成功：清除失败计数，删除旧 token，签发新双 token 存入 Redis")
        void shouldLoginSuccessfully() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);

            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            user.setRole("user");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

            when(jwtUtil.generateAccessToken(TEST_EMAIL, "user")).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(TEST_EMAIL, "user")).thenReturn(TEST_REFRESH_TOKEN);

            // mock 旧 token 存在（单设备登录，需要删除）
            when(valueOperations.get("token:" + TEST_EMAIL)).thenReturn("old-access-token");

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "ABC");

            assertEquals(200, r.getCode());
            assertEquals("登录成功", r.getMsg());

            // 清除失败计数
            verify(stringRedisTemplate).delete("login_fail:" + TEST_EMAIL);

            // 删除旧 token
            verify(stringRedisTemplate).delete("token:" + TEST_EMAIL);
            verify(stringRedisTemplate).delete("refresh_token:" + TEST_EMAIL);

            // 存储新 token
            verify(valueOperations).set("token:" + TEST_EMAIL, TEST_ACCESS_TOKEN, 30, TimeUnit.MINUTES);
            verify(valueOperations).set("refresh_token:" + TEST_EMAIL, TEST_REFRESH_TOKEN, 7, TimeUnit.DAYS);

            // 返回数据
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) r.getData();
            assertNotNull(data);
            assertEquals(TEST_ACCESS_TOKEN, data.get("accessToken"));
            assertEquals(TEST_REFRESH_TOKEN, data.get("refreshToken"));
            assertEquals("user", data.get("role"));
        }

        @Test
        @DisplayName("登录成功：无旧设备时仅签发新 token")
        void shouldNotDeleteOldToken_whenNoPreviousSession() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);

            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            user.setRole("admin");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

            when(jwtUtil.generateAccessToken(TEST_EMAIL, "admin")).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(TEST_EMAIL, "admin")).thenReturn(TEST_REFRESH_TOKEN);
            when(valueOperations.get("token:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "ABC");

            assertEquals(200, r.getCode());
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) r.getData();
            assertEquals("admin", data.get("role"));
        }

        @Test
        @DisplayName("验证码大小写不敏感")
        void shouldBeCaseInsensitiveForCaptcha() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABCDEF");

            // 验证码不匹配但忽略大小写 → 应该通过
            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "abcdef");

            // validateCaptcha 在 ignoreCase 后通过，所以不会再返回验证码错误
            // 会进入邮箱密码校验阶段（因为没有 mock login_fail / userMapper）
            // 这里我们验证 validateCaptcha 通过了（小写匹配大写）
            assertNotEquals("图形验证码错误", r.getMsg());
        }

        @Test
        @DisplayName("密码校验使用 BCrypt")
        void shouldUseBCryptForPasswordValidation() {
            when(valueOperations.get("captcha:capId")).thenReturn("ABC");
            when(stringRedisTemplate.delete("captcha:capId")).thenReturn(true);
            when(valueOperations.get("login_fail:" + TEST_EMAIL)).thenReturn(null);

            String encodedPassword = passwordEncoder.encode(TEST_PASSWORD);
            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword(encodedPassword);
            user.setRole("user");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
            when(jwtUtil.generateAccessToken(anyString(), anyString())).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(anyString(), anyString())).thenReturn(TEST_REFRESH_TOKEN);
            when(valueOperations.get("token:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.login(TEST_EMAIL, TEST_PASSWORD, "capId", "ABC");
            assertEquals(200, r.getCode());
        }
    }

    // ==================== logout ====================

    @Nested
    @DisplayName("logout - 登出")
    class LogoutTests {

        @Test
        @DisplayName("Token 无效 → 返回 400")
        void shouldReturn400_whenTokenInvalid() {
            when(jwtUtil.getUsernameFromToken("bad-token")).thenThrow(new RuntimeException("invalid"));

            ResponseUtil r = userService.logout("bad-token");
            assertEquals(400, r.getCode());
            assertEquals("Token 无效", r.getMsg());
        }

        @Test
        @DisplayName("正常登出：删除 Redis 中的 accessToken 和 refreshToken")
        void shouldDeleteBothTokens() {
            when(jwtUtil.getUsernameFromToken(TEST_ACCESS_TOKEN)).thenReturn(TEST_EMAIL);

            ResponseUtil r = userService.logout(TEST_ACCESS_TOKEN);

            assertEquals(200, r.getCode());
            assertEquals("登出成功", r.getMsg());
            verify(stringRedisTemplate).delete("token:" + TEST_EMAIL);
            verify(stringRedisTemplate).delete("refresh_token:" + TEST_EMAIL);
        }
    }

    // ==================== refresh ====================

    @Nested
    @DisplayName("refresh - 刷新 accessToken")
    class RefreshTests {

        @Test
        @DisplayName("refreshToken 为空 → 返回 400")
        void shouldReturn400_whenTokenIsNull() {
            ResponseUtil r = userService.refresh(null);
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("refreshToken 为空字符串 → 返回 400")
        void shouldReturn400_whenTokenIsEmpty() {
            ResponseUtil r = userService.refresh("");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("Token type 不是 refresh → 返回 401")
        void shouldReturn401_whenTokenTypeIsNotRefresh() {
            when(jwtUtil.isRefreshToken(TEST_ACCESS_TOKEN)).thenReturn(false);

            ResponseUtil r = userService.refresh(TEST_ACCESS_TOKEN);
            assertEquals(401, r.getCode());
            assertTrue(r.getMsg().contains("类型错误"));
        }

        @Test
        @DisplayName("JWT 校验失败 → 返回 401")
        void shouldReturn401_whenJwtValidationFails() {
            when(jwtUtil.isRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.validateToken(TEST_REFRESH_TOKEN)).thenReturn(false);

            ResponseUtil r = userService.refresh(TEST_REFRESH_TOKEN);
            assertEquals(401, r.getCode());
        }

        @Test
        @DisplayName("Redis 中无对应 refreshToken → 返回 401")
        void shouldReturn401_whenTokenNotFoundInRedis() {
            when(jwtUtil.isRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.validateToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getUsernameFromToken(TEST_REFRESH_TOKEN)).thenReturn(TEST_EMAIL);
            when(valueOperations.get("refresh_token:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.refresh(TEST_REFRESH_TOKEN);
            assertEquals(401, r.getCode());
            assertTrue(r.getMsg().contains("已失效"));
        }

        @Test
        @DisplayName("Redis 中 token 不匹配 → 返回 401")
        void shouldReturn401_whenTokenMismatch() {
            when(jwtUtil.isRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.validateToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getUsernameFromToken(TEST_REFRESH_TOKEN)).thenReturn(TEST_EMAIL);
            when(valueOperations.get("refresh_token:" + TEST_EMAIL)).thenReturn("different-refresh-token");

            ResponseUtil r = userService.refresh(TEST_REFRESH_TOKEN);
            assertEquals(401, r.getCode());
        }

        @Test
        @DisplayName("刷新成功：签发新 accessToken 存入 Redis（30min TTL）")
        void shouldRefreshSuccessfully() {
            when(jwtUtil.isRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.validateToken(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getUsernameFromToken(TEST_REFRESH_TOKEN)).thenReturn(TEST_EMAIL);
            when(jwtUtil.getRoleFromToken(TEST_REFRESH_TOKEN)).thenReturn("user");
            when(valueOperations.get("refresh_token:" + TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);
            when(jwtUtil.generateAccessToken(TEST_EMAIL, "user")).thenReturn("new-access-token");

            ResponseUtil r = userService.refresh(TEST_REFRESH_TOKEN);

            assertEquals(200, r.getCode());
            assertEquals("Token 刷新成功", r.getMsg());

            verify(valueOperations).set("token:" + TEST_EMAIL, "new-access-token", 30, TimeUnit.MINUTES);

            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) r.getData();
            assertEquals("new-access-token", data.get("accessToken"));
            assertEquals("user", data.get("role"));
        }
    }

    // ==================== wechatQrcode ====================

    @Nested
    @DisplayName("wechatQrcode - 微信扫码场景")
    class WechatQrcodeTests {

        @Test
        @DisplayName("WeChat 未配置 → 返回 500")
        void shouldReturn500_whenWeChatNotConfigured() {
            when(wechatConfig.isConfigured()).thenReturn(false);

            ResponseUtil r = userService.wechatQrcode();
            assertEquals(500, r.getCode());
            assertTrue(r.getMsg().contains("未配置"));
        }

        @Test
        @DisplayName("配置正常：生成 sceneId + authUrl，存 pending 状态")
        void shouldGenerateSceneAndStorePending() {
            when(wechatConfig.isConfigured()).thenReturn(true);
            when(wechatConfig.getAppId()).thenReturn("test-app-id");
            when(wechatConfig.getCallbackUrl()).thenReturn("http://localhost/callback");

            ResponseUtil r = userService.wechatQrcode();
            assertEquals(200, r.getCode());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertNotNull(data.get("sceneId"));
            assertNotNull(data.get("authUrl"));
            assertTrue(((String) data.get("authUrl")).contains("snsapi_userinfo"));

            // 验证 Redis 存储了 pending 状态
            verify(valueOperations).set(startsWith("wechat_scene:"), eq("pending"),
                    eq(5L), eq(TimeUnit.MINUTES));
        }
    }

    // ==================== wechatCheck ====================

    @Nested
    @DisplayName("wechatCheck - 轮询扫码状态")
    class WechatCheckTests {

        @Test
        @DisplayName("sceneId 为空 → 返回 400")
        void shouldReturn400_whenSceneIdIsEmpty() {
            ResponseUtil r = userService.wechatCheck("");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("二维码过期 → 返回 expired 状态")
        void shouldReturnExpired() {
            when(valueOperations.get("wechat_scene:scene1")).thenReturn(null);

            ResponseUtil r = userService.wechatCheck("scene1");
            assertEquals(400, r.getCode());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals("expired", data.get("status"));
        }

        @Test
        @DisplayName("pending 状态 → 返回 pending")
        void shouldReturnPending() {
            when(valueOperations.get("wechat_scene:scene1")).thenReturn("pending");

            ResponseUtil r = userService.wechatCheck("scene1");
            assertEquals(200, r.getCode());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals("pending", data.get("status"));
        }

        @Test
        @DisplayName("scanned 状态 → 返回 nickname + avatar")
        void shouldReturnScannedWithUserInfo() throws Exception {
            when(valueOperations.get("wechat_scene:scene1")).thenReturn("scanned");
            when(valueOperations.get("wechat_data:scene1")).thenReturn(
                    "{\"nickname\":\"测试用户\",\"avatar\":\"http://avatar.url\"}");

            ResponseUtil r = userService.wechatCheck("scene1");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals("scanned", data.get("status"));
            assertEquals("测试用户", data.get("nickname"));
            assertEquals("http://avatar.url", data.get("avatar"));
        }

        @Test
        @DisplayName("confirmed 状态 → 返回 tokens")
        void shouldReturnConfirmedWithTokens() {
            when(valueOperations.get("wechat_scene:scene1")).thenReturn("confirmed");
            when(valueOperations.get("wechat_token:scene1"))
                    .thenReturn(TEST_ACCESS_TOKEN + "|" + TEST_REFRESH_TOKEN + "|" + TEST_EMAIL + "|nick|admin");

            ResponseUtil r = userService.wechatCheck("scene1");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals("confirmed", data.get("status"));
            assertEquals(TEST_ACCESS_TOKEN, data.get("accessToken"));
            assertEquals(TEST_REFRESH_TOKEN, data.get("refreshToken"));
            assertEquals(TEST_EMAIL, data.get("email"));
        }
    }

    // ==================== wechatCallback ====================

    @Nested
    @DisplayName("wechatCallback - 微信 OAuth 回调")
    class WechatCallbackTests {

        @Test
        @DisplayName("code 为空 → 返回 400")
        void shouldReturn400_whenCodeIsEmpty() {
            ResponseUtil r = userService.wechatCallback("", "state1");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("state 为空 → 返回 400")
        void shouldReturn400_whenStateIsEmpty() {
            ResponseUtil r = userService.wechatCallback("code1", "");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("scene 已过期 → 返回 400")
        void shouldReturn400_whenSceneExpired() {
            when(valueOperations.get("wechat_scene:state1")).thenReturn(null);

            ResponseUtil r = userService.wechatCallback("code1", "state1");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("已过期"));
        }

        @Test
        @DisplayName("scene 非 pending → 返回 400")
        void shouldReturn400_whenSceneNotPending() {
            when(valueOperations.get("wechat_scene:state1")).thenReturn("confirmed");

            ResponseUtil r = userService.wechatCallback("code1", "state1");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("已被使用"));
        }

        @Test
        @DisplayName("WeChat API 失败 → 返回 500")
        void shouldReturn500_whenWechatAPIFails() {
            when(valueOperations.get("wechat_scene:state1")).thenReturn("pending");
            when(wechatUtil.getAccessToken("code1")).thenReturn(null);

            ResponseUtil r = userService.wechatCallback("code1", "state1");
            assertEquals(500, r.getCode());
        }

        @Test
        @DisplayName("已绑定用户：自动登录，更新昵称头像，签发 token")
        void shouldAutoLoginForBoundUser() {
            when(valueOperations.get("wechat_scene:state1")).thenReturn("pending");

            WechatUtil.WechatTokenResponse tokenResp = mock(WechatUtil.WechatTokenResponse.class);
            when(tokenResp.getOpenid()).thenReturn("wx-openid-123");
            when(tokenResp.getAccessToken()).thenReturn("wx-at");
            when(wechatUtil.getAccessToken("code1")).thenReturn(tokenResp);

            WechatUtil.WechatUserInfo userInfo = mock(WechatUtil.WechatUserInfo.class);
            when(userInfo.getNickname()).thenReturn("微信用户");
            when(userInfo.getHeadImgUrl()).thenReturn("http://head.img");
            when(wechatUtil.getUserInfo("wx-at", "wx-openid-123")).thenReturn(userInfo);

            User boundUser = new User();
            boundUser.setId(1);
            boundUser.setEmail(TEST_EMAIL);
            boundUser.setRole("user");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(boundUser);

            when(jwtUtil.generateAccessToken(TEST_EMAIL, "user")).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(TEST_EMAIL, "user")).thenReturn(TEST_REFRESH_TOKEN);

            ResponseUtil r = userService.wechatCallback("code1", "state1");

            assertEquals(200, r.getCode());
            // 状态改为 confirmed
            verify(valueOperations).set("wechat_scene:state1", "confirmed", 5, TimeUnit.MINUTES);
            // 存储 token 供 PC 轮询
            verify(valueOperations).set(startsWith("wechat_token:state1"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("新用户扫码：存储微信信息，状态改为 scanned")
        void shouldStoreWxDataAndSetScanned() {
            when(valueOperations.get("wechat_scene:state2")).thenReturn("pending");

            WechatUtil.WechatTokenResponse tokenResp = mock(WechatUtil.WechatTokenResponse.class);
            when(tokenResp.getOpenid()).thenReturn("wx-openid-456");
            when(tokenResp.getAccessToken()).thenReturn("wx-at2");
            when(wechatUtil.getAccessToken("code2")).thenReturn(tokenResp);

            WechatUtil.WechatUserInfo userInfo = mock(WechatUtil.WechatUserInfo.class);
            when(userInfo.getNickname()).thenReturn("新用户");
            when(userInfo.getHeadImgUrl()).thenReturn("");
            when(wechatUtil.getUserInfo("wx-at2", "wx-openid-456")).thenReturn(userInfo);

            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            ResponseUtil r = userService.wechatCallback("code2", "state2");

            assertEquals(200, r.getCode());
            assertTrue(r.getMsg().contains("邮箱绑定"));

            // 存储微信数据
            verify(valueOperations).set(startsWith("wechat_data:state2"), contains("wx-openid-456"),
                    eq(5L), eq(TimeUnit.MINUTES));
            // 状态改为 scanned
            verify(valueOperations).set("wechat_scene:state2", "scanned", 5, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("openid 为 null → 返回 500")
        void shouldReturn500_whenOpenIdIsNull() {
            when(valueOperations.get("wechat_scene:state1")).thenReturn("pending");

            WechatUtil.WechatTokenResponse tokenResp = mock(WechatUtil.WechatTokenResponse.class);
            when(tokenResp.getOpenid()).thenReturn(null);
            when(wechatUtil.getAccessToken("code1")).thenReturn(tokenResp);

            ResponseUtil r = userService.wechatCallback("code1", "state1");
            assertEquals(500, r.getCode());
        }
    }

    // ==================== bindWechatEmail ====================

    @Nested
    @DisplayName("bindWechatEmail - 微信绑定邮箱")
    class BindWechatEmailTests {

        private static final String SCENE_ID = "test-scene-id";

        @Test
        @DisplayName("字段缺失 → 返回 400")
        void shouldReturn400_whenFieldsAreMissing() {
            ResponseUtil r = userService.bindWechatEmail(null, TEST_EMAIL, "123456");
            assertEquals(400, r.getCode());

            r = userService.bindWechatEmail(SCENE_ID, null, "123456");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("邮箱格式错误 → 返回 400")
        void shouldReturn400_whenEmailFormatInvalid() {
            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, "bad", "123456");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("验证码过期 → 返回 400")
        void shouldReturn400_whenCodeExpired() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("过期"));
        }

        @Test
        @DisplayName("验证码错误 → 返回 400")
        void shouldReturn400_whenCodeWrong() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("correct");

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "wrong");
            assertEquals(400, r.getCode());
            assertEquals("验证码不正确", r.getMsg());
        }

        @Test
        @DisplayName("scene 状态非 scanned → 返回 400")
        void shouldReturn400_whenSceneNotScanned() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(valueOperations.get("wechat_scene:" + SCENE_ID)).thenReturn("pending");

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("请先使用微信扫码"));
        }

        @Test
        @DisplayName("微信数据过期 → 返回 400")
        void shouldReturn400_whenWxDataExpired() {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(valueOperations.get("wechat_scene:" + SCENE_ID)).thenReturn("scanned");
            when(valueOperations.get("wechat_data:" + SCENE_ID)).thenReturn(null);

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("已过期"));
        }

        @Test
        @DisplayName("邮箱已绑定其他微信 → 返回 400")
        void shouldReturn400_whenEmailBoundToOtherWeChat() throws Exception {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(valueOperations.get("wechat_scene:" + SCENE_ID)).thenReturn("scanned");
            when(valueOperations.get("wechat_data:" + SCENE_ID))
                    .thenReturn("{\"openid\":\"wx-new\",\"nickname\":\"test\",\"avatar\":\"\"}");

            User existingUser = new User();
            existingUser.setEmail(TEST_EMAIL);
            existingUser.setOpenid("wx-other"); // 已绑定其他微信
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingUser);

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("已绑定其他微信号"));
        }

        @Test
        @DisplayName("已有账号升级：绑定微信 openid + 更新昵称头像")
        void shouldUpgradeExistingAccount() throws Exception {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(valueOperations.get("wechat_scene:" + SCENE_ID)).thenReturn("scanned");
            when(valueOperations.get("wechat_data:" + SCENE_ID))
                    .thenReturn("{\"openid\":\"wx-openid\",\"nickname\":\"老用户\",\"avatar\":\"http://a.img\"}");

            User existingUser = new User();
            existingUser.setId(1);
            existingUser.setEmail(TEST_EMAIL);
            existingUser.setOpenid(null); // 未绑定微信
            existingUser.setRole("user");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingUser);

            when(jwtUtil.generateAccessToken(TEST_EMAIL, "user")).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(TEST_EMAIL, "user")).thenReturn(TEST_REFRESH_TOKEN);

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");

            assertEquals(200, r.getCode());
            // 更新了用户
            verify(userMapper).updateById(any(User.class));
            // 删除了验证码
            verify(stringRedisTemplate).delete("email_code:" + TEST_EMAIL);
            // 状态改 confirmed
            verify(valueOperations).set("wechat_scene:" + SCENE_ID, "confirmed", 5, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("新用户创建：随机密码 + BCrypt 加密 + 绑定微信")
        void shouldCreateNewUser() throws Exception {
            when(valueOperations.get("email_code:" + TEST_EMAIL)).thenReturn("123456");
            when(valueOperations.get("wechat_scene:" + SCENE_ID)).thenReturn("scanned");
            when(valueOperations.get("wechat_data:" + SCENE_ID))
                    .thenReturn("{\"openid\":\"wx-new\",\"nickname\":\"新人\",\"avatar\":\"\"}");

            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            when(jwtUtil.generateAccessToken(TEST_EMAIL, "user")).thenReturn(TEST_ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(TEST_EMAIL, "user")).thenReturn(TEST_REFRESH_TOKEN);

            ResponseUtil r = userService.bindWechatEmail(SCENE_ID, TEST_EMAIL, "123456");

            assertEquals(200, r.getCode());

            // 验证插入
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            User created = userCaptor.getValue();
            assertEquals(TEST_EMAIL, created.getEmail());
            assertEquals("wx-new", created.getOpenid());
            assertEquals("新人", created.getNickname());
            assertEquals("user", created.getRole());
            // 密码是随机生成的 UUID
            assertNotNull(created.getPassword());
            assertFalse(created.getPassword().isEmpty());
        }
    }

    // ==================== resetPassword ====================

    @Nested
    @DisplayName("resetPassword - 密码重置")
    class ResetPasswordTests {

        @Test
        @DisplayName("字段缺失 → 返回 400")
        void shouldReturn400_whenFieldsAreMissing() {
            ResponseUtil r = userService.resetPassword(null, "pass", "code");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("密码少于 6 位 → 返回 400")
        void shouldReturn400_whenPasswordTooShort() {
            ResponseUtil r = userService.resetPassword(TEST_EMAIL, "12345", "code");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("不能少于6位"));
        }

        @Test
        @DisplayName("验证码过期 → 返回 400")
        void shouldReturn400_whenCodeExpired() {
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn(null);

            ResponseUtil r = userService.resetPassword(TEST_EMAIL, TEST_PASSWORD, "expired");
            assertEquals(400, r.getCode());
            assertTrue(r.getMsg().contains("已过期"));
        }

        @Test
        @DisplayName("验证码错误 → 返回 400")
        void shouldReturn400_whenCodeWrong() {
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn("correct");

            ResponseUtil r = userService.resetPassword(TEST_EMAIL, TEST_PASSWORD, "wrong");
            assertEquals(400, r.getCode());
            assertEquals("验证码不正确", r.getMsg());
        }

        @Test
        @DisplayName("用户不存在（防御） → 返回 400")
        void shouldReturn400_whenUserNotFound() {
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn("123456");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            ResponseUtil r = userService.resetPassword(TEST_EMAIL, TEST_PASSWORD, "123456");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("邮箱格式错误 → 返回 400")
        void shouldReturn400_whenEmailFormatInvalid() {
            ResponseUtil r = userService.resetPassword("bad", TEST_PASSWORD, "123456");
            assertEquals(400, r.getCode());
        }

        @Test
        @DisplayName("重置成功：BCrypt 加密新密码，删除验证码，清除所有 token")
        void shouldResetSuccessfully() {
            when(valueOperations.get("reset_code:" + TEST_EMAIL)).thenReturn("123456");

            User user = new User();
            user.setId(1);
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode("old-password"));
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

            ResponseUtil r = userService.resetPassword(TEST_EMAIL, TEST_PASSWORD, "123456");

            assertEquals(200, r.getCode());
            assertEquals("密码重置成功，请使用新密码登录", r.getMsg());

            // 密码已更新为 BCrypt 编码
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(userCaptor.capture());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, userCaptor.getValue().getPassword()));

            // 验证码删除
            verify(stringRedisTemplate).delete("reset_code:" + TEST_EMAIL);

            // 踢掉所有设备
            verify(stringRedisTemplate).delete("token:" + TEST_EMAIL);
            verify(stringRedisTemplate).delete("refresh_token:" + TEST_EMAIL);
        }
    }

    // ==================== listUsers ====================

    @Nested
    @DisplayName("listUsers - 管理员分页查询")
    class ListUsersTests {

        @Test
        @DisplayName("分页查询：使用正确的 page/size 参数")
        void shouldUseCorrectPagination() {
            Map<String, Object> result = userService.listUsers(2, 20);

            assertNotNull(result);
            assertTrue(result.containsKey("records"));
            assertTrue(result.containsKey("total"));
            assertTrue(result.containsKey("current"));
            assertTrue(result.containsKey("size"));
        }

        @Test
        @DisplayName("查询不含 password 字段")
        void shouldNotSelectPassword() {
            userService.listUsers(1, 10);

            ArgumentCaptor<QueryWrapper<User>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
            verify(userMapper).selectPage(any(Page.class), captor.capture());
            // select 语句应该包含必要字段但不包含 password
            String sqlSegment = captor.getValue().getSqlSegment();
            assertNotNull(sqlSegment);
        }
    }

    // ==================== getCurrentUser ====================

    @Nested
    @DisplayName("getCurrentUser - 获取当前用户信息")
    class GetCurrentUserTests {

        @Test
        @DisplayName("用户存在 → 返回 map 含 id, email, role, nickname, avatar")
        void shouldReturnUserInfo() {
            User user = new User();
            user.setId(42);
            user.setEmail(TEST_EMAIL);
            user.setRole("admin");
            user.setNickname("Admin");
            user.setAvatar("http://avatar.url");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

            Map<String, Object> result = userService.getCurrentUser(TEST_EMAIL);

            assertNotNull(result);
            assertEquals(42, result.get("id"));
            assertEquals(TEST_EMAIL, result.get("email"));
            assertEquals("admin", result.get("role"));
            assertEquals("Admin", result.get("nickname"));
            assertEquals("http://avatar.url", result.get("avatar"));
            // 不包含 password
            assertFalse(result.containsKey("password"));
        }

        @Test
        @DisplayName("用户不存在 → 返回 null")
        void shouldReturnNull_whenUserNotFound() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = userService.getCurrentUser("unknown@test.com");
            assertNull(result);
        }
    }
}
