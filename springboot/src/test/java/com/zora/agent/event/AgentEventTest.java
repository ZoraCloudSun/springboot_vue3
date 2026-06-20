package com.zora.agent.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentEvent SSE 事件序列化测试
 * <p>
 * 验证各种事件类型的 JSON 序列化正确性。
 * </p>
 */
@DisplayName("AgentEvent SSE 事件测试")
class AgentEventTest {

    @Nested
    @DisplayName("thinking 思考事件")
    class ThinkingEvent {

        @Test
        @DisplayName("应生成包含 type=thinking 的 JSON")
        void shouldGenerateThinkingJson() {
            AgentEvent event = AgentEvent.thinking("正在分析问题...");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"thinking\""));
            assertTrue(json.contains("正在分析问题..."));
            assertEquals("thinking", event.getType());
        }
    }

    @Nested
    @DisplayName("tool_call 工具调用事件")
    class ToolCallEvent {

        @Test
        @DisplayName("应生成包含工具名和参数的 JSON")
        void shouldGenerateToolCallJson() {
            Map<String, Object> args = new HashMap<>();
            args.put("expression", "sqrt(144)");

            AgentEvent event = AgentEvent.toolCall("calculate", args);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_call\""));
            assertTrue(json.contains("\"tool\":\"calculate\""));
            assertTrue(json.contains("sqrt(144)"));
            assertEquals("tool_call", event.getType());
        }

        @Test
        @DisplayName("应支持空参数")
        void shouldSupportEmptyArgs() {
            AgentEvent event = AgentEvent.toolCall("myTool", new HashMap<>());
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_call\""));
            assertNotNull(json);
        }
    }

    @Nested
    @DisplayName("tool_result 工具结果事件")
    class ToolResultEvent {

        @Test
        @DisplayName("应生成包含工具名和结果的 JSON")
        void shouldGenerateToolResultJson() {
            AgentEvent event = AgentEvent.toolResult("calculate", "{\"result\":12.0}");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_result\""));
            assertTrue(json.contains("\"tool\":\"calculate\""));
            assertTrue(json.contains("12.0"));
            assertEquals("tool_result", event.getType());
        }
    }

    @Nested
    @DisplayName("token 文本事件")
    class TokenEvent {

        @Test
        @DisplayName("应生成包含文本内容的 JSON")
        void shouldGenerateTokenJson() {
            AgentEvent event = AgentEvent.token("你好，世界");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"token\""));
            assertTrue(json.contains("你好，世界"));
            assertEquals("token", event.getType());
        }

        @Test
        @DisplayName("应正确处理换行符等特殊字符")
        void shouldHandleSpecialCharacters() {
            AgentEvent event = AgentEvent.token("第一行\n第二行\n\t缩进");
            String json = event.toJson();

            // JSON 序列化应正确 escape 特殊字符
            assertNotNull(json);
            // 能反序列化回来即表示序列化正确
            assertDoesNotThrow(() -> {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            });
        }
    }

    @Nested
    @DisplayName("done 完成事件")
    class DoneEvent {

        @Test
        @DisplayName("应生成包含 conversationId 的 JSON")
        void shouldGenerateDoneJson() {
            AgentEvent event = AgentEvent.done(42L);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"done\""));
            assertTrue(json.contains("\"conversationId\":42"));
            assertEquals("done", event.getType());
        }

        @Test
        @DisplayName("应支持 null conversationId")
        void shouldSupportNullConversationId() {
            AgentEvent event = AgentEvent.done(null);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"done\""));
            assertNotNull(json);
        }
    }

    @Nested
    @DisplayName("error 错误事件")
    class ErrorEvent {

        @Test
        @DisplayName("应生成包含错误信息的 JSON")
        void shouldGenerateErrorJson() {
            AgentEvent event = AgentEvent.error("AI 服务异常");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"error\""));
            assertTrue(json.contains("AI 服务异常"));
            assertEquals("error", event.getType());
        }
    }

    @Test
    @DisplayName("toJson 不应返回 null 或空字符串")
    void toJsonShouldNeverReturnNull() {
        AgentEvent event = AgentEvent.thinking("test");
        String json = event.toJson();

        assertNotNull(json);
        assertFalse(json.isEmpty());
    }

    // ==================== Phase 3.3 补充测试 ====================

    @Nested
    @DisplayName("JSON 结构完整性")
    class JsonStructure {

        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        @Test
        @DisplayName("thinking 事件 JSON 应包含 type 和 content 字段")
        void thinkingShouldHaveCorrectStructure() throws Exception {
            String json = AgentEvent.thinking("分析中").toJson();
            var node = mapper.readTree(json);
            assertEquals("thinking", node.get("type").asText());
            assertEquals("分析中", node.get("content").asText());
        }

        @Test
        @DisplayName("tool_call 事件 JSON 应包含 type、tool、args 字段")
        void toolCallShouldHaveCorrectStructure() throws Exception {
            Map<String, Object> args = new HashMap<>();
            args.put("query", "AI新闻");
            String json = AgentEvent.toolCall("searchWeb", args).toJson();
            var node = mapper.readTree(json);
            assertEquals("tool_call", node.get("type").asText());
            assertEquals("searchWeb", node.get("tool").asText());
            assertEquals("AI新闻", node.get("args").get("query").asText());
        }

        @Test
        @DisplayName("tool_result 事件 JSON 应包含 type、tool、content 字段")
        void toolResultShouldHaveCorrectStructure() throws Exception {
            String json = AgentEvent.toolResult("calculate", "{\"result\":42}").toJson();
            var node = mapper.readTree(json);
            assertEquals("tool_result", node.get("type").asText());
            assertEquals("calculate", node.get("tool").asText());
            assertNotNull(node.get("content"));
        }

        @Test
        @DisplayName("token 事件 JSON 应包含 type 和 content 字段")
        void tokenShouldHaveCorrectStructure() throws Exception {
            String json = AgentEvent.token("回答片段").toJson();
            var node = mapper.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("回答片段", node.get("content").asText());
        }

        @Test
        @DisplayName("done 事件 JSON 应包含 type 和 conversationId 字段")
        void doneShouldHaveCorrectStructure() throws Exception {
            String json = AgentEvent.done(99L).toJson();
            var node = mapper.readTree(json);
            assertEquals("done", node.get("type").asText());
            assertEquals(99, node.get("conversationId").asLong());
        }

        @Test
        @DisplayName("error 事件 JSON 应包含 type 和 message 字段")
        void errorShouldHaveCorrectStructure() throws Exception {
            String json = AgentEvent.error("服务异常").toJson();
            var node = mapper.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("服务异常", node.get("message").asText());
        }
    }

    @Nested
    @DisplayName("数据完整性")
    class DataIntegrity {

        @Test
        @DisplayName("getType() 应与构造时传入的 type 一致")
        void getTypeShouldMatch() {
            assertEquals("thinking", AgentEvent.thinking("x").getType());
            assertEquals("tool_call", AgentEvent.toolCall("t", Map.of()).getType());
            assertEquals("tool_result", AgentEvent.toolResult("t", "r").getType());
            assertEquals("token", AgentEvent.token("x").getType());
            assertEquals("done", AgentEvent.done(1L).getType());
            assertEquals("error", AgentEvent.error("x").getType());
        }

        @Test
        @DisplayName("getData() 不应为 null")
        void getDataShouldNotBeNull() {
            assertNotNull(AgentEvent.thinking("x").getData());
            assertNotNull(AgentEvent.toolCall("t", Map.of()).getData());
            assertNotNull(AgentEvent.toolResult("t", "r").getData());
            assertNotNull(AgentEvent.token("x").getData());
            assertNotNull(AgentEvent.done(1L).getData());
            assertNotNull(AgentEvent.error("x").getData());
        }

        @Test
        @DisplayName("done(null) 的 data 应包含 null conversationId")
        void doneWithNullShouldContainNullId() {
            AgentEvent event = AgentEvent.done(null);
            assertTrue(event.toJson().contains("\"conversationId\":null"));
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空字符串 content 应正常序列化")
        void emptyContentShouldSerialize() {
            String json = AgentEvent.thinking("").toJson();
            assertTrue(json.contains("\"type\":\"thinking\""));
            assertNotNull(json);
        }

        @Test
        @DisplayName("超长 content 应正常序列化")
        void veryLongContentShouldSerialize() {
            String longText = "A".repeat(5000);
            String json = AgentEvent.token(longText).toJson();
            assertTrue(json.contains(longText));
            assertTrue(json.length() > 5000);
        }

        @Test
        @DisplayName("工具名包含特殊字符应正常序列化")
        void specialCharsInToolNameShouldEscape() {
            AgentEvent event = AgentEvent.toolCall("tool\"with'quotes", Map.of("k", "v"));
            String json = event.toJson();
            // JSON 应合法可解析
            assertDoesNotThrow(() ->
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
        }

        @Test
        @DisplayName("args 中包含嵌套对象应正常序列化")
        void nestedArgsShouldSerialize() {
            Map<String, Object> nested = new HashMap<>();
            nested.put("outer", Map.of("inner", "value"));
            AgentEvent event = AgentEvent.toolCall("test", nested);
            String json = event.toJson();
            assertDoesNotThrow(() ->
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
        }

        @Test
        @DisplayName("tool_result 内容为空字符串")
        void emptyToolResultShouldSerialize() {
            String json = AgentEvent.toolResult("t", "").toJson();
            assertTrue(json.contains("\"type\":\"tool_result\""));
        }
    }

    @Nested
    @DisplayName("完整 Agent 对话流程模拟")
    class FullFlow {

        @Test
        @DisplayName("应能模拟完整的 Agent 对话事件序列")
        void shouldSimulateFullAgentFlow() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // 模拟一次带工具调用的 Agent 对话
            String[] events = {
                AgentEvent.thinking("正在分析您的问题...").toJson(),
                AgentEvent.thinking("需要使用搜索工具查找最新信息").toJson(),
                AgentEvent.toolCall("searchWeb",
                        Map.of("query", "2025年AI发展趋势", "maxResults", 5)).toJson(),
                AgentEvent.toolResult("searchWeb",
                        "{\"results\":[{\"title\":\"AI趋势\",\"content\":\"...\"}]}").toJson(),
                AgentEvent.thinking("已获取搜索结果，正在总结...").toJson(),
                AgentEvent.token("根据搜索结果，").toJson(),
                AgentEvent.token("2025年AI发展趋势包括...").toJson(),
                AgentEvent.done(42L).toJson(),
            };

            // 每个事件都应是合法的 JSON
            for (String json : events) {
                var node = mapper.readTree(json);
                assertTrue(node.has("type"), "每个事件必须包含 type 字段: " + json);
                assertNotNull(node.get("type").asText());
            }

            // 验证流程顺序：首个事件是 thinking，最后是 done
            var first = mapper.readTree(events[0]);
            assertEquals("thinking", first.get("type").asText());

            var last = mapper.readTree(events[events.length - 1]);
            assertEquals("done", last.get("type").asText());
            assertEquals(42, last.get("conversationId").asLong());
        }

        @Test
        @DisplayName("无工具调用的简单对话流程也应正确")
        void shouldHandleSimpleFlowWithoutTools() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            String[] events = {
                AgentEvent.thinking("这是一个简单问题，无需工具").toJson(),
                AgentEvent.token("你好！").toJson(),
                AgentEvent.token("有什么可以帮你的？").toJson(),
                AgentEvent.done(1L).toJson(),
            };

            for (String json : events) {
                assertDoesNotThrow(() -> mapper.readTree(json));
            }
        }

        @Test
        @DisplayName("错误流程应正确终止")
        void shouldHandleErrorFlow() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            String[] events = {
                AgentEvent.thinking("正在尝试调用工具...").toJson(),
                AgentEvent.toolCall("searchWeb", Map.of("query", "test")).toJson(),
                AgentEvent.error("搜索服务暂时不可用").toJson(),
            };

            var errorEvent = mapper.readTree(events[2]);
            assertEquals("error", errorEvent.get("type").asText());
            assertTrue(errorEvent.get("message").asText().contains("不可用"));
        }
    }

    // ==================== Phase 3.5: withField 多 Agent 字段 ====================

    @Nested
    @DisplayName("withField 多 Agent 字段（Phase 3.5）")
    class WithField {

        @Test
        @DisplayName("withField 应在 JSON 中添加自定义字段")
        void shouldAddCustomFieldToJson() throws Exception {
            AgentEvent event = AgentEvent.thinking("分析中")
                    .withField("agent", "research");
            String json = event.toJson();

            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            assertEquals("thinking", node.get("type").asText());
            assertEquals("research", node.get("agent").asText());
        }

        @Test
        @DisplayName("withField 应支持链式调用添加多个字段")
        void shouldSupportChaining() throws Exception {
            AgentEvent event = AgentEvent.toolCall("searchWeb", Map.of("query", "AI"))
                    .withField("agent", "research")
                    .withField("step", 2);
            String json = event.toJson();

            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            assertEquals("tool_call", node.get("type").asText());
            assertEquals("research", node.get("agent").asText());
            assertEquals(2, node.get("step").asInt());
        }

        @Test
        @DisplayName("各种事件类型都应支持 withField")
        void allEventTypesShouldSupportWithField() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            String[] events = {
                AgentEvent.thinking("思考").withField("agent", "supervisor").toJson(),
                AgentEvent.toolCall("t", Map.of()).withField("agent", "math").toJson(),
                AgentEvent.toolResult("t", "r").withField("agent", "code").toJson(),
                AgentEvent.token("文本").withField("agent", "summarizer").toJson(),
                AgentEvent.done(1L).withField("totalSteps", 3).toJson(),
                AgentEvent.error("错误").withField("agent", "research").toJson(),
            };

            for (String json : events) {
                var node = mapper.readTree(json);
                assertTrue(node.has("type"), "必须包含 type 字段");
                assertTrue(node.has("agent") || node.has("totalSteps"),
                        "必须包含自定义字段: " + json);
            }
        }

        @Test
        @DisplayName("withField 不应覆盖原有 type 和 data 字段")
        void shouldNotOverrideOriginalFields() throws Exception {
            AgentEvent event = AgentEvent.toolCall("calculate", Map.of("expr", "sqrt(144)"))
                    .withField("agent", "math");
            String json = event.toJson();

            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            assertEquals("tool_call", node.get("type").asText());
            assertEquals("calculate", node.get("tool").asText());
            assertEquals("math", node.get("agent").asText());
        }
    }

    @Nested
    @DisplayName("前端兼容性")
    class FrontendCompatibility {

        @Test
        @DisplayName("所有事件 JSON 应能被前端 dispatchEvent 正确路由")
        void allEventTypesShouldBeRoutable() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // 模拟前端 dispatchEvent 的 switch-case 逻辑
            record RoutedEvent(String type, String content) {}

            var all = new RoutedEvent[]{
                new RoutedEvent("thinking", "思考中"),
                new RoutedEvent("tool_call", null),
                new RoutedEvent("tool_result", null),
                new RoutedEvent("token", "文本"),
                new RoutedEvent("done", null),
                new RoutedEvent("error", "错误"),
            };

            for (var expected : all) {
                AgentEvent event = switch (expected.type) {
                    case "thinking" -> AgentEvent.thinking(expected.content);
                    case "tool_call" -> AgentEvent.toolCall("t", Map.of());
                    case "tool_result" -> AgentEvent.toolResult("t", "r");
                    case "token" -> AgentEvent.token(expected.content);
                    case "done" -> AgentEvent.done(1L);
                    case "error" -> AgentEvent.error(expected.content);
                    default -> throw new IllegalStateException();
                };

                var node = mapper.readTree(event.toJson());
                assertEquals(expected.type, node.get("type").asText(),
                        "type 字段必须与事件类型一致");
            }
        }
    }
}
