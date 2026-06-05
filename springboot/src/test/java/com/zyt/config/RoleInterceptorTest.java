package com.zyt.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleInterceptor 角色权限拦截器测试")
class RoleInterceptorTest {

    private RoleInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter stringWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new RoleInterceptor();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        lenient().when(response.getWriter()).thenReturn(printWriter);
    }

    // ==================== 非 Controller 方法 ====================

    @Test
    @DisplayName("非 HandlerMethod（静态资源） → 直接放行")
    void shouldPass_whenNotHandlerMethod() throws Exception {
        boolean result = interceptor.preHandle(request, response, "not_a_handler");

        assertTrue(result);
        verify(response, never()).setStatus(anyInt());
    }

    // ==================== 无 @RequireRole 注解 ====================

    @Test
    @DisplayName("无 @RequireRole 注解 → 放行")
    void shouldPass_whenNoRequireRoleAnnotation() throws Exception {
        // 创建一个模拟的 HandlerMethod，方法上没有 @RequireRole
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class); // 类上也没有

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    // ==================== 方法级注解 - 角色匹配 ====================

    @Test
    @DisplayName("@RequireRole(\"admin\") + 用户 role=admin → 放行")
    void shouldPass_whenRoleMatches() throws Exception {
        RequireRole annotation = mock(RequireRole.class);
        when(annotation.value()).thenReturn("admin");

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(annotation);
        lenient().when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        when(request.getAttribute("userRole")).thenReturn("admin");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    @Test
    @DisplayName("@RequireRole(\"admin\") + 用户 role=user → 返回 403")
    void shouldReject_whenRoleDoesNotMatch() throws Exception {
        RequireRole annotation = mock(RequireRole.class);
        when(annotation.value()).thenReturn("admin");

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(annotation);
        lenient().when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        when(request.getAttribute("userRole")).thenReturn("user");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(403);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("权限不足"));
        assertTrue(stringWriter.toString().contains("403"));
    }

    // ==================== 类级注解 ====================

    @Test
    @DisplayName("类级 @RequireRole → 方法无注解时使用类级")
    void shouldUseClassLevelAnnotation_whenMethodHasNone() throws Exception {
        // 方法级无注解
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

        // AdminController 类上有 @RequireRole("admin")，反射获取
        when(handlerMethod.getBeanType()).thenReturn((Class) AdminController.class);

        when(request.getAttribute("userRole")).thenReturn("admin");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    // ==================== 未登录（无 userRole 属性） ====================

    @Test
    @DisplayName("无 userRole 属性 → 返回 403 '未登录，无法访问'")
    void shouldReject_whenNoUserRoleAttribute() throws Exception {
        RequireRole annotation = mock(RequireRole.class);
        // annotation.value() 不会被调用，因为 userRole=null 直接返回

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(annotation);
        lenient().when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        when(request.getAttribute("userRole")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(403);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("未登录"));
    }

    // ==================== 403 响应内容校验 ====================

    @Test
    @DisplayName("403 响应包含正确的 JSON 格式错误信息")
    void shouldWrite403JsonResponse() throws Exception {
        RequireRole annotation = mock(RequireRole.class);
        when(annotation.value()).thenReturn("admin");

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(annotation);
        lenient().when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        when(request.getAttribute("userRole")).thenReturn("user");

        interceptor.preHandle(request, response, handlerMethod);

        verify(response).setContentType("application/json;charset=UTF-8");
        printWriter.flush();
        String json = stringWriter.toString();
        assertTrue(json.contains("\"code\""));
        assertTrue(json.contains("\"msg\""));
        assertTrue(json.contains("权限不足"));
    }

    // 用于测试类级注解的辅助类
    @RequireRole("admin")
    private static class AdminController {
        public void someMethod() {}
    }
}
