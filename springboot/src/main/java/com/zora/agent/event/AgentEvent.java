package com.zora.agent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent SSE 结构化事件（Phase 3）
 * <p>
 * 用于在 SSE 流中传输 Agent 推理过程的结构化信息。
 * 替代 Phase 1 中单一 token JSON 编码协议，支持多种事件类型：
 * </p>
 *
 * <h3>事件类型</h3>
 * <table>
 * <tr><td>thinking</td><td>Agent 思考过程（分析问题、选择工具等）</td></tr>
 * <tr><td>tool_call</td><td>工具调用请求（包含工具名和参数）</td></tr>
 * <tr><td>tool_result</td><td>工具执行结果</td></tr>
 * <tr><td>token</td><td>最终回答的文本 token（流式输出）</td></tr>
 * <tr><td>done</td><td>对话结束（包含 conversationId）</td></tr>
 * <tr><td>error</td><td>错误信息</td></tr>
 * </table>
 *
 * <h3>JSON 格式示例</h3>
 * <pre>{@code
 * {"type":"thinking",    "content":"正在分析问题..."}
 * {"type":"tool_call",   "tool":"calculate", "args":{"expression":"sqrt(144)"}}
 * {"type":"tool_result", "tool":"calculate", "content":"{\"result\":12.0}"}
 * {"type":"token",       "content":"计算结果为12。"}
 * {"type":"done",        "conversationId":42}
 * }</pre>
 */
public class AgentEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 事件类型 */
    private final String type;

    /** 事件携带的数据 */
    private final Map<String, Object> data;

    /**
     * 私有构造器，通过工厂方法创建
     */
    private AgentEvent(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建思考事件
     * <p>
     * 表示 Agent 的内部推理过程，如"正在分析问题..."、"正在选择工具..."。
     * </p>
     *
     * @param content 思考内容描述
     * @return AgentEvent 实例
     */
    public static AgentEvent thinking(String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        return new AgentEvent("thinking", data);
    }

    /**
     * 创建工具调用事件
     * <p>
     * 表示 Agent 决定调用某个工具。前端可显示为可展开的工具调用卡片。
     * </p>
     *
     * @param tool 工具名称（如 "calculate"、"searchWeb"）
     * @param args 工具参数（key-value 形式）
     * @return AgentEvent 实例
     */
    public static AgentEvent toolCall(String tool, Map<String, Object> args) {
        Map<String, Object> data = new HashMap<>();
        data.put("tool", tool);
        data.put("args", args);
        return new AgentEvent("tool_call", data);
    }

    /**
     * 创建工具结果事件
     * <p>
     * 表示工具执行完毕，返回结果。通常与 tool_call 配对使用。
     * </p>
     *
     * @param tool    工具名称
     * @param content 工具返回的内容（JSON 字符串或纯文本）
     * @return AgentEvent 实例
     */
    public static AgentEvent toolResult(String tool, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("tool", tool);
        data.put("content", content);
        return new AgentEvent("tool_result", data);
    }

    /**
     * 创建文本 token 事件
     * <p>
     * 最终回答的文本片段，与 Phase 1 SSE 协议兼容。
     * </p>
     *
     * @param content token 文本内容
     * @return AgentEvent 实例
     */
    public static AgentEvent token(String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        return new AgentEvent("token", data);
    }

    /**
     * 创建对话完成事件
     *
     * @param conversationId 对话 ID
     * @return AgentEvent 实例
     */
    public static AgentEvent done(Long conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        return new AgentEvent("done", data);
    }

    /**
     * 创建错误事件
     *
     * @param message 错误描述（已脱敏）
     * @return AgentEvent 实例
     */
    public static AgentEvent error(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return new AgentEvent("error", data);
    }

    // ==================== 序列化 ====================

    /**
     * 序列化为 JSON 字符串
     * <p>
     * 输出格式：{@code {"type":"...", "data":{...}}}。
     * 直接拼入 SSE {@code data:} 行。
     * </p>
     *
     * @return JSON 字符串
     * @throws RuntimeException 序列化失败时抛出（极少发生）
     */
    public String toJson() {
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", type);
            // 扁平化 data 到顶层，方便前端解析
            envelope.putAll(data);
            return MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // JSON 编码失败时返回一个保底格式
            return "{\"type\":\"" + type + "\",\"content\":\"(序列化错误)\"}";
        }
    }

    /**
     * 向事件数据中添加额外字段（Phase 3.5 多 Agent 显示需要）
     * <p>
     * 例：{@code AgentEvent.thinking("...").withField("agent", "research").toJson()}
     * 会在 JSON 中增加 {@code "agent":"research"} 字段。
     * 此方法返回 this 以支持链式调用。
     * </p>
     *
     * @param key   字段名
     * @param value 字段值
     * @return this（链式调用）
     */
    public AgentEvent withField(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    // ==================== getter ====================

    public String getType() { return type; }
    public Map<String, Object> getData() { return data; }
}
