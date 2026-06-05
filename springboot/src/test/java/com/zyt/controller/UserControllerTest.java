package com.zyt.controller;

import com.zyt.exception.BadRequestException;
import com.zyt.exception.GlobalExceptionHandler;
import com.zyt.service.UserService;
import com.zyt.utils.ResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 控制器测试")
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 基础认证 ====================

    @Nested
    @DisplayName("基础认证端点")
    class BasicAuthTests {

        @Test
        @DisplayName("GET /user/captcha：返回 200 + captchaId + captchaImage")
        void shouldReturnCaptcha() throws Exception {
            Map<String, String> captchaData = new HashMap<>();
            captchaData.put("captchaId", "test-captcha-id");
            captchaData.put("captchaImage", "data:image/png;base64,ABC123");
            when(userService.generateCaptcha()).thenReturn(captchaData);

            mockMvc.perform(get("/user/captcha"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("验证码生成成功"))
                    .andExpect(jsonPath("$.data.captchaId").value("test-captcha-id"))
                    .andExpect(jsonPath("$.data.captchaImage").exists());
        }

        @Test
        @DisplayName("POST /user/send-code：正常发送 → 200")
        void shouldSendCode() throws Exception {
            when(userService.sendCode(TEST_EMAIL)).thenReturn(ResponseUtil.success("验证码已发送"));

            mockMvc.perform(post("/user/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("POST /user/send-code：异常 → service 抛 BadRequestException")
        void shouldReturn400_whenEmailIsEmpty() throws Exception {
            when(userService.sendCode(any())).thenThrow(new BadRequestException("邮箱不能为空"));

            mockMvc.perform(post("/user/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/register：正常注册 → 200")
        void shouldRegister() throws Exception {
            when(userService.register(eq(TEST_EMAIL), eq("password123"), eq("123456")))
                    .thenReturn(new ResponseUtil(200, "注册成功", null));

            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"password123\",\"code\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("注册成功"));
        }

        @Test
        @DisplayName("POST /user/register：参数缺失 → service 抛 BadRequestException")
        void shouldReturn400_whenRegisterFieldsMissing() throws Exception {
            when(userService.register(any(), any(), any()))
                    .thenThrow(new BadRequestException("邮箱、密码和验证码不能为空"));

            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/login：正常登录 → 200 + tokens + role")
        void shouldLogin() throws Exception {
            Map<String, String> tokenData = new HashMap<>();
            tokenData.put("accessToken", TEST_ACCESS_TOKEN);
            tokenData.put("refreshToken", TEST_REFRESH_TOKEN);
            tokenData.put("role", "user");
            when(userService.login(eq(TEST_EMAIL), eq("password123"), eq("capId"), eq("ABC")))
                    .thenReturn(new ResponseUtil(200, "登录成功", tokenData));

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"password123\",\"captchaId\":\"capId\",\"captchaCode\":\"ABC\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN))
                    .andExpect(jsonPath("$.data.refreshToken").value(TEST_REFRESH_TOKEN))
                    .andExpect(jsonPath("$.data.role").value("user"));
        }

        @Test
        @DisplayName("POST /user/login：账户锁定 → 429")
        void shouldReturn429_whenAccountLocked() throws Exception {
            when(userService.login(any(), any(), any(), any()))
                    .thenReturn(new ResponseUtil(429, "登录失败次数过多，请15分钟后再试", null));

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"wrong\",\"captchaId\":\"c\",\"captchaCode\":\"a\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(429));
        }

        @Test
        @DisplayName("POST /user/login：邮箱密码为空 → 400")
        void shouldReturn400_whenEmailOrPasswordEmpty() throws Exception {
            when(userService.login(any(), any(), any(), any()))
                    .thenReturn(new ResponseUtil(400, "邮箱和密码不能为空", null));

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"captchaId\":\"c\",\"captchaCode\":\"a\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/refresh：正常刷新 → 200 + 新 accessToken")
        void shouldRefreshToken() throws Exception {
            Map<String, String> tokenData = new HashMap<>();
            tokenData.put("accessToken", "new-access-token");
            tokenData.put("role", "user");
            when(userService.refresh(TEST_REFRESH_TOKEN))
                    .thenReturn(new ResponseUtil(200, "Token 刷新成功", tokenData));

            mockMvc.perform(post("/user/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + TEST_REFRESH_TOKEN + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
        }

        @Test
        @DisplayName("POST /user/refresh：refreshToken 为空 → 400")
        void shouldReturn400_whenRefreshTokenEmpty() throws Exception {
            when(userService.refresh(null))
                    .thenReturn(new ResponseUtil(400, "refreshToken 不能为空", null));

            mockMvc.perform(post("/user/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/refresh：token 类型错误 → 401")
        void shouldReturn401_whenTokenTypeWrong() throws Exception {
            when(userService.refresh(TEST_ACCESS_TOKEN))
                    .thenReturn(new ResponseUtil(401, "Token 类型错误", null));

            mockMvc.perform(post("/user/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + TEST_ACCESS_TOKEN + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    // ==================== 受保护端点 ====================

    @Nested
    @DisplayName("受保护端点（需登录）")
    class ProtectedEndpointTests {

        @Test
        @DisplayName("POST /user/logout：正常登出 → 200")
        void shouldLogout() throws Exception {
            when(userService.logout(TEST_ACCESS_TOKEN))
                    .thenReturn(new ResponseUtil(200, "登出成功", null));

            mockMvc.perform(post("/user/logout")
                            .header("Authorization", TEST_ACCESS_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("POST /user/logout：token 无效 → 400")
        void shouldReturn400_whenLogoutTokenInvalid() throws Exception {
            when(userService.logout("bad-token"))
                    .thenReturn(new ResponseUtil(400, "Token 无效", null));

            mockMvc.perform(post("/user/logout")
                            .header("Authorization", "bad-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("GET /user/info：鉴权通过 → 200")
        void shouldReturnAuthSuccess() throws Exception {
            mockMvc.perform(get("/user/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("鉴权通过，可正常访问"));
        }

        @Test
        @DisplayName("GET /user/me：未登录 → 401")
        void shouldReturn401_whenNotLoggedIn() throws Exception {
            mockMvc.perform(get("/user/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.msg").value("未登录"));
        }

        @Test
        @DisplayName("GET /user/me：用户不存在 → 404")
        void shouldReturn404_whenUserNotFound() throws Exception {
            when(userService.getCurrentUser(TEST_EMAIL)).thenReturn(null);

            mockMvc.perform(get("/user/me")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("用户不存在"));
        }

        @Test
        @DisplayName("GET /user/me：返回用户信息")
        void shouldReturnCurrentUser() throws Exception {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", 1);
            userInfo.put("email", TEST_EMAIL);
            userInfo.put("role", "user");
            userInfo.put("nickname", "Test");
            userInfo.put("avatar", "");
            when(userService.getCurrentUser(TEST_EMAIL)).thenReturn(userInfo);

            mockMvc.perform(get("/user/me")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                    .andExpect(jsonPath("$.data.role").value("user"));
        }
    }

    // ==================== 微信扫码登录 ====================

    @Nested
    @DisplayName("微信扫码登录端点")
    class WechatTests {

        @Test
        @DisplayName("POST /user/wechat/qrcode：正常 → 200 + sceneId + authUrl")
        void shouldGenerateQrcode() throws Exception {
            when(userService.wechatQrcode())
                    .thenReturn(new ResponseUtil(200, "微信扫码场景生成成功",
                            Map.of("sceneId", "abc123", "authUrl", "https://open.weixin.qq.com/...")));

            mockMvc.perform(post("/user/wechat/qrcode"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.sceneId").value("abc123"))
                    .andExpect(jsonPath("$.data.authUrl").isString());
        }

        @Test
        @DisplayName("POST /user/wechat/qrcode：未配置 → 500")
        void shouldReturn500_whenWeChatNotConfigured() throws Exception {
            when(userService.wechatQrcode())
                    .thenReturn(new ResponseUtil(500, "微信登录未配置", null));

            mockMvc.perform(post("/user/wechat/qrcode"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));
        }

        @Test
        @DisplayName("GET /user/wechat/check：pending 状态")
        void shouldReturnPending() throws Exception {
            when(userService.wechatCheck("scene1"))
                    .thenReturn(new ResponseUtil(200, "查询成功", Map.of("status", "pending")));

            mockMvc.perform(get("/user/wechat/check").param("sceneId", "scene1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("pending"));
        }

        @Test
        @DisplayName("GET /user/wechat/check：scanned 状态 + 用户信息")
        void shouldReturnScannedWithUserInfo() throws Exception {
            Map<String, Object> data = new HashMap<>();
            data.put("status", "scanned");
            data.put("nickname", "微信用户");
            data.put("avatar", "http://head.img");
            when(userService.wechatCheck("scene1"))
                    .thenReturn(new ResponseUtil(200, "查询成功", data));

            mockMvc.perform(get("/user/wechat/check").param("sceneId", "scene1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("scanned"))
                    .andExpect(jsonPath("$.data.nickname").value("微信用户"));
        }

        @Test
        @DisplayName("GET /user/wechat/check：expired 状态")
        void shouldReturnExpired() throws Exception {
            when(userService.wechatCheck("scene1"))
                    .thenReturn(new ResponseUtil(400, "二维码已过期", Map.of("status", "expired")));

            mockMvc.perform(get("/user/wechat/check").param("sceneId", "scene1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.data.status").value("expired"));
        }

        @Test
        @DisplayName("GET /user/wechat/callback：授权成功 → HTML 页面")
        void shouldReturnSuccessHtml() throws Exception {
            when(userService.wechatCallback("code1", "state1"))
                    .thenReturn(new ResponseUtil(200, "微信授权成功", null));

            mockMvc.perform(get("/user/wechat/callback")
                            .param("code", "code1")
                            .param("state", "state1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("授权成功")));
        }

        @Test
        @DisplayName("GET /user/wechat/callback：授权失败 → HTML 错误页面")
        void shouldReturnErrorHtml() throws Exception {
            when(userService.wechatCallback("bad-code", "bad-state"))
                    .thenReturn(new ResponseUtil(400, "二维码已过期", null));

            mockMvc.perform(get("/user/wechat/callback")
                            .param("code", "bad-code")
                            .param("state", "bad-state"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("授权失败")));
        }

        @Test
        @DisplayName("POST /user/wechat/bind-email：绑定成功 → 200 + tokens")
        void shouldBindWechatEmail() throws Exception {
            Map<String, String> bindResult = new HashMap<>();
            bindResult.put("accessToken", TEST_ACCESS_TOKEN);
            bindResult.put("refreshToken", TEST_REFRESH_TOKEN);
            bindResult.put("role", "user");
            when(userService.bindWechatEmail("scene1", TEST_EMAIL, "123456"))
                    .thenReturn(new ResponseUtil(200, "绑定成功，登录中...", bindResult));

            mockMvc.perform(post("/user/wechat/bind-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sceneId\":\"scene1\",\"email\":\"" + TEST_EMAIL + "\",\"code\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN));
        }

        @Test
        @DisplayName("POST /user/wechat/bind-email：字段缺失 → 400")
        void shouldReturn400_whenBindFieldsMissing() throws Exception {
            when(userService.bindWechatEmail(null, TEST_EMAIL, "123456"))
                    .thenReturn(new ResponseUtil(400, "sceneId、邮箱和验证码均不能为空", null));

            mockMvc.perform(post("/user/wechat/bind-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/send-bind-code：正常发送 → 200")
        void shouldSendBindCode() throws Exception {
            when(userService.sendBindCode(TEST_EMAIL))
                    .thenReturn(new ResponseUtil(200, "验证码已发送", null));

            mockMvc.perform(post("/user/send-bind-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 邮箱找回密码 ====================

    @Nested
    @DisplayName("邮箱找回密码端点")
    class ForgotPasswordTests {

        @Test
        @DisplayName("POST /user/forgot-password/send-code：正常发送 → 200")
        void shouldSendResetCode() throws Exception {
            when(userService.sendResetCode(TEST_EMAIL))
                    .thenReturn(new ResponseUtil(200, "验证码已发送", null));

            mockMvc.perform(post("/user/forgot-password/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("POST /user/forgot-password/send-code：邮箱为空 → 400")
        void shouldReturn400_whenEmailEmpty() throws Exception {
            when(userService.sendResetCode(null))
                    .thenReturn(new ResponseUtil(400, "邮箱不能为空", null));

            mockMvc.perform(post("/user/forgot-password/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/forgot-password/reset：正常重置 → 200")
        void shouldResetPassword() throws Exception {
            when(userService.resetPassword(TEST_EMAIL, "newpass123", "123456"))
                    .thenReturn(new ResponseUtil(200, "密码重置成功", null));

            mockMvc.perform(post("/user/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"newpass123\",\"code\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("密码重置成功"));
        }

        @Test
        @DisplayName("POST /user/forgot-password/reset：字段缺失 → 400")
        void shouldReturn400_whenResetFieldsMissing() throws Exception {
            when(userService.resetPassword(any(), any(), any()))
                    .thenReturn(new ResponseUtil(400, "邮箱、密码和验证码不能为空", null));

            mockMvc.perform(post("/user/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("POST /user/forgot-password/reset：验证码错误 → 400")
        void shouldReturn400_whenResetCodeWrong() throws Exception {
            when(userService.resetPassword(TEST_EMAIL, "newpass123", "wrong"))
                    .thenReturn(new ResponseUtil(400, "验证码不正确", null));

            mockMvc.perform(post("/user/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"newpass123\",\"code\":\"wrong\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("验证码不正确"));
        }
    }

    // ==================== RBAC 管理员端点 ====================

    @Nested
    @DisplayName("RBAC 管理员端点")
    class AdminEndpointTests {

        @Test
        @DisplayName("GET /user/admin/users：默认分页（page=1, size=10） → 200")
        void shouldListUsersWithDefaultPagination() throws Exception {
            Map<String, Object> pageResult = new HashMap<>();
            pageResult.put("records", java.util.List.of());
            pageResult.put("total", 0L);
            pageResult.put("current", 1L);
            pageResult.put("size", 10L);
            when(userService.listUsers(1, 10)).thenReturn(pageResult);

            mockMvc.perform(get("/user/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.current").value(1))
                    .andExpect(jsonPath("$.data.size").value(10));
        }

        @Test
        @DisplayName("GET /user/admin/users：自定义分页（page=2, size=5） → 200")
        void shouldListUsersWithCustomPagination() throws Exception {
            Map<String, Object> pageResult = new HashMap<>();
            pageResult.put("records", java.util.List.of());
            pageResult.put("total", 25L);
            pageResult.put("current", 2L);
            pageResult.put("size", 5L);
            when(userService.listUsers(2, 5)).thenReturn(pageResult);

            mockMvc.perform(get("/user/admin/users")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(5));
        }

        @Test
        @DisplayName("GET /user/admin/users：验证分页参数正确传递")
        void shouldPassCorrectPaginationParams() throws Exception {
            when(userService.listUsers(3, 15)).thenReturn(Map.of());

            mockMvc.perform(get("/user/admin/users")
                            .param("page", "3")
                            .param("size", "15"))
                    .andExpect(status().isOk());

            verify(userService).listUsers(3, 15);
        }
    }
}
