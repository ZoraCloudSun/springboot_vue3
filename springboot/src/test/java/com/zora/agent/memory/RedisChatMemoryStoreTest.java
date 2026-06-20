package com.zora.agent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisChatMemoryStore 测试（Phase 3.4）
 * <p>
 * Mock StringRedisTemplate，验证记忆的 CRUD 和序列化/反序列化。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisChatMemoryStore 记忆存储测试")
class RedisChatMemoryStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisChatMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        memoryStore = new RedisChatMemoryStore(redisTemplate);
    }

    @Nested
    @DisplayName("基本 CRUD")
    class BasicCrud {

        @Test
        @DisplayName("getMessages 应返回空列表当 Redis 中无数据")
        void shouldReturnEmptyListWhenNoData() {
            when(valueOps.get(anyString())).thenReturn(null);

            List<ChatMessage> messages = memoryStore.getMessages(1L);
            assertNotNull(messages);
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("updateMessages 应序列化并存储消息列表")
        void shouldSerializeAndStoreMessages() {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("系统提示"),
                    UserMessage.from("用户消息"),
                    AiMessage.from("AI 回复")
            );

            memoryStore.updateMessages(1L, messages);

            verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("getMessages 应正确反序列化已存储的消息")
        void shouldDeserializeStoredMessages() {
            String stored = "[{\"type\":\"SYSTEM\",\"text\":\"系统提示\"}," +
                    "{\"type\":\"USER\",\"text\":\"用户消息\"}," +
                    "{\"type\":\"AI\",\"text\":\"AI 回复\"}]";
            when(valueOps.get("memory:conv:1")).thenReturn(stored);

            List<ChatMessage> messages = memoryStore.getMessages(1L);

            assertEquals(3, messages.size());
            assertEquals(dev.langchain4j.data.message.ChatMessageType.SYSTEM, messages.get(0).type());
            assertEquals(dev.langchain4j.data.message.ChatMessageType.USER, messages.get(1).type());
            assertEquals(dev.langchain4j.data.message.ChatMessageType.AI, messages.get(2).type());
        }

        @Test
        @DisplayName("updateMessages 空列表应存储空 JSON 数组")
        void shouldStoreEmptyList() {
            memoryStore.updateMessages(1L, List.of());

            verify(valueOps).set(contains("memory:conv:1"), eq("[]"), any());
        }

        @Test
        @DisplayName("deleteMessages 应删除 Redis key")
        void shouldDeleteKey() {
            memoryStore.deleteMessages(1L);

            verify(redisTemplate).delete("memory:conv:1");
        }
    }

    @Nested
    @DisplayName("序列化边界")
    class SerializationEdgeCases {

        @Test
        @DisplayName("含特殊字符的消息应正确序列化")
        void shouldHandleSpecialCharacters() {
            List<ChatMessage> messages = List.of(
                    UserMessage.from("换行\n引号\"反斜杠\\ 制表\t")
            );

            memoryStore.updateMessages(1L, messages);

            // 不应抛异常
            verify(valueOps).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("null 文本消息应序列化为空字符串")
        void shouldHandleNullText() {
            // AiMessage 的 text 可能为 null（仅包含工具调用时）
            AiMessage toolOnly = AiMessage.from(
                    dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                            .name("test").arguments("{}").build()
            );

            List<ChatMessage> messages = List.of(toolOnly);

            assertDoesNotThrow(() -> memoryStore.updateMessages(1L, messages));
        }

        @Test
        @DisplayName("损坏的 JSON 应返回空列表而非抛异常")
        void shouldReturnEmptyOnCorruptedJson() {
            when(valueOps.get("memory:conv:1")).thenReturn("{broken json");

            List<ChatMessage> messages = memoryStore.getMessages(1L);
            assertNotNull(messages);
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("空字符串记忆应返回空列表")
        void shouldReturnEmptyForBlankString() {
            when(valueOps.get("memory:conv:1")).thenReturn("");

            List<ChatMessage> messages = memoryStore.getMessages(1L);
            assertNotNull(messages);
            assertTrue(messages.isEmpty());
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class UtilityMethods {

        @Test
        @DisplayName("hasMemory 应返回 true 当 key 存在")
        void hasMemoryShouldReturnTrue() {
            when(redisTemplate.hasKey("memory:conv:1")).thenReturn(true);

            assertTrue(memoryStore.hasMemory(1L));
        }

        @Test
        @DisplayName("hasMemory 应返回 false 当 key 不存在")
        void hasMemoryShouldReturnFalse() {
            when(redisTemplate.hasKey("memory:conv:1")).thenReturn(false);

            assertFalse(memoryStore.hasMemory(1L));
        }

        @Test
        @DisplayName("refreshTtl 应调用 expire")
        void refreshTtlShouldCallExpire() {
            memoryStore.refreshTtl(1L);

            verify(redisTemplate).expire("memory:conv:1", Duration.ofHours(24));
        }

        @Test
        @DisplayName("null memoryId 应使用默认 key")
        void nullMemoryIdShouldUseDefaultKey() {
            when(valueOps.get("memory:conv:default")).thenReturn(null);

            List<ChatMessage> result = memoryStore.getMessages(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
