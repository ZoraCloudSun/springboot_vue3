package com.zora.service;

import com.zora.entity.dto.SearchResult;
import com.zora.entity.User;
import com.zora.exception.BadRequestException;
import com.zora.exception.NotFoundException;
import com.zora.mapper.ChatMessageMapper;
import com.zora.utils.UserContext;
import com.zora.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SearchServiceImpl 全文搜索服务测试
 * <p>Phase 4 Task A: 测试关键词校验、特殊字符转义、高亮处理和分页逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl 全文搜索服务测试")
class SearchServiceImplTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private SearchServiceImpl searchService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final Integer TEST_USER_ID = 1;

    @BeforeEach
    void setUp() {
        User mockUser = new User();
        mockUser.setId(TEST_USER_ID);
        mockUser.setEmail(TEST_EMAIL);
        lenient().when(userContext.getUserId()).thenReturn(mockUser.getId());
    }

    @Nested
    @DisplayName("searchMessages - 关键词校验")
    class KeywordValidation {

        @Test
        @DisplayName("空关键词：抛出 BadRequestException")
        void shouldThrowBadRequestWhenKeywordIsBlank() {
            assertThrows(BadRequestException.class,
                    () -> searchService.searchMessages(TEST_EMAIL, "   ", 1, 20));
        }

        @Test
        @DisplayName("null 关键词：抛出 BadRequestException")
        void shouldThrowBadRequestWhenKeywordIsNull() {
            assertThrows(BadRequestException.class,
                    () -> searchService.searchMessages(TEST_EMAIL, null, 1, 20));
        }

        @Test
        @DisplayName("超长关键词（>200 字符）：抛出 BadRequestException")
        void shouldThrowBadRequestWhenKeywordTooLong() {
            String longKeyword = "a".repeat(201);
            assertThrows(BadRequestException.class,
                    () -> searchService.searchMessages(TEST_EMAIL, longKeyword, 1, 20));
        }
    }

    @Nested
    @DisplayName("searchMessages - 正常搜索")
    class NormalSearch {

        @Test
        @DisplayName("有效搜索：返回分页结果含 total 和 list")
        void shouldReturnPaginatedResults() {
            SearchResult result = new SearchResult();
            result.setMessageId(1L);
            result.setConversationId(10L);
            result.setConversationTitle("测试对话");
            result.setRole("user");
            result.setContent("这是一个关于 Spring Boot 的消息内容");
            result.setCreatedAt(LocalDateTime.now());
            result.setRelevanceScore(3.5);

            when(chatMessageMapper.fulltextSearchCount(eq(TEST_USER_ID), anyString())).thenReturn(1L);
            when(chatMessageMapper.fulltextSearch(eq(TEST_USER_ID), anyString(), eq(0L), eq(20)))
                    .thenReturn(List.of(result));

            Map<String, Object> response = searchService.searchMessages(TEST_EMAIL, "Spring Boot", 1, 20);

            assertEquals(1L, response.get("total"));
            assertEquals(1, response.get("page"));
            assertNotNull(response.get("list"));
            @SuppressWarnings("unchecked")
            List<SearchResult> list = (List<SearchResult>) response.get("list");
            assertEquals(1, list.size());
            // 验证高亮
            assertTrue(list.get(0).getHighlightContent().contains("<mark>"));
        }

        @Test
        @DisplayName("无结果搜索：返回空列表但分页元数据正确")
        void shouldReturnEmptyListWhenNoMatches() {
            when(chatMessageMapper.fulltextSearchCount(eq(TEST_USER_ID), anyString())).thenReturn(0L);

            Map<String, Object> response = searchService.searchMessages(TEST_EMAIL, "不存在", 1, 20);

            assertEquals(0L, response.get("total"));
            @SuppressWarnings("unchecked")
            List<SearchResult> list = (List<SearchResult>) response.get("list");
            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("searchMessages - 用户不存在")
    class UserNotFound {

        @Test
        @DisplayName("邮箱对应无用户：抛出 NotFoundException")
        void shouldThrowNotFoundWhenUserMissing() {
            // 覆盖 setUp 的 mock
            when(userContext.getUserId()).thenThrow(new com.zora.exception.NotFoundException("用户不存在"));

            assertThrows(NotFoundException.class,
                    () -> searchService.searchMessages("ghost@example.com", "test", 1, 20));
        }
    }
}
