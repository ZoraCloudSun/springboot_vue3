package com.zora.service;

import com.zora.exception.BadRequestException;
import com.zora.exception.NotFoundException;
import com.zora.mapper.ChatConversationMapper;
import com.zora.mapper.ChatMessageMapper;
import com.zora.utils.UserContext;
import com.zora.service.impl.AiChatServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiChatServiceImpl 服务层测试（批量操作）
 * <p>
 * 使用 Mockito spy 将单条操作方法 mock 掉（doNothing / doThrow），
 * 只测试批量方法本身的遍历逻辑、异常跳过和成功计数。
 * 不依赖内部 Mapper 的复杂 stub 链。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatServiceImpl 批量操作测试")
class AiChatServiceImplTest {

    @Mock
    private UserContext userContext;
    @Mock
    private ChatConversationMapper conversationMapper;
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 通过 spy() 创建部分 mock：真实批量方法 + mock 单条方法 */
    private AiChatServiceImpl aiChatService;

    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        AiChatServiceImpl realService = new AiChatServiceImpl();
        // 注入 mock 的 Mapper 到真实实例
        ReflectionTestUtils.setField(realService, "userContext", userContext);
        ReflectionTestUtils.setField(realService, "conversationMapper", conversationMapper);
        ReflectionTestUtils.setField(realService, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(realService, "stringRedisTemplate", stringRedisTemplate);

        // 创建 spy：保留真实 batch 方法，mock 单条方法
        aiChatService = spy(realService);
    }

    // ==================== 批量删除 ====================

    @Nested
    @DisplayName("batchDeleteConversations - 批量软删除")
    class BatchDeleteTests {

        @Test
        @DisplayName("批量删除两个对话：两个都应成功，返回 2")
        void shouldDeleteBothConversations() throws Exception {
            doNothing().when(aiChatService).deleteConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchDeleteConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(2, count);
            verify(aiChatService, times(2)).deleteConversation(eq(TEST_EMAIL), anyLong());
        }

        @Test
        @DisplayName("批量删除时第一个失败：应继续处理第二个，返回成功计数 1")
        void shouldSkipFailedAndContinueOthers() throws Exception {
            doThrow(new NotFoundException("对话不存在"))
                    .doNothing()
                    .when(aiChatService).deleteConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchDeleteConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(1, count);
            verify(aiChatService, times(2)).deleteConversation(eq(TEST_EMAIL), anyLong());
        }

        @Test
        @DisplayName("批量删除空列表：返回 0，不调用单条删除")
        void shouldReturnZeroForEmptyList() throws Exception {
            int count = aiChatService.batchDeleteConversations(TEST_EMAIL,
                    Collections.emptyList());

            assertEquals(0, count);
            verify(aiChatService, never()).deleteConversation(anyString(), anyLong());
        }
    }

    // ==================== 批量恢复 ====================

    @Nested
    @DisplayName("batchRestoreConversations - 批量恢复")
    class BatchRestoreTests {

        @Test
        @DisplayName("批量恢复两个对话：都应成功，返回 2")
        void shouldRestoreBothConversations() throws Exception {
            doNothing().when(aiChatService).restoreConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchRestoreConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(2, count);
            verify(aiChatService, times(2)).restoreConversation(eq(TEST_EMAIL), anyLong());
        }

        @Test
        @DisplayName("批量恢复时一个未删除：应跳过并继续，返回成功计数 1")
        void shouldSkipNotDeletedAndContinue() throws Exception {
            doThrow(new BadRequestException("该对话未被删除，无需恢复"))
                    .doNothing()
                    .when(aiChatService).restoreConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchRestoreConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(1, count);
            verify(aiChatService, times(2)).restoreConversation(eq(TEST_EMAIL), anyLong());
        }
    }

    // ==================== 批量永久删除 ====================

    @Nested
    @DisplayName("batchPermanentDeleteConversations - 批量永久删除")
    class BatchPermanentDeleteTests {

        @Test
        @DisplayName("批量永久删除两个对话：都应成功，返回 2")
        void shouldPermanentDeleteBothConversations() throws Exception {
            doNothing().when(aiChatService).permanentDeleteConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchPermanentDeleteConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(2, count);
            verify(aiChatService, times(2)).permanentDeleteConversation(eq(TEST_EMAIL), anyLong());
        }

        @Test
        @DisplayName("批量永久删除时一个未删除：应跳过并继续")
        void shouldSkipNotDeletedForPermanentDelete() throws Exception {
            doThrow(new BadRequestException("该对话未被删除，不能永久删除"))
                    .doNothing()
                    .when(aiChatService).permanentDeleteConversation(eq(TEST_EMAIL), anyLong());

            int count = aiChatService.batchPermanentDeleteConversations(TEST_EMAIL,
                    Arrays.asList(1L, 2L));

            assertEquals(1, count);
            verify(aiChatService, times(2)).permanentDeleteConversation(eq(TEST_EMAIL), anyLong());
        }
    }
}
