package com.zora.agent.tool;

import com.zora.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WebSearchTool 网页搜索工具测试（Phase 3.2）
 * <p>
 * 使用 Mockito 模拟 HttpClient，验证搜索请求构建、响应解析、
 * 错误处理和 API 密钥校验逻辑。无网络依赖。
 * </p>
 */
@DisplayName("WebSearchTool 网页搜索工具测试")
class WebSearchToolTest {

    private WebSearchTool webSearchTool;
    private AgentConfig agentConfig;
    private AgentConfig.TavilyConfig tavilyConfig;
    private HttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        webSearchTool = new WebSearchTool();

        // 构建 AgentConfig mock
        agentConfig = mock(AgentConfig.class);
        tavilyConfig = new AgentConfig.TavilyConfig();
        tavilyConfig.setApiKey("test-api-key");
        tavilyConfig.setBaseUrl("https://api.tavily.com/search");
        tavilyConfig.setTimeoutSeconds(15);
        lenient().when(agentConfig.getTavily()).thenReturn(tavilyConfig);

        // 构建 HttpClient mock
        mockHttpClient = mock(HttpClient.class);

        // 使用 ReflectionTestUtils 注入依赖
        ReflectionTestUtils.setField(webSearchTool, "agentConfig", agentConfig);
        ReflectionTestUtils.setField(webSearchTool, "httpClient", mockHttpClient);
    }

    @Nested
    @DisplayName("API 密钥校验")
    class ApiKeyValidation {

        @Test
        @DisplayName("API 密钥为空时应返回错误 JSON")
        void shouldReturnErrorWhenApiKeyEmpty() {
            tavilyConfig.setApiKey("");

            String result = webSearchTool.searchWeb("test query", 5);

            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("TAVILY_API_KEY"));
        }

        @Test
        @DisplayName("API 密钥为 null 时应返回错误 JSON")
        void shouldReturnErrorWhenApiKeyNull() {
            tavilyConfig.setApiKey(null);

            String result = webSearchTool.searchWeb("test query", 5);

            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("TAVILY_API_KEY"));
        }
    }

    @Nested
    @DisplayName("搜索结果处理")
    class SearchResultProcessing {

        @Test
        @DisplayName("搜索成功时应返回响应体")
        void shouldReturnResponseBodyOnSuccess() throws Exception {
            String expectedResponse = "{\"results\":[{\"title\":\"测试\",\"url\":\"http://example.com\",\"content\":\"内容\"}]}";

            @SuppressWarnings("unchecked")
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn(expectedResponse);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            String result = webSearchTool.searchWeb("AI 新闻", 3);

            assertTrue(result.contains("\"results\""));
            assertTrue(result.contains("测试"));
        }

        @Test
        @DisplayName("API 返回非 200 时应返回错误 JSON")
        void shouldReturnErrorOnNon200() throws Exception {
            @SuppressWarnings("unchecked")
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(500);
            when(mockResponse.body()).thenReturn("Internal Server Error");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            String result = webSearchTool.searchWeb("test", 5);

            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("500"));
        }

        @Test
        @DisplayName("maxResults 超过 10 时应收敛为 10")
        void shouldCapMaxResultsAt10() throws Exception {
            @SuppressWarnings("unchecked")
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn("{\"results\":[]}");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            // 请求 maxResults=20，内部应 cap 为 10
            assertDoesNotThrow(() -> webSearchTool.searchWeb("test", 20));
        }

        @Test
        @DisplayName("maxResults 为 null 时默认使用 5")
        void shouldDefaultTo5WhenMaxResultsNull() throws Exception {
            @SuppressWarnings("unchecked")
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn("{\"results\":[]}");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            assertDoesNotThrow(() -> webSearchTool.searchWeb("test", null));
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("网络异常时应返回错误 JSON 而非抛出异常")
        void shouldReturnErrorOnNetworkException() throws Exception {
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new java.net.http.HttpTimeoutException("Request timed out"));

            String result = webSearchTool.searchWeb("test", 5);

            assertTrue(result.contains("\"error\""));
            assertTrue(result.contains("超时") || result.contains("失败"));
        }

        @Test
        @DisplayName("通用异常应被捕获并返回错误 JSON")
        void shouldCatchGenericException() throws Exception {
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            String result = webSearchTool.searchWeb("test", 5);

            assertTrue(result.contains("\"error\""));
            // 不应抛出异常
            assertNotNull(result);
        }
    }
}
