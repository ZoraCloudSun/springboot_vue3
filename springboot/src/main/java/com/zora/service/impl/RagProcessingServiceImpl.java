package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zora.entity.KbChunk;
import com.zora.entity.KbDocument;
import com.zora.mapper.KbChunkMapper;
import com.zora.mapper.KbDocumentMapper;
import com.zora.mapper.KnowledgeBaseMapper;
import com.zora.service.RagProcessingService;
import com.zora.utils.TextSplitterUtil;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RAG 文档处理服务实现
 * <p>
 * 实现文档处理的完整管道：解析 → 分块 → 嵌入 → 存储。
 * 支持应用启动时从 MySQL 重建向量索引。
 * </p>
 *
 * <h3>处理管道流程</h3>
 * <ol>
 * <li>使用 Apache Tika 解析文档文件（支持 PDF/DOCX/DOC/TXT/MD 等）</li>
 * <li>递归分割为文本块（最大 800 字符，重叠 100 字符）</li>
 * <li>调用 EmbeddingModel 将每个文本块转为向量</li>
 * <li>向量存入 SimpleEmbeddingStore，文本块存入 kb_chunk 表</li>
 * <li>更新文档状态为 COMPLETED</li>
 * </ol>
 */
@Service
public class RagProcessingServiceImpl implements RagProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RagProcessingServiceImpl.class);

    /** Apache Tika 实例（线程安全，可复用） */
    private static final Tika TIKA = new Tika();

    /**
     * 匹配 Emoji 和其他 Unicode 补充平面字符（U+10000 以上）
     * 这类字符需要 4 字节 UTF-8 编码，部分 Embedding API 不支持
     */
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{10000}-\\x{10FFFF}\\x{FE00}-\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]");

    /**
     * 清理文本以适配 Embedding API 的限制
     * <p>
     * 处理逻辑：
     * 1. 移除 emoji 和 Unicode 补充平面字符（部分 API 不支持 4 字节 UTF-8）
     * 2. 截断超长文本（BGE 模型 512 token 限制，中文约 1.5~2 token/字，安全上限 400 字）
     * </p>
     * <p>
     * 原始文本仍保留在 kb_chunk 表中用于展示，
     * 仅在调用 Embedding API 时使用清理后的文本。
     * </p>
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String sanitizeForEmbedding(String text) {
        if (text == null) return null;
        // 1. 移除 emoji 和补充平面字符，保留 BMP 内的所有字符（包括中文、日韩文等）
        String cleaned = EMOJI_PATTERN.matcher(text).replaceAll("").trim();
        // 2. 截断超长文本（BGE 模型 512 token ≈ 300~400 中文字）
        if (cleaned.length() > 400) {
            cleaned = cleaned.substring(0, 400);
        }
        return cleaned;
    }

    @Resource
    private KbDocumentMapper documentMapper;

    @Resource
    private KbChunkMapper chunkMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private SimpleEmbeddingStore embeddingStore;

    @Value("${rag.document.max-chunk-size:300}")
    private int maxChunkSize;

    @Value("${rag.document.max-chunk-overlap:50}")
    private int maxChunkOverlap;

    /**
     * 应用启动时从 MySQL 重建内存向量索引
     * <p>
     * 遍历所有状态为 COMPLETED 且未删除的文档，加载其文本块，
     * 重新嵌入并存入向量存储。
     * </p>
     */
    @Override
    @PostConstruct
    public void rebuildEmbeddingStore() {
        log.info("开始从 MySQL 重建向量索引...");
        long startTime = System.currentTimeMillis();

        // 查询所有已完成且未删除的文档
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getStatus, KbDocument.STATUS_COMPLETED)
                .isNull(KbDocument::getDeletedAt);
        List<KbDocument> completedDocs = documentMapper.selectList(docWrapper);

        if (completedDocs.isEmpty()) {
            log.info("向量索引重建完成：无已完成文档需要恢复");
            return;
        }

        int totalChunks = 0;
        for (KbDocument doc : completedDocs) {
            try {
                // 加载该文档的所有文本块（按序号排序）
                LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
                chunkWrapper.eq(KbChunk::getDocumentId, doc.getId())
                        .orderByAsc(KbChunk::getChunkIndex);
                List<KbChunk> chunks = chunkMapper.selectList(chunkWrapper);

                if (chunks.isEmpty()) {
                    log.warn("文档 id={} 状态为 COMPLETED 但无文本块，跳过", doc.getId());
                    continue;
                }

                // 批量嵌入并存入向量库
                for (KbChunk chunk : chunks) {
                    try {
                        // 清理 emoji 等特殊字符后再发送给 Embedding API
                        String embedText = sanitizeForEmbedding(chunk.getContent());
                        if (embedText.isBlank()) {
                            log.warn("文本块清理后为空，跳过嵌入: doc={}, chunk={}",
                                    doc.getFilename(), chunk.getChunkIndex());
                            continue;
                        }
                        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(embedText)
                                .content();
                        Metadata metadata = new Metadata()
                                .put("document_id", String.valueOf(doc.getId()))
                                .put("kb_id", String.valueOf(doc.getKbId()))
                                .put("filename", doc.getFilename())
                                .put("chunk_index", String.valueOf(chunk.getChunkIndex()));
                        TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                        embeddingStore.add(embedding, segment);
                        totalChunks++;
                    } catch (Exception e) {
                        log.error("嵌入文本块失败: doc={}, chunk={}, error={}",
                                doc.getFilename(), chunk.getChunkIndex(), e.getMessage());
                    }
                }
                log.debug("已恢复文档: {} ({} 个文本块)", doc.getFilename(), chunks.size());
            } catch (Exception e) {
                log.error("恢复文档 {} 时出错: {}", doc.getFilename(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("向量索引重建完成：恢复了 {} 个文档的 {} 个文本块，耗时 {}ms",
                completedDocs.size(), totalChunks, elapsed);
    }

    /**
     * 异步处理文档
     */
    @Override
    @Async
    public void processDocument(Long documentId) {
        KbDocument kbDoc = documentMapper.selectById(documentId);
        if (kbDoc == null) {
            log.error("文档 id={} 不存在，无法处理", documentId);
            return;
        }

        // 更新状态为 PROCESSING
        kbDoc.setStatus(KbDocument.STATUS_PROCESSING);
        documentMapper.updateById(kbDoc);

        try {
            log.info("开始处理文档: {} (id={})", kbDoc.getFilename(), documentId);

            // 1. 使用 Apache Tika 提取文本内容
            Path filePath = Paths.get(kbDoc.getFilePath());
            if (!Files.exists(filePath)) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            String fullText;
            try (InputStream is = Files.newInputStream(filePath)) {
                fullText = TIKA.parseToString(is);
            }

            if (fullText == null || fullText.isBlank()) {
                throw new RuntimeException(
                    "文档解析失败：未能提取到文字内容。"
                    + "该文件可能为扫描件或图片型 PDF（无文字层），"
                    + "当前暂不支持 OCR 识别。"
                    + "请上传文本型 PDF（如 Word 导出、网页打印生成的 PDF）"
                    + "或 DOCX/DOC/TXT/MD 格式的文档。");
            }
            log.debug("文档解析完成，总字符数: {}", fullText.length());

            // 2. 分块：使用自实现的递归文本分割器
            List<String> chunkTexts = TextSplitterUtil.split(fullText, maxChunkSize, maxChunkOverlap);
            log.debug("文档分块完成，共 {} 个文本块", chunkTexts.size());

            // 3. 逐块嵌入并存储
            int chunkIndex = 0;
            for (String chunkText : chunkTexts) {
                if (chunkText.isBlank())
                    continue;

                // 保存到 kb_chunk 表
                KbChunk chunk = new KbChunk(
                        documentId, chunkIndex, chunkText, chunkText.length());
                chunkMapper.insert(chunk);

                // 嵌入并存入向量库
                try {
                    // 清理 emoji 等特殊字符后再发送给 Embedding API（原始文本已存入 kb_chunk）
                    String embedText = sanitizeForEmbedding(chunkText);
                    if (embedText.isBlank()) {
                        log.warn("文本块清理后为空（仅含特殊字符），跳过嵌入: doc={}, chunk={}",
                                kbDoc.getFilename(), chunkIndex);
                        continue;
                    }
                    dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(embedText).content();

                    Metadata metadata = new Metadata()
                            .put("document_id", String.valueOf(documentId))
                            .put("kb_id", String.valueOf(kbDoc.getKbId()))
                            .put("filename", kbDoc.getFilename())
                            .put("chunk_index", String.valueOf(chunkIndex));
                    TextSegment segment = TextSegment.from(chunkText, metadata);
                    embeddingStore.add(embedding, segment);
                } catch (Exception e) {
                    log.error("嵌入文本块失败: doc={}, chunk={}, error={}",
                            kbDoc.getFilename(), chunkIndex, e.getMessage());
                    throw e;
                }

                chunkIndex++;
            }

            // 4. 更新文档状态为 COMPLETED
            kbDoc.setStatus(KbDocument.STATUS_COMPLETED);
            kbDoc.setChunkCount(chunkIndex);
            kbDoc.setErrorMessage(null);
            documentMapper.updateById(kbDoc);

            log.info("文档处理完成: {}, 共 {} 个文本块", kbDoc.getFilename(), chunkIndex);

        } catch (Exception e) {
            log.error("文档处理失败: {} (id={}), error={}",
                    kbDoc.getFilename(), documentId, e.getMessage(), e);
            // 更新状态为 FAILED
            kbDoc.setStatus(KbDocument.STATUS_FAILED);
            kbDoc.setErrorMessage(e.getMessage() != null ? e.getMessage() : "未知错误");
            documentMapper.updateById(kbDoc);
        }
    }
}
