package com.zora.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 对话记忆存储（Phase 3.4）
 * <p>
 * 实现 LangChain4j {@link ChatMemoryStore} 接口，将对话消息序列化到 Redis。
 * 配合 {@link dev.langchain4j.memory.chat.MessageWindowChatMemory} 使用，
 * 提供短期的滑动窗口记忆能力。
 * </p>
 *
 * <h3>存储策略</h3>
 * <ul>
 * <li>Key 格式：{@code memory:conv:{conversationId}}</li>
 * <li>Value：JSON 数组 [{@code {"type":"USER","text":"..."}}, ...]</li>
 * <li>TTL：24 小时（超时后自动清除，回退到 MySQL 历史加载）</li>
 * </ul>
 *
 * <h3>序列化说明</h3>
 * <p>
 * LangChain4j 的 {@link ChatMessage} 子类（UserMessage、AiMessage 等）
 * 没有默认的 Jackson 序列化支持，因此手动构建 JSON 结构进行存储。
 * </p>
 *
 * <h3>与 MySQL 的关系</h3>
 * <p>
 * Redis 是"热缓存"层——用于 ChatMemory 窗口管理的快速读写。
 * MySQL 是"冷存储"层——保存完整的对话历史，供前端展示和摘要生成。
 * Redis 数据丢失时（TTL 过期或重启），系统自动从 MySQL 重建历史。
 * </p>
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "memory:conv:";

    /** 默认 TTL（24 小时） */
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * @param redisTemplate StringRedisTemplate（由 Spring 注入）
     */
    public RedisChatMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== ChatMemoryStore 接口实现 ====================

    /**
     * 从 Redis 加载对话消息列表
     *
     * @param memoryId 记忆 ID（本项目使用 conversationId）
     * @return 消息列表（不存在时返回空列表）
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return deserializeMessages(json);
        } catch (Exception e) {
            log.warn("Redis 记忆反序列化失败: key={}, error={}", key, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 更新 Redis 中的对话消息列表
     * <p>
     * 每次更新时刷新 TTL，确保活跃对话的记忆不会过期。
     * </p>
     *
     * @param memoryId 记忆 ID
     * @param messages 完整的消息列表（ChatMemory 会传入全量列表）
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        try {
            String json = serializeMessages(messages);
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL);
        } catch (Exception e) {
            log.error("Redis 记忆序列化失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 删除 Redis 中的对话记忆
     * <p>
     * 通常不需要主动调用——TTL 会自动清理。
     * 但在对话被删除时调用此方法立即清除缓存。
     * </p>
     *
     * @param memoryId 记忆 ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String key = buildKey(memoryId);
        redisTemplate.delete(key);
        log.debug("已清除对话记忆缓存: key={}", key);
    }

    // ==================== 序列化/反序列化 ====================

    /**
     * 将消息列表序列化为 JSON 数组字符串
     *
     * @param messages ChatMessage 列表
     * @return JSON 字符串
     */
    @SuppressWarnings("unchecked")
    private String serializeMessages(List<ChatMessage> messages) throws JsonProcessingException {
        List<Map<String, String>> records = new ArrayList<>();
        for (ChatMessage msg : messages) {
            String type = msg.type().name();
            String text = extractText(msg);
            records.add(Map.of("type", type, "text", text));
        }
        return MAPPER.writeValueAsString(records);
    }

    /**
     * 将 JSON 数组字符串反序列化为消息列表
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage> deserializeMessages(String json) throws JsonProcessingException {
        List<Map<String, String>> records = MAPPER.readValue(json, List.class);
        List<ChatMessage> messages = new ArrayList<>();

        for (Map<String, String> record : records) {
            String type = record.get("type");
            String text = record.get("text");

            if (type == null) continue;

            try {
                ChatMessageType messageType = ChatMessageType.valueOf(type);
                messages.add(createMessage(messageType, text));
            } catch (IllegalArgumentException e) {
                log.warn("未知消息类型: {}", type);
            }
        }

        return messages;
    }

    /**
     * 根据类型和文本创建 LangChain4j ChatMessage 实例
     */
    private ChatMessage createMessage(ChatMessageType type, String text) {
        return switch (type) {
            case SYSTEM -> SystemMessage.from(text != null ? text : "");
            case USER -> UserMessage.from(text != null ? text : "");
            case AI -> AiMessage.from(text != null ? text : "");
            default -> {
                log.warn("不支持的消息类型反序列化: {}", type);
                yield AiMessage.from(text != null ? text : "");
            }
        };
    }

    /**
     * 从 ChatMessage 中提取文本内容
     * <p>
     * 不同类型有不同提取方式，ToolExecutionResultMessage 不直接存储。
     * </p>
     */
    private String extractText(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> ((SystemMessage) message).text();
            case USER -> ((UserMessage) message).singleText();
            case AI -> {
                AiMessage ai = (AiMessage) message;
                if (ai.hasToolExecutionRequests()) {
                    yield "[Tool Call: " + ai.toolExecutionRequests().size() + " tools]";
                }
                yield ai.text() != null ? ai.text() : "";
            }
            case TOOL_EXECUTION_RESULT -> {
                ToolExecutionResultMessage tool = (ToolExecutionResultMessage) message;
                yield tool.text();
            }
            default -> "";
        };
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 Redis key
     *
     * @param memoryId 记忆 ID
     * @return "memory:conv:{id}"
     */
    private String buildKey(Object memoryId) {
        return KEY_PREFIX + (memoryId != null ? memoryId.toString() : "default");
    }

    /**
     * 检查 Redis 中是否存在指定对话的记忆
     */
    public boolean hasMemory(Object memoryId) {
        String key = buildKey(memoryId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 刷新 TTL（不修改内容）
     */
    public void refreshTtl(Object memoryId) {
        String key = buildKey(memoryId);
        redisTemplate.expire(key, DEFAULT_TTL);
    }
}
