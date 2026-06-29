package com.zora.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.zora.entity.KbChunk;
import com.zora.entity.KbDocument;
import com.zora.entity.KnowledgeBase;
import com.zora.entity.User;
import com.zora.exception.BadRequestException;
import com.zora.exception.ForbiddenException;
import com.zora.exception.NotFoundException;
import com.zora.mapper.*;
import com.zora.utils.UserContext;
import com.zora.service.RagProcessingService;
import com.zora.service.impl.RagServiceImpl;
import com.zora.service.impl.SimpleEmbeddingStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagServiceImpl 服务测试（Phase 2: RAG 知识库）
 * <p>
 * 使用 Mockito，无 Spring 上下文加载。测试知识库 CRUD 和文档管理的业务逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagServiceImpl 服务测试")
class RagServiceImplTest {

    @Mock
    private UserContext userContext;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbChunkMapper chunkMapper;
    @Mock
    private RagProcessingService ragProcessingService;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private SimpleEmbeddingStore embeddingStore;

    @InjectMocks
    private RagServiceImpl ragService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final Integer USER_ID = 1;
    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ragService, "uploadDir", "./test-uploads");
        ReflectionTestUtils.setField(ragService, "maxFileSize", 10485760L);
        ReflectionTestUtils.setField(ragService, "maxRetrieveResults", 5);
        ReflectionTestUtils.setField(ragService, "minRelevanceScore", 0.3);

        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setEmail(TEST_EMAIL);
        testUser.setRole("user");

        // 默认 stub: 用户存在
        lenient().when(userContext.getUserId()).thenReturn(testUser.getId());

        // 默认 stub: 嵌入模型（避免 NPE）
        Embedding dummyEmbedding = new Embedding(new float[] { 0.1f, 0.2f, 0.3f });
        lenient().when(embeddingModel.embed(anyString())).thenReturn(Response.from(dummyEmbedding));

        // 默认 stub: 向量搜索返回空结果（避免 NPE）
        dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> emptyResult = new dev.langchain4j.store.embedding.EmbeddingSearchResult<>(
                List.of());
        lenient().when(embeddingStore.search(any())).thenReturn(emptyResult);
    }

    @Nested
    @DisplayName("知识库 CRUD")
    class KnowledgeBaseCrud {

        @Test
        @DisplayName("创建知识库：正常输入 → 返回 id+name")
        void shouldCreateKnowledgeBase() {
            Map<String, Object> result = ragService.createKnowledgeBase(TEST_EMAIL, "测试库", "描述");

            assertNotNull(result);
            assertEquals("测试库", result.get("name"));
            assertEquals("描述", result.get("description"));

            // 验证插入了正确数据
            ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
            verify(knowledgeBaseMapper).insert(captor.capture());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertEquals("测试库", captor.getValue().getName());
        }

        @Test
        @DisplayName("创建知识库：名称为空 → BadRequestException")
        void shouldRejectEmptyName() {
            assertThrows(BadRequestException.class, () -> ragService.createKnowledgeBase(TEST_EMAIL, "", "描述"));
            assertThrows(BadRequestException.class, () -> ragService.createKnowledgeBase(TEST_EMAIL, null, "描述"));
        }

        @Test
        @DisplayName("创建知识库：名称超长 → BadRequestException")
        void shouldRejectLongName() {
            String longName = "a".repeat(201);
            assertThrows(BadRequestException.class, () -> ragService.createKnowledgeBase(TEST_EMAIL, longName, ""));
        }

        @Test
        @DisplayName("列出知识库：返回用户的所有知识库及文档数")
        void shouldListKnowledgeBases() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb));
            when(documentMapper.selectCount(any())).thenReturn(3L);

            List<Map<String, Object>> result = ragService.listKnowledgeBases(TEST_EMAIL);

            assertEquals(1, result.size());
            assertEquals("测试库", result.get(0).get("name"));
            assertEquals(3L, result.get(0).get("documentCount"));
        }

        @Test
        @DisplayName("更新知识库：正常更新名称和描述")
        void shouldUpdateKnowledgeBase() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "旧名称", "旧描述");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            ragService.updateKnowledgeBase(TEST_EMAIL, 1L, "新名称", "新描述");

            verify(knowledgeBaseMapper).updateById(kb);
            assertEquals("新名称", kb.getName());
            assertEquals("新描述", kb.getDescription());
        }

        @Test
        @DisplayName("访问他人知识库：ForbiddenException")
        void shouldRejectOtherUserKb() {
            KnowledgeBase kb = new KnowledgeBase(999, "他人的库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            assertThrows(ForbiddenException.class, () -> ragService.getKnowledgeBase(TEST_EMAIL, 1L));
        }

        @Test
        @DisplayName("访问不存在的知识库：NotFoundException")
        void shouldRejectNonExistentKb() {
            when(knowledgeBaseMapper.selectById(99L)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> ragService.getKnowledgeBase(TEST_EMAIL, 99L));
        }
    }

    @Nested
    @DisplayName("文档管理")
    class DocumentManagement {

        @Test
        @DisplayName("列出文档：返回知识库中的所有文档")
        void shouldListDocuments() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "readme.md", "md", 1024L, "/tmp/readme.md");
            doc.setId(1L);
            when(documentMapper.selectList(any())).thenReturn(List.of(doc));

            List<Map<String, Object>> result = ragService.listDocuments(TEST_EMAIL, 1L);

            assertEquals(1, result.size());
            assertEquals("readme.md", result.get(0).get("filename"));
            assertEquals("PENDING", result.get(0).get("status"));
        }

        @Test
        @DisplayName("删除文档：软删除 → 设置 deleted_at")
        void shouldSoftDeleteDocument() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "test.txt", "txt", 100L, "/tmp/test.txt");
            doc.setId(2L);
            when(documentMapper.selectById(2L)).thenReturn(doc);

            ragService.deleteDocument(TEST_EMAIL, 1L, 2L);

            assertNotNull(doc.getDeletedAt());
            verify(documentMapper).updateById(doc);
        }

        @Test
        @DisplayName("删除不存在的文档：NotFoundException")
        void shouldRejectNonExistentDoc() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
            when(documentMapper.selectById(99L)).thenReturn(null);

            assertThrows(NotFoundException.class, () -> ragService.deleteDocument(TEST_EMAIL, 1L, 99L));
        }
    }

    @Nested
    @DisplayName("检索查询")
    class SearchTests {

        @Test
        @DisplayName("空查询：返回空列表")
        void shouldReturnEmptyForBlankQuery() {
            List<Map<String, Object>> result = ragService.searchChunks(1L, "", 5, 0.3);
            assertTrue(result.isEmpty());

            result = ragService.searchChunks(1L, null, 5, 0.3);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("retrieveContext：无结果时返回空字符串")
        void shouldReturnEmptyContext() {
            String context = ragService.retrieveContext(1L, "查询", 5, 0.9);
            assertEquals("", context);
        }
    }

    @Nested
    @DisplayName("回收站（知识库级别）")
    class KbRecycleBin {

        @Test
        @DisplayName("listDeletedKnowledgeBases：返回用户的软删除知识库及文档数量")
        void shouldListDeletedKnowledgeBases() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "已删除的库", "");
            kb.setId(1L);
            kb.setDeletedAt(java.time.LocalDateTime.now());
            when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb));
            when(documentMapper.selectCount(any())).thenReturn(2L);

            List<Map<String, Object>> result = ragService.listDeletedKnowledgeBases(TEST_EMAIL);

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).get("id"));
            assertEquals("已删除的库", result.get(0).get("name"));
            assertEquals(2L, result.get(0).get("documentCount"));
        }

        @Test
        @DisplayName("restoreKnowledgeBase：恢复知识库及所有文档并重新嵌入")
        void shouldRestoreKnowledgeBase() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "待恢复库", "");
            kb.setId(1L);
            kb.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            // 旗下有一个已删除文档
            KbDocument doc = new KbDocument(1L, "doc.pdf", "pdf", 100L, "/tmp/doc.pdf");
            doc.setId(10L);
            doc.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(documentMapper.selectList(any())).thenReturn(List.of(doc));

            // 文档有文本块
            KbChunk chunk = new KbChunk(10L, 0, "内容", 2);
            when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
            when(embeddingStore.removeByDocumentId(anyLong())).thenReturn(0);

            ragService.restoreKnowledgeBase(TEST_EMAIL, 1L);

            // 验证：KB 已恢复
            assertNull(kb.getDeletedAt());

            // 验证：文档已恢复
            assertNull(doc.getDeletedAt());

            // 验证：重新嵌入了文本块
            verify(embeddingModel).embed("内容");
            verify(embeddingStore).add(any(Embedding.class), any(TextSegment.class));
        }

        @Test
        @DisplayName("permanentlyDeleteKnowledgeBase：完整永久删除流程")
        void shouldPermanentlyDeleteKnowledgeBase() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "待永久删除", "");
            kb.setId(1L);
            kb.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "doc.pdf", "pdf", 100L,
                    "/nonexistent/doc.pdf");
            doc.setId(10L);
            when(documentMapper.selectList(any())).thenReturn(List.of(doc));
            when(embeddingStore.removeByDocumentId(anyLong())).thenReturn(0);

            ragService.permanentlyDeleteKnowledgeBase(TEST_EMAIL, 1L);

            // 验证：chunks 已删除
            verify(chunkMapper).delete(any());
            // 验证：文档已删除
            verify(documentMapper).delete(any());
            // 验证：知识库已删除
            verify(knowledgeBaseMapper).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("回收站（文档级别）")
    class DocRecycleBin {

        @Test
        @DisplayName("listDeletedDocuments：返回指定知识库的软删除文档")
        void shouldListDeletedDocuments() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "deleted.pdf", "pdf", 1024L, "/tmp/deleted.pdf");
            doc.setId(10L);
            doc.setDeletedAt(java.time.LocalDateTime.now());
            when(documentMapper.selectList(any())).thenReturn(List.of(doc));

            List<Map<String, Object>> result = ragService.listDeletedDocuments(TEST_EMAIL, 1L);

            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).get("id"));
            assertEquals("deleted.pdf", result.get(0).get("filename"));
        }

        @Test
        @DisplayName("restoreDocument：正常恢复文档并重新嵌入")
        void shouldRestoreDocument() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "restore.pdf", "pdf", 500L, "/tmp/restore.pdf");
            doc.setId(10L);
            doc.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(documentMapper.selectById(10L)).thenReturn(doc);

            KbChunk chunk = new KbChunk(10L, 0, "测试内容", 4);
            chunk.setId(100L);
            when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
            when(embeddingStore.removeByDocumentId(10L)).thenReturn(0);

            ragService.restoreDocument(TEST_EMAIL, 10L);

            assertNull(doc.getDeletedAt());
            verify(documentMapper).update(isNull(), any());
            verify(embeddingModel).embed("测试内容");
            verify(embeddingStore).add(any(Embedding.class), any(TextSegment.class));
        }

        @Test
        @DisplayName("restoreDocument：KB也处于删除状态时自动恢复KB")
        void shouldAutoRestoreParentKb() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "已删除的库", "");
            kb.setId(1L);
            kb.setDeletedAt(java.time.LocalDateTime.now().minusDays(2));
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "doc.pdf", "pdf", 100L, "/tmp/doc.pdf");
            doc.setId(10L);
            doc.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(documentMapper.selectById(10L)).thenReturn(doc);
            when(chunkMapper.selectList(any())).thenReturn(List.of());
            when(embeddingStore.removeByDocumentId(10L)).thenReturn(0);

            ragService.restoreDocument(TEST_EMAIL, 10L);

            assertNull(kb.getDeletedAt());
            assertNull(doc.getDeletedAt());
        }

        @Test
        @DisplayName("permanentlyDeleteDocument：完整永久删除流程")
        void shouldPermanentlyDeleteDocument() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc = new KbDocument(1L, "delete.pdf", "pdf", 100L,
                    "/nonexistent/path/delete.pdf");
            doc.setId(10L);
            doc.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(documentMapper.selectById(10L)).thenReturn(doc);
            when(embeddingStore.removeByDocumentId(10L)).thenReturn(3);

            ragService.permanentlyDeleteDocument(TEST_EMAIL, 10L);

            verify(embeddingStore).removeByDocumentId(10L);
            verify(chunkMapper).delete(any());
            verify(documentMapper).deleteById(10L);
        }

        @Test
        @DisplayName("emptyDocumentRecycleBin：清空指定知识库的文档回收站")
        void shouldEmptyDocumentRecycleBin() {
            KnowledgeBase kb = new KnowledgeBase(USER_ID, "测试库", "");
            kb.setId(1L);
            when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

            KbDocument doc1 = new KbDocument(1L, "doc1.pdf", "pdf", 100L,
                    "/nonexistent/doc1.pdf");
            doc1.setId(10L);
            doc1.setDeletedAt(java.time.LocalDateTime.now().minusDays(2));
            KbDocument doc2 = new KbDocument(1L, "doc2.pdf", "pdf", 200L,
                    "/nonexistent/doc2.pdf");
            doc2.setId(20L);
            doc2.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
            when(documentMapper.selectList(any())).thenReturn(List.of(doc1, doc2));
            when(documentMapper.selectById(10L)).thenReturn(doc1);
            when(documentMapper.selectById(20L)).thenReturn(doc2);
            when(embeddingStore.removeByDocumentId(anyLong())).thenReturn(0);

            int count = ragService.emptyDocumentRecycleBin(TEST_EMAIL, 1L);

            assertEquals(2, count);
            verify(documentMapper).deleteById(10L);
            verify(documentMapper).deleteById(20L);
        }
    }
}
