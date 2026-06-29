package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zora.entity.*;
import com.zora.exception.BadRequestException;
import com.zora.exception.ForbiddenException;
import com.zora.exception.NotFoundException;
import com.zora.mapper.*;
import com.zora.service.RagProcessingService;
import com.zora.service.RagService;
import com.zora.utils.UserContext;
import com.zora.utils.FileTypeUtil;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务实现
 * <p>
 * 提供知识库 CRUD、文档上传管理、知识检索的完整业务逻辑。
 * 所有操作均验证所有权（知识库和文档都属于当前用户）。
 * </p>
 *
 * <h3>文件存储</h3>
 * 上传的文件保存到配置的本地目录 {@code rag.document.upload-dir}，
 * 文件名格式：{@code {timestamp}_{原始文件名}} 防止冲突。
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    /** 匹配 Emoji 和 Unicode 补充平面字符（U+10000 以上），部分 Embedding API 不支持 */
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{10000}-\\x{10FFFF}\\x{FE00}-\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]");

    /** 清理 emoji 等特殊字符并截断超长文本后再发送给 Embedding API */
    private String sanitizeForEmbedding(String text) {
        if (text == null) return null;
        String cleaned = EMOJI_PATTERN.matcher(text).replaceAll("").trim();
        // 截断超长文本（BGE 模型 512 token ≈ 300~400 中文字）
        if (cleaned.length() > 400) {
            cleaned = cleaned.substring(0, 400);
        }
        return cleaned;
    }

    @Resource
    private UserContext userContext;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbDocumentMapper documentMapper;

    @Resource
    private KbChunkMapper chunkMapper;

    @Resource
    private RagProcessingService ragProcessingService;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private SimpleEmbeddingStore embeddingStore;

    @Value("${rag.document.upload-dir:./uploads/rag}")
    private String uploadDir;

    @Value("${rag.document.max-size:10485760}")
    private long maxFileSize;

    @Value("${rag.document.max-retrieve-results:5}")
    private int maxRetrieveResults;

    @Value("${rag.document.min-relevance-score:0.3}")
    private double minRelevanceScore;

    // ==================== 知识库 CRUD ====================

    @Override
    public Map<String, Object> createKnowledgeBase(String email, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("知识库名称不能为空");
        }
        if (name.length() > 200) {
            throw new BadRequestException("知识库名称不能超过 200 个字符");
        }

        Integer userId = userContext.getUserId();
        KnowledgeBase kb = new KnowledgeBase(userId, name.trim(),
                description != null ? description.trim() : "");
        knowledgeBaseMapper.insert(kb);

        log.info("用户 {} 创建知识库: {} (id={})", email, kb.getName(), kb.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", kb.getId());
        result.put("name", kb.getName());
        result.put("description", kb.getDescription());
        result.put("createdAt", kb.getCreatedAt());
        return result;
    }

    @Override
    public List<Map<String, Object>> listKnowledgeBases(String email) {
        Integer userId = userContext.getUserId();

        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
                .isNull(KnowledgeBase::getDeletedAt)
                .orderByDesc(KnowledgeBase::getUpdatedAt);
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectList(wrapper);

        return kbs.stream().map(kb -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", kb.getId());
            map.put("name", kb.getName());
            map.put("description", kb.getDescription());
            // 统计文档数量（仅已完成的）
            LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
            docWrapper.eq(KbDocument::getKbId, kb.getId())
                    .isNull(KbDocument::getDeletedAt)
                    .eq(KbDocument::getStatus, KbDocument.STATUS_COMPLETED);
            map.put("documentCount", documentMapper.selectCount(docWrapper));
            map.put("createdAt", kb.getCreatedAt());
            map.put("updatedAt", kb.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getKnowledgeBase(String email, Long kbId) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);
        List<Map<String, Object>> documents = listDocuments(email, kbId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", kb.getId());
        result.put("name", kb.getName());
        result.put("description", kb.getDescription());
        result.put("createdAt", kb.getCreatedAt());
        result.put("updatedAt", kb.getUpdatedAt());
        result.put("documents", documents);
        return result;
    }

    @Override
    public void updateKnowledgeBase(String email, Long kbId, String name, String description) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        if (name != null && !name.isBlank()) {
            if (name.length() > 200) {
                throw new BadRequestException("知识库名称不能超过 200 个字符");
            }
            kb.setName(name.trim());
        }
        if (description != null) {
            kb.setDescription(description.trim());
        }
        knowledgeBaseMapper.updateById(kb);
        log.info("用户 {} 更新知识库 id={}", email, kbId);
    }

    @Override
    public void deleteKnowledgeBase(String email, Long kbId) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        LocalDateTime now = LocalDateTime.now();
        // 软删除知识库
        kb.setDeletedAt(now);
        knowledgeBaseMapper.updateById(kb);

        // 软删除该知识库下所有未删除的文档
        LambdaUpdateWrapper<KbDocument> docUpdate = new LambdaUpdateWrapper<>();
        docUpdate.eq(KbDocument::getKbId, kbId)
                .isNull(KbDocument::getDeletedAt)
                .set(KbDocument::getDeletedAt, now);
        documentMapper.update(null, docUpdate);

        log.info("用户 {} 删除知识库: {} (id={})", email, kb.getName(), kbId);
    }

    // ==================== 文档管理 ====================

    @Override
    public Map<String, Object> uploadDocument(String email, Long kbId, MultipartFile file) {
        // 1. 验证知识库所有权
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        // 2. 验证文件
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要上传的文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("文件名为空");
        }

        // 3. 检测文件类型
        String fileType = FileTypeUtil.detectFileType(originalFilename);

        // 4. 校验文件大小
        long fileSize = file.getSize();
        FileTypeUtil.checkFileSize(fileSize, maxFileSize);

        // 5. 确保上传目录存在（转为绝对路径，防止 Tomcat 内部相对路径解析不一致导致
        //    目录创建在一处、文件写入到另一处的问题）
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", uploadPath, e);
            throw new BadRequestException("文件上传失败，请联系管理员");
        }

        // 6. 保存文件到磁盘（文件名加时间戳防止冲突）
        String storedFilename = System.currentTimeMillis() + "_" + originalFilename;
        Path destPath = uploadPath.resolve(storedFilename);
        try {
            file.transferTo(destPath.toFile());
            log.info("文件已保存: {}", destPath);
        } catch (IOException e) {
            log.error("保存文件失败: {}", destPath, e);
            throw new BadRequestException("文件保存失败，请重试");
        }

        // 7. 创建文档数据库记录
        KbDocument document = new KbDocument(kbId, originalFilename, fileType, fileSize,
                destPath.toAbsolutePath().toString());
        documentMapper.insert(document);

        // 8. 异步触发文档处理
        ragProcessingService.processDocument(document.getId());

        log.info("用户 {} 上传文档: {} 到知识库 {} (id={})",
                email, originalFilename, kb.getName(), kbId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", document.getId());
        result.put("filename", document.getFilename());
        result.put("fileType", document.getFileType());
        result.put("fileSize", document.getFileSize());
        result.put("status", document.getStatus());
        result.put("createdAt", document.getCreatedAt());
        return result;
    }

    @Override
    public List<Map<String, Object>> listDocuments(String email, Long kbId) {
        // 验证知识库所有权
        findKnowledgeBase(kbId, email);

        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId)
                .isNull(KbDocument::getDeletedAt)
                .orderByDesc(KbDocument::getCreatedAt);
        List<KbDocument> documents = documentMapper.selectList(wrapper);

        return documents.stream().map(doc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", doc.getId());
            map.put("filename", doc.getFilename());
            map.put("fileType", doc.getFileType());
            map.put("fileSize", doc.getFileSize());
            map.put("status", doc.getStatus());
            map.put("errorMessage", doc.getErrorMessage());
            map.put("chunkCount", doc.getChunkCount());
            map.put("createdAt", doc.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteDocument(String email, Long kbId, Long docId) {
        // 验证知识库所有权
        findKnowledgeBase(kbId, email);

        // 验证文档存在于该知识库中
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new NotFoundException("文档不存在");
        }
        if (doc.getDeletedAt() != null) {
            throw new BadRequestException("文档已被删除");
        }

        // 软删除文档
        LocalDateTime now = LocalDateTime.now();
        doc.setDeletedAt(now);
        documentMapper.updateById(doc);

        log.info("用户 {} 删除文档: {} (id={})", email, doc.getFilename(), docId);
    }

    // ==================== 检索查询 ====================

    @Override
    public List<Map<String, Object>> searchChunks(Long kbId, String query, int maxResults, double minScore) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 嵌入查询文本（清理 emoji 等特殊字符）
        String embedQuery = sanitizeForEmbedding(query);
        if (embedQuery.isBlank()) {
            return Collections.emptyList();
        }
        Embedding queryEmbedding = embeddingModel.embed(embedQuery).content();

        // 在向量库中搜索
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults > 0 ? maxResults : maxRetrieveResults)
                        .minScore(minScore > 0 ? minScore : minRelevanceScore)
                        .build());

        // 过滤：只保留属于指定知识库的块
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            if (segment == null || segment.metadata() == null)
                continue;

            // 使用 metadata().toMap() 获取自定义字段
            Map<String, ?> metaMap = segment.metadata().toMap();
            String chunkKbId = (String) metaMap.get("kb_id");
            if (chunkKbId != null && String.valueOf(kbId).equals(chunkKbId)) {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("content", segment.text());
                chunk.put("filename", metaMap.get("filename"));
                chunk.put("chunkIndex", metaMap.get("chunk_index"));
                chunk.put("score", match.score());
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    @Override
    public String retrieveContext(Long kbId, String query, int maxResults, double minScore) {
        List<Map<String, Object>> chunks = searchChunks(kbId, query, maxResults, minScore);

        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (Map<String, Object> chunk : chunks) {
            String filename = (String) chunk.getOrDefault("filename", "未知文件");
            String chunkIndex = (String) chunk.getOrDefault("chunkIndex", "0");
            String content = (String) chunk.get("content");

            // 将块序号转换为人类可读的段号（从 0 开始 → 第 1 段）
            int segmentNum = 1;
            try {
                segmentNum = Integer.parseInt(chunkIndex) + 1;
            } catch (NumberFormatException ignored) {
            }

            context.append("[来源: ").append(filename)
                    .append(" (第").append(segmentNum).append("段)]\n")
                    .append(content).append("\n\n");
        }

        return context.toString().trim();
    }

    // ==================== 定期清理 ====================

    @Override
    public int cleanupOldDeletedRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int totalDeleted = 0;

        // 查找超过 30 天的软删除知识库
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.isNotNull(KnowledgeBase::getDeletedAt)
                .le(KnowledgeBase::getDeletedAt, threshold);
        List<KnowledgeBase> oldKbs = knowledgeBaseMapper.selectList(kbWrapper);

        for (KnowledgeBase kb : oldKbs) {
            // 物理删除文本块（通过文档关联）
            LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
            docWrapper.eq(KbDocument::getKbId, kb.getId());
            List<KbDocument> docs = documentMapper.selectList(docWrapper);
            for (KbDocument doc : docs) {
                LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
                chunkWrapper.eq(KbChunk::getDocumentId, doc.getId());
                chunkMapper.delete(chunkWrapper);
            }
            // 物理删除文档
            documentMapper.delete(docWrapper);
            // 物理删除知识库
            knowledgeBaseMapper.deleteById(kb.getId());
            totalDeleted++;
        }

        if (totalDeleted > 0) {
            log.info("定期清理：物理删除了 {} 个超过 30 天的软删除知识库", totalDeleted);
        }
        return totalDeleted;
    }

    // ==================== 回收站（知识库级别）====================

    @Override
    public List<Map<String, Object>> listDeletedKnowledgeBases(String email) {
        Integer userId = userContext.getUserId();

        // 查询当前用户所有已软删除的知识库
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
                .isNotNull(KnowledgeBase::getDeletedAt)
                .orderByDesc(KnowledgeBase::getDeletedAt);
        List<KnowledgeBase> deletedKbs = knowledgeBaseMapper.selectList(wrapper);

        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeBase kb : deletedKbs) {
            // 统计该知识库下已软删除的文档数量
            LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
            docWrapper.eq(KbDocument::getKbId, kb.getId())
                    .isNotNull(KbDocument::getDeletedAt);
            long docCount = documentMapper.selectCount(docWrapper);

            Map<String, Object> item = new HashMap<>();
            item.put("id", kb.getId());
            item.put("name", kb.getName());
            item.put("description", kb.getDescription());
            item.put("documentCount", docCount);
            item.put("deletedAt", kb.getDeletedAt());
            item.put("createdAt", kb.getCreatedAt());
            results.add(item);
        }

        return results;
    }

    @Override
    public void restoreKnowledgeBase(String email, Long kbId) {
        // 1. 查找知识库并验证已软删除
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new NotFoundException("知识库不存在");
        }
        if (kb.getDeletedAt() == null) {
            throw new BadRequestException("知识库未被删除，无需恢复");
        }

        // 2. 验证所有权
        Integer userId = userContext.getUserId();
        if (!kb.getUserId().equals(userId)) {
            throw new ForbiddenException("无权恢复此知识库");
        }

        // 3. 恢复知识库本身（使用 UpdateWrapper 显式设置 null，避免 MyBatis-Plus updateById 忽略 null 字段）
        UpdateWrapper<KnowledgeBase> kbUpdate = new UpdateWrapper<>();
        kbUpdate.eq("id", kbId).set("deleted_at", null);
        knowledgeBaseMapper.update(null, kbUpdate);
        // 同步 Java 对象状态，防止后续 updateById 把旧值写回 DB
        kb.setDeletedAt(null);

        // 4. 恢复该知识库下所有已软删除的文档，并重新嵌入向量
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getKbId, kbId)
                .isNotNull(KbDocument::getDeletedAt);
        List<KbDocument> deletedDocs = documentMapper.selectList(docWrapper);

        int totalEmbedded = 0;
        for (KbDocument doc : deletedDocs) {
            // 清除残留向量
            embeddingStore.removeByDocumentId(doc.getId());
            // 恢复文档（使用 UpdateWrapper 显式设置 null）
            UpdateWrapper<KbDocument> restoreUpdate = new UpdateWrapper<>();
            restoreUpdate.eq("id", doc.getId()).set("deleted_at", null);
            documentMapper.update(null, restoreUpdate);
            doc.setDeletedAt(null); // 同步 Java 对象

            // 重新嵌入该文档的文本块
            LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
            chunkWrapper.eq(KbChunk::getDocumentId, doc.getId())
                    .orderByAsc(KbChunk::getChunkIndex);
            List<KbChunk> chunks = chunkMapper.selectList(chunkWrapper);

            for (KbChunk chunk : chunks) {
                try {
                    String embedText = sanitizeForEmbedding(chunk.getContent());
                    if (embedText.isBlank()) continue;
                    Embedding embedding = embeddingModel.embed(embedText).content();
                    Metadata metadata = new Metadata()
                            .put("document_id", String.valueOf(doc.getId()))
                            .put("kb_id", String.valueOf(kbId))
                            .put("filename", doc.getFilename())
                            .put("chunk_index", String.valueOf(chunk.getChunkIndex()));
                    TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                    embeddingStore.add(embedding, segment);
                    totalEmbedded++;
                } catch (Exception e) {
                    log.error("恢复知识库时嵌入文本块失败: doc={}, chunk={}, error={}",
                            doc.getFilename(), chunk.getChunkIndex(), e.getMessage());
                }
            }
        }

        // 5. 刷新 KB 更新时间
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);

        log.info("用户 {} 恢复知识库: {} (id={}), 恢复 {} 个文档, 重新嵌入 {} 个文本块",
                email, kb.getName(), kbId, deletedDocs.size(), totalEmbedded);
    }

    @Override
    public void permanentlyDeleteKnowledgeBase(String email, Long kbId) {
        // 1. 查找知识库并验证已软删除
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new NotFoundException("知识库不存在");
        }
        if (kb.getDeletedAt() == null) {
            throw new BadRequestException("只能永久删除回收站中的知识库");
        }

        // 2. 验证所有权
        Integer userId = userContext.getUserId();
        if (!kb.getUserId().equals(userId)) {
            throw new ForbiddenException("无权删除此知识库");
        }

        String kbName = kb.getName();

        // 3. 加载该 KB 下所有文档（忽略 deleted_at 状态，全部清理）
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getKbId, kbId);
        List<KbDocument> docs = documentMapper.selectList(docWrapper);

        // 4. 逐个清理文档（文件 + 向量 + chunks）
        for (KbDocument doc : docs) {
            // 删除磁盘文件
            try {
                Files.deleteIfExists(Path.of(doc.getFilePath()));
            } catch (IOException e) {
                log.warn("删除磁盘文件失败: {}", doc.getFilePath());
            }
            // 移除向量
            embeddingStore.removeByDocumentId(doc.getId());
            // 物理删除 chunks
            LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
            chunkWrapper.eq(KbChunk::getDocumentId, doc.getId());
            chunkMapper.delete(chunkWrapper);
        }

        // 5. 物理删除所有文档记录
        if (!docs.isEmpty()) {
            documentMapper.delete(docWrapper);
        }

        // 6. 物理删除知识库记录
        knowledgeBaseMapper.deleteById(kbId);

        log.info("用户 {} 永久删除知识库: {} (id={}), 共删除 {} 个文档",
                email, kbName, kbId, docs.size());
    }

    // ==================== 回收站（文档级别，按知识库）====================

    @Override
    public List<Map<String, Object>> listDeletedDocuments(String email, Long kbId) {
        // 验证知识库所有权（包括已软删除的 KB）
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null) {
            Integer userId = userContext.getUserId();
            if (!kb.getUserId().equals(userId)) {
                throw new ForbiddenException("无权访问此知识库");
            }
        }

        // 查询该知识库下所有已软删除的文档
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId)
                .isNotNull(KbDocument::getDeletedAt)
                .orderByDesc(KbDocument::getDeletedAt);
        List<KbDocument> deletedDocs = documentMapper.selectList(wrapper);

        return deletedDocs.stream().map(doc -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", doc.getId());
            item.put("kbId", doc.getKbId());
            item.put("filename", doc.getFilename());
            item.put("fileType", doc.getFileType());
            item.put("fileSize", doc.getFileSize());
            item.put("status", doc.getStatus());
            item.put("chunkCount", doc.getChunkCount());
            item.put("deletedAt", doc.getDeletedAt());
            item.put("createdAt", doc.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public void restoreDocument(String email, Long docId) {
        // 1. 查找文档并验证已软删除
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new NotFoundException("文档不存在");
        }
        if (doc.getDeletedAt() == null) {
            throw new BadRequestException("文档未被删除，无需恢复");
        }

        // 2. 加载知识库并验证所有权
        KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
        if (kb != null) {
            Integer userId = userContext.getUserId();
            if (!kb.getUserId().equals(userId)) {
                throw new ForbiddenException("无权恢复此文档");
            }

            // 3. 若知识库也被软删除，自动恢复知识库（使用 UpdateWrapper 防止 MyBatis-Plus 忽略 null）
            if (kb.getDeletedAt() != null) {
                UpdateWrapper<KnowledgeBase> autoKbUpdate = new UpdateWrapper<>();
                autoKbUpdate.eq("id", kb.getId()).set("deleted_at", null);
                knowledgeBaseMapper.update(null, autoKbUpdate);
                kb.setDeletedAt(null); // 同步 Java 对象状态
                log.info("自动恢复知识库: {} (id={})", kb.getName(), kb.getId());
            }
        }

        // 4. 清除可能残留的向量
        int removed = embeddingStore.removeByDocumentId(docId);
        if (removed > 0) {
            log.debug("恢复文档前清除残留向量: docId={}, count={}", docId, removed);
        }

        // 5. 恢复文档（使用 UpdateWrapper 显式设置 null，避免 MyBatis-Plus 忽略 null 字段）
        UpdateWrapper<KbDocument> restoreUpdate = new UpdateWrapper<>();
        restoreUpdate.eq("id", docId).set("deleted_at", null);
        documentMapper.update(null, restoreUpdate);
        doc.setDeletedAt(null); // 同步 Java 对象

        // 6. 重新嵌入文本块到向量库
        LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(KbChunk::getDocumentId, docId)
                .orderByAsc(KbChunk::getChunkIndex);
        List<KbChunk> chunks = chunkMapper.selectList(chunkWrapper);

        int embeddedCount = 0;
        for (KbChunk chunk : chunks) {
            try {
                String embedText = sanitizeForEmbedding(chunk.getContent());
                if (embedText.isBlank()) continue;
                Embedding embedding = embeddingModel.embed(embedText).content();
                Metadata metadata = new Metadata()
                        .put("document_id", String.valueOf(doc.getId()))
                        .put("kb_id", String.valueOf(doc.getKbId()))
                        .put("filename", doc.getFilename())
                        .put("chunk_index", String.valueOf(chunk.getChunkIndex()));
                TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                embeddingStore.add(embedding, segment);
                embeddedCount++;
            } catch (Exception e) {
                log.error("恢复文档时嵌入文本块失败: doc={}, chunk={}, error={}",
                        doc.getFilename(), chunk.getChunkIndex(), e.getMessage());
            }
        }

        // 7. 更新 KB 更新时间
        if (kb != null) {
            kb.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(kb);
        }

        log.info("用户 {} 恢复文档: {} (id={}), 重新嵌入 {} 个文本块",
                email, doc.getFilename(), docId, embeddedCount);
    }

    @Override
    public void permanentlyDeleteDocument(String email, Long docId) {
        // 1. 查找文档并验证已软删除
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new NotFoundException("文档不存在");
        }
        if (doc.getDeletedAt() == null) {
            throw new BadRequestException("只能永久删除回收站中的文档");
        }

        // 2. 验证所有权
        KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
        if (kb != null) {
            Integer userId = userContext.getUserId();
            if (!kb.getUserId().equals(userId)) {
                throw new ForbiddenException("无权删除此文档");
            }
        }

        String filename = doc.getFilename();

        // 3. 删除磁盘文件
        try {
            Path filePath = Path.of(doc.getFilePath());
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("已删除磁盘文件: {}", filePath);
            } else {
                log.warn("磁盘文件不存在: {}", filePath);
            }
        } catch (IOException e) {
            log.error("删除磁盘文件失败: doc={}, path={}, error={}",
                    filename, doc.getFilePath(), e.getMessage());
        }

        // 4. 从向量库移除向量
        int removed = embeddingStore.removeByDocumentId(docId);
        log.debug("从向量库移除 {} 个向量: docId={}", removed, docId);

        // 5. 物理删除文本块
        LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(KbChunk::getDocumentId, docId);
        chunkMapper.delete(chunkWrapper);

        // 6. 物理删除文档记录
        documentMapper.deleteById(docId);

        log.info("用户 {} 永久删除文档: {} (id={}), 从向量库移除 {} 个向量",
                email, filename, docId, removed);
    }

    @Override
    public int emptyDocumentRecycleBin(String email, Long kbId) {
        // 验证所有权
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null) {
            Integer userId = userContext.getUserId();
            if (!kb.getUserId().equals(userId)) {
                throw new ForbiddenException("无权操作此知识库");
            }
        }

        List<Map<String, Object>> items = listDeletedDocuments(email, kbId);

        if (items.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (Map<String, Object> item : items) {
            Long docId = ((Number) item.get("id")).longValue();
            try {
                permanentlyDeleteDocument(email, docId);
                successCount++;
            } catch (Exception e) {
                log.error("清空文档回收站时失败: kbId={}, docId={}, error={}", kbId, docId, e.getMessage());
            }
        }

        log.info("用户 {} 清空知识库 id={} 的文档回收站：成功 {} 个，失败 {} 个",
                email, kbId, successCount, items.size() - successCount);
        return successCount;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查找知识库并验证所有权
     */
    private KnowledgeBase findKnowledgeBase(Long kbId, String email) {
        Integer userId = userContext.getUserId();
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeletedAt() != null) {
            throw new NotFoundException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问此知识库");
        }
        return kb;
    }
}
