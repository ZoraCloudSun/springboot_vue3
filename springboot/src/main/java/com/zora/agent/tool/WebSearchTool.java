package com.zora.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zora.config.AgentConfig;
import dev.langchain4j.agent.tool.P;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Web 搜索工具（Phase 3.2）
 * <p>
 * 使用 Tavily Search API 搜索互联网获取最新信息。
 * Tavily 是专为 AI Agent 设计的搜索引擎，返回结构化 JSON 结果，
 * 包含标题、URL 和内容摘要。
 * </p>
 *
 * <h3>API 格式</h3>
 * <pre>{@code
 * POST https://api.tavily.com/search
 * Body: {"api_key":"...", "query":"...", "max_results":5}
 * Response: {
 *   "results": [
 *     {"title":"...", "url":"...", "content":"..."}
 *   ]
 * }
 * }</pre>
 *
 * <h3>配置</h3>
 * <p>
 * 通过 {@code agent.tavily.*} 配置段注入 API 密钥和超时时间：
 * </p>
 * <ul>
 * <li>{@code agent.tavily.api-key} — Tavily API 密钥（环境变量 TAVILY_API_KEY）</li>
 * <li>{@code agent.tavily.base-url} — API 基础 URL，默认 https://api.tavily.com/search</li>
 * <li>{@code agent.tavily.timeout-seconds} — 请求超时（秒），默认 15</li>
 * </ul>
 *
 * <h3>启用/禁用</h3>
 * <p>
 * 通过 {@code agent.tools.web-search.enabled} 配置控制工具开关。
 * </p>
 *
 * @see AgentConfig.TavilyConfig
 * @see com.zora.agent.tool.Tool
 */
@Component
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Resource
    private AgentConfig agentConfig;

    /** HTTP 客户端，连接超时 15 秒 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 搜索互联网获取最新信息
     * <p>
     * 当 AI 需要查找实时信息、新闻、事实数据、最新动态时调用此工具。
     * 搜索结果为 JSON 格式，包含标题、URL 和内容摘要。
     * </p>
     *
     * <h3>适用场景</h3>
     * <ul>
     * <li>查询最新新闻、事件</li>
     * <li>查找事实性数据（日期、统计等）</li>
     * <li>获取实时信息（天气、股价等）</li>
     * <li>搜索技术文档、API 参考</li>
     * </ul>
     *
     * <h3>限制</h3>
     * <ul>
     * <li>免费额度：1000 次/月</li>
     * <li>单次最多返回 10 条结果</li>
     * <li>请求超时 15 秒</li>
     * </ul>
     *
     * @param query      搜索查询关键词，建议使用简洁、明确的关键词
     * @param maxResults 返回结果数量，默认 5，最大 10
     * @return JSON 格式的搜索结果字符串
     */
    @dev.langchain4j.agent.tool.Tool("搜索互联网获取最新信息。当需要查找实时信息、新闻、事实数据时使用此工具。"
            + "参数 query 为搜索关键词，maxResults 为返回结果数（默认5，最大10）")
    public String searchWeb(
            @P("搜索查询关键词，例如'最新AI新闻'或'圆周率最新计算记录'") String query,
            @P("返回结果数量，默认5，最大10") Integer maxResults) {

        AgentConfig.TavilyConfig tavily = agentConfig.getTavily();
        String apiKey = tavily.getApiKey();

        // API 密钥未配置时返回明确错误
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("WebSearch: Tavily API 密钥未配置");
            return "{\"error\": \"Tavily API 密钥未配置，请在 .env 文件中设置 TAVILY_API_KEY\"}";
        }

        // 限制返回结果数量
        int results = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 10) : 5;

        try {
            // 构建 Tavily API 请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("api_key", apiKey);
            requestBody.put("query", query);
            requestBody.put("max_results", results);

            String jsonBody = JSON_MAPPER.writeValueAsString(requestBody);

            // 构建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tavily.getBaseUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(tavily.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("WebSearch: query=\"{}\", maxResults={}", query, results);

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("WebSearch: 搜索成功，响应长度 {} 字符", response.body().length());
                return response.body();
            } else {
                log.error("WebSearch: Tavily API 返回 HTTP {} — {}", response.statusCode(), response.body());
                return "{\"error\": \"搜索服务返回错误状态码 " + response.statusCode() + "\"}";
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("WebSearch: 请求超时");
            return "{\"error\": \"搜索请求超时，请稍后重试\"}";
        } catch (Exception e) {
            log.error("WebSearch: 执行失败 — {}", e.getMessage(), e);
            return "{\"error\": \"搜索请求失败: " + e.getMessage() + "\"}";
        }
    }
}
