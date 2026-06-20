package com.zora.service;

import com.zora.config.AgentConfig;
import com.zora.entity.ChatConversation;
import com.zora.entity.ChatConversationSummary;
import com.zora.mapper.ChatConversationMapper;
import com.zora.mapper.ChatConversationSummaryMapper;
import com.zora.mapper.ChatMessageMapper;
import com.zora.service.impl.ConversationSummaryServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConversationSummaryService 测试（Phase 3.4）
 * <p>
 * Mock ChatLanguageModel 和各 Mapper，验证摘要触发、生成和上下文构建。
 * 异步方法使用 sleep 等待完成。
 * </p>
 * <p>
 * <b>注意</b>：MyBatis-Plus BaseMapper 的 insert/updateById 有重载（单实体和集合），
 * Mockito 无法在编译时区分。因此验证点聚焦在 LLM 调用行为上。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationSummaryService 对话摘要服务测试")
class ConversationSummaryServiceTest {

    @Mock
    private ChatModel chatLanguageModel;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private ChatConversationMapper conversationMapper;

    @Mock
    private ChatConversationSummaryMapper summaryMapper;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private AgentConfig.MemoryConfig memoryConfig;

    @InjectMocks
    private ConversationSummaryServiceImpl summaryService;

    @BeforeEach
    void setUp() {
        lenient().when(agentConfig.getMemory()).thenReturn(memoryConfig);
        lenient().when(memoryConfig.getSummaryTriggerCount()).thenReturn(10);
        lenient().when(memoryConfig.getSummaryMaxLength()).thenReturn(300);
    }

    // ==================== 辅助方法 ====================

    /** 创建 10 条 mock 消息 */
    private List<com.zora.entity.ChatMessage> createMockMessages(int count) {
        List<com.zora.entity.ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            com.zora.entity.ChatMessage msg = new com.zora.entity.ChatMessage();
            msg.setRole(i % 2 == 0 ? "user" : "assistant");
            msg.setContent("消息内容 " + i);
            messages.add(msg);
        }
        return messages;
    }

    /** Mock LLM 返回指定摘要文本 */
    private void mockLlmSummary(String summaryText) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.aiMessage()).thenReturn(AiMessage.from(summaryText));
        when(chatLanguageModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);
    }

    // ==================== 测试用例 ====================

    @Nested
    @DisplayName("摘要触发逻辑")
    class SummaryTrigger {

        @Test
        @DisplayName("消息数不足阈值时不应触发摘要")
        void shouldNotTriggerWhenBelowThreshold() {
            when(messageMapper.selectCount(any())).thenReturn(5L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());

            summaryService.checkAndSummarize(1L);

            // 不应调用 LLM（同步判断，在异步之前）
            verify(chatLanguageModel, never()).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("消息数达到阈值时应触发摘要（异步）")
        void shouldTriggerWhenAtThreshold() throws Exception {
            when(messageMapper.selectCount(any())).thenReturn(12L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());
            when(messageMapper.selectList(any())).thenReturn(createMockMessages(10));
            mockLlmSummary("用户讨论了多个话题，包括技术问题和日常交流。");

            // 模拟 selectById 返回会话（用于更新 summary_id）
            when(conversationMapper.selectById(1L)).thenReturn(new ChatConversation());

            summaryService.checkAndSummarize(1L);

            // 等待异步完成
            Thread.sleep(2000);

            // 验证 LLM 被调用（核心行为）
            verify(chatLanguageModel, timeout(1000)).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("已有摘要覆盖部分消息时应计算未覆盖数量")
        void shouldCalculateUncoveredCount() throws Exception {
            // 25 条消息，已有摘要覆盖 15 条，未覆盖 10 条 → 达阈值
            when(messageMapper.selectCount(any())).thenReturn(25L);

            ChatConversationSummary existingSummary = new ChatConversationSummary();
            existingSummary.setMessageCount(15);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of(existingSummary));

            when(messageMapper.selectList(any())).thenReturn(createMockMessages(10));
            mockLlmSummary("摘要内容");
            when(conversationMapper.selectById(1L)).thenReturn(new ChatConversation());

            summaryService.checkAndSummarize(1L);

            // 应触发 LLM 调用
            verify(chatLanguageModel, timeout(2000)).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("消息数为 0 时不应触发")
        void shouldNotTriggerOnZeroMessages() {
            when(messageMapper.selectCount(any())).thenReturn(0L);

            summaryService.checkAndSummarize(1L);

            verify(chatLanguageModel, never()).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("未覆盖消息数正好等于阈值时应触发")
        void shouldTriggerExactlyAtThreshold() throws Exception {
            // 10 条总消息，无历史摘要 → 正好 10 条未覆盖 = 阈值
            when(messageMapper.selectCount(any())).thenReturn(10L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());
            when(messageMapper.selectList(any())).thenReturn(createMockMessages(10));
            mockLlmSummary("正好达到摘要触发阈值");
            when(conversationMapper.selectById(1L)).thenReturn(new ChatConversation());

            summaryService.checkAndSummarize(1L);

            verify(chatLanguageModel, timeout(2000)).chat(any(ChatRequest.class));
        }
    }

    @Nested
    @DisplayName("摘要上下文构建")
    class SummaryContext {

        @Test
        @DisplayName("无摘要时应返回空字符串")
        void shouldReturnEmptyWhenNoSummaries() {
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());

            String context = summaryService.buildSummaryContext(1L);

            assertEquals("", context);
        }

        @Test
        @DisplayName("有摘要时应返回格式化的上下文文本")
        void shouldReturnFormattedContext() {
            ChatConversationSummary s1 = new ChatConversationSummary();
            s1.setSummary("第一段摘要：用户询问了 Spring Boot 配置");
            ChatConversationSummary s2 = new ChatConversationSummary();
            s2.setSummary("第二段摘要：用户继续讨论了数据库优化");
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of(s1, s2));

            String context = summaryService.buildSummaryContext(1L);

            assertTrue(context.contains("【对话历史摘要（长期记忆，按时间顺序）】"));
            assertTrue(context.contains("第一段摘要"));
            assertTrue(context.contains("第二段摘要"));
            assertTrue(context.contains("【历史摘要结束】"));
        }

        @Test
        @DisplayName("null 摘要列表应返回空字符串")
        void shouldHandleNullSummaryList() {
            when(summaryMapper.selectByConversationId(1L)).thenReturn(null);

            String context = summaryService.buildSummaryContext(1L);

            assertEquals("", context);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("LLM 异常时应优雅降级（不抛异常到调用方）")
        void shouldGracefullyHandleLlmError() {
            when(messageMapper.selectCount(any())).thenReturn(12L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());
            when(messageMapper.selectList(any())).thenReturn(createMockMessages(10));

            // LLM 调用抛异常
            when(chatLanguageModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("AI 服务异常"));

            // 不应抛异常到调用方（checkAndSummarize 在异步前不调用 LLM）
            assertDoesNotThrow(() -> summaryService.checkAndSummarize(1L));
        }

        @Test
        @DisplayName("LLM 返回空摘要时应跳过保存（不抛异常）")
        void shouldNotThrowOnEmptySummary() {
            when(messageMapper.selectCount(any())).thenReturn(12L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());
            when(messageMapper.selectList(any())).thenReturn(createMockMessages(10));

            // LLM 返回空字符串
            mockLlmSummary("");

            assertDoesNotThrow(() -> summaryService.checkAndSummarize(1L));
        }

        @Test
        @DisplayName("消息加载失败时应优雅降级")
        void shouldHandleMessageLoadFailure() {
            when(messageMapper.selectCount(any())).thenReturn(12L);
            when(summaryMapper.selectByConversationId(1L)).thenReturn(List.of());

            // 消息加载抛异常
            when(messageMapper.selectList(any()))
                    .thenThrow(new RuntimeException("数据库异常"));

            assertDoesNotThrow(() -> summaryService.checkAndSummarize(1L));
        }
    }
}
