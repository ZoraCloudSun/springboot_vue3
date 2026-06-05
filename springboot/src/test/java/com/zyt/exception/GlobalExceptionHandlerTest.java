package com.zyt.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("GlobalExceptionHandler 全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 内嵌测试控制器，仅用于抛出各类异常
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/bad-request")
        public void badRequest() {
            throw new BadRequestException("参数错误");
        }

        @GetMapping("/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException("请先登录");
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenException("权限不足");
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new NotFoundException("用户不存在");
        }

        @GetMapping("/rate-limit")
        public void rateLimit() {
            throw new RateLimitException("请求过于频繁");
        }

        @GetMapping("/business")
        public void businessException() {
            throw new BusinessException(418, "自定义错误");
        }

        @GetMapping("/server-error")
        public void serverError() {
            throw new RuntimeException("内部错误");
        }

        @PostMapping("/method-not-allowed-read")
        public void methodNotAllowed() {}

        @PostMapping("/body-error")
        public void bodyError(@RequestBody Map<String, String> body) {}
    }

    // ==================== BusinessException 子类 ====================

    @Nested
    @DisplayName("业务异常处理")
    class BusinessExceptionTests {

        @Test
        @DisplayName("BadRequestException → HTTP 200, body.code=400")
        void shouldReturn200WithCode400_forBadRequest() throws Exception {
            mockMvc.perform(get("/test/bad-request"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("参数错误"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("UnauthorizedException → HTTP 401, body.code=401")
        void shouldReturn401_forUnauthorized() throws Exception {
            mockMvc.perform(get("/test/unauthorized"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.msg").value("请先登录"));
        }

        @Test
        @DisplayName("ForbiddenException → HTTP 200, body.code=403")
        void shouldReturn200WithCode403_forForbidden() throws Exception {
            mockMvc.perform(get("/test/forbidden"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.msg").value("权限不足"));
        }

        @Test
        @DisplayName("NotFoundException → HTTP 200, body.code=404")
        void shouldReturn200WithCode404_forNotFound() throws Exception {
            mockMvc.perform(get("/test/not-found"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("用户不存在"));
        }

        @Test
        @DisplayName("RateLimitException → HTTP 200, body.code=429")
        void shouldReturn200WithCode429_forRateLimit() throws Exception {
            mockMvc.perform(get("/test/rate-limit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.msg").value("请求过于频繁"));
        }

        @Test
        @DisplayName("BusinessException(418) → HTTP 200, body.code=418")
        void shouldReturn200WithCode418_forCustomBusinessException() throws Exception {
            mockMvc.perform(get("/test/business"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(418))
                    .andExpect(jsonPath("$.msg").value("自定义错误"));
        }
    }

    // ==================== 兜底处理 ====================

    @Test
    @DisplayName("RuntimeException → HTTP 200, body.code=500")
    void shouldReturn200WithCode500_forRuntimeException() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("服务器内部错误"));
    }

    // ==================== Spring MVC 内置异常 ====================

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException → HTTP 200, body.code=405")
    void shouldReturn200WithCode405_forMethodNotSupported() throws Exception {
        // GET 访问仅支持 POST 的端点
        mockMvc.perform(get("/test/method-not-allowed-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.msg").value("请求方法不支持"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → HTTP 200, body.code=400")
    void shouldReturn200WithCode400_forUnreadableBody() throws Exception {
        mockMvc.perform(post("/test/body-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not valid json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求体格式错误"));
    }
}
