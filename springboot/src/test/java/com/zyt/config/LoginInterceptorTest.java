package com.zyt.config;

import com.zyt.utils.JwtUtil;
import com.zyt.utils.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginInterceptor 登录拦截器测试")
class LoginInterceptorTest {

    private LoginInterceptor interceptor;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter stringWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(interceptor, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        lenient().when(response.getWriter()).thenReturn(printWriter);
    }

    // ==================== 第一层：Token 缺失 ====================

    @Test
    @DisplayName("无 Authorization header → 返回 false，写 401 错误")
    void shouldReject_whenNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        printWriter.flush();
        String json = stringWriter.toString();
        assertTrue(json.contains("未携带 Token"));
        assertTrue(json.contains("401"));
    }

    @Test
    @DisplayName("Authorization header 为空字符串 → 返回 false")
    void shouldReject_whenEmptyAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("");

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("未携带 Token"));
    }

    // ==================== 第二层：JWT 校验失败 ====================

    @Test
    @DisplayName("JWT 校验失败 → 返回 false，写 'Token 无效或已过期'")
    void shouldReject_whenJwtValidationFails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("invalid-jwt-token");
        when(jwtUtil.validateToken("invalid-jwt-token")).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Token 无效或已过期"));
    }

    // ==================== 第三层：Redis 比对失败 ====================

    @Test
    @DisplayName("Redis 中无 token（登出/被踢） → 返回 false")
    void shouldReject_whenTokenNotFoundInRedis() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("valid-jwt");
        when(jwtUtil.validateToken("valid-jwt")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("valid-jwt")).thenReturn("user@test.com");
        when(valueOperations.get("token:user@test.com")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Token 已失效"));
    }

    @Test
    @DisplayName("Redis 中 token 不匹配（其他设备登录） → 返回 false")
    void shouldReject_whenTokenMismatch() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("valid-jwt");
        when(jwtUtil.validateToken("valid-jwt")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("valid-jwt")).thenReturn("user@test.com");
        when(valueOperations.get("token:user@test.com")).thenReturn("different-token-in-redis");

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Token 已失效"));
    }

    // ==================== 全部通过 ====================

    @Test
    @DisplayName("三层校验全通过 → 返回 true，设置 request attributes")
    void shouldPass_whenAllThreeLayersValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("valid-jwt");
        when(jwtUtil.validateToken("valid-jwt")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("valid-jwt")).thenReturn("user@test.com");
        when(valueOperations.get("token:user@test.com")).thenReturn("valid-jwt");
        when(jwtUtil.getRoleFromToken("valid-jwt")).thenReturn("admin");

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        verify(request).setAttribute("userEmail", "user@test.com");
        verify(request).setAttribute("userRole", "admin");
    }

    @Test
    @DisplayName("通过后设置正确的 Redis key 前缀（token: + email）")
    void shouldUseCorrectRedisKeyPrefix() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("token-abc");
        when(jwtUtil.validateToken("token-abc")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("token-abc")).thenReturn("alice@example.com");
        when(valueOperations.get("token:alice@example.com")).thenReturn("token-abc");

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        verify(valueOperations).get("token:alice@example.com");
    }
}
