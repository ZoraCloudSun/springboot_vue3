package com.zora.service.impl;

import com.zora.entity.dto.SearchResult;
import com.zora.exception.BadRequestException;
import com.zora.mapper.ChatMessageMapper;
import com.zora.service.SearchService;
import com.zora.utils.UserContext;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 全文搜索服务实现
 * <p>
 * Phase 4 全文搜索引擎 —— 基于 MySQL FULLTEXT + ngram parser 实现。
 * 核心流程：用户身份解析 → 关键词校验与转义 → MySQL 全文搜索 → Java 层关键词高亮 → 分页包装。
 * </p>
 *
 * <h3>安全考量</h3>
 * <ul>
 * <li>关键词转义：防止 MySQL FULLTEXT boolean mode 特殊字符被注入利用</li>
 * <li>长度限制：最大 200 字符，防止恶意超长查询</li>
 * <li>数据隔离：仅搜索当前用户未删除的消息和对话</li>
 * <li>高亮安全：使用 Pattern.quote 防止正则注入，前端 DOMPurify 二次净化</li>
 * </ul>
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    /** 搜索关键词最大长度 */
    private static final int MAX_KEYWORD_LENGTH = 200;

    /** 默认每页数量 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大每页数量 */
    private static final int MAX_PAGE_SIZE = 50;

    /** MySQL FULLTEXT boolean mode 特殊字符正则（需转义） */
    private static final Pattern FULLTEXT_SPECIAL_CHARS = Pattern.compile("[+\\-><\\(\\)~*\"@]+");

    /** 高亮时内容截取的最大长度（字符数） */
    private static final int MAX_HIGHLIGHT_LENGTH = 300;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserContext userContext;

    @Override
    public Map<String, Object> searchMessages(String email, String keyword, int page, int size) {
        // 1. 获取当前用户 ID
        Integer userId = userContext.getUserId();

        // 2. 关键词校验
        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException("搜索关键词不能为空");
        }
        keyword = keyword.trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException("搜索关键词不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }

        // 3. 参数规范化
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        // 4. 转义 FULLTEXT 特殊字符，防止 SQL 注入
        String escapedKeyword = escapeFulltextKeyword(keyword);

        // 5. 执行搜索
        long offset = (long) (page - 1) * size;
        long total = chatMessageMapper.fulltextSearchCount(userId, escapedKeyword);
        List<SearchResult> results;
        if (total > 0) {
            results = chatMessageMapper.fulltextSearch(userId, escapedKeyword, offset, size);
            // 6. Java 层高亮处理
            for (SearchResult r : results) {
                r.setHighlightContent(highlightContent(keyword, r.getContent()));
            }
        } else {
            results = Collections.emptyList();
        }

        // 7. 构建分页响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("list", results);
        return response;
    }

    /**
     * 转义 MySQL FULLTEXT boolean mode 特殊字符
     * <p>
     * 用户输入可能包含 + - > < ( ) ~ * " @ 等字符，
     * 在 boolean mode 下有特殊含义，需转义以避免语法错误或注入。
     * 转义后每个词前加 + 表示必须包含。
     * </p>
     */
    private String escapeFulltextKeyword(String keyword) {
        // 先去除多余空白，按空格分词
        String[] words = keyword.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            // 转义特殊字符
            String escaped = FULLTEXT_SPECIAL_CHARS.matcher(word).replaceAll("");
            if (escaped.isEmpty()) continue;
            // boolean mode: +word 表示必须包含
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("+").append(escaped);
        }
        return sb.toString();
    }

    /**
     * 对搜索匹配的内容进行关键词高亮处理
     * <p>
     * 使用正则替换将关键词用 &lt;mark&gt; 标签包裹。
     * 如果内容过长，截取第一个匹配位置周围的片段（前后各约 150 字符）。
     * 使用 {@link Pattern#quote} 防止关键词中的正则特殊字符导致注入。
     * </p>
     */
    private String highlightContent(String keyword, String content) {
        if (content == null) return "";

        // 对每个搜索词分别高亮（按空格分词）
        String highlighted = content;
        String[] words = keyword.split("\\s+");

        for (String word : words) {
            if (word.isEmpty()) continue;
            // Pattern.quote 防止正则注入
            String regex = "(?i)" + Pattern.quote(word);
            highlighted = highlighted.replaceAll(regex, "<mark>$0</mark>");
        }

        // 如果内容过长，截取第一个高亮位置周围的内容
        if (highlighted.length() > MAX_HIGHLIGHT_LENGTH * 2) {
            int markStart = highlighted.indexOf("<mark>");
            if (markStart >= 0) {
                int snippetStart = Math.max(0, markStart - MAX_HIGHLIGHT_LENGTH / 2);
                int snippetEnd = Math.min(highlighted.length(), markStart + MAX_HIGHLIGHT_LENGTH / 2);
                String prefix = snippetStart > 0 ? "…" : "";
                String suffix = snippetEnd < highlighted.length() ? "…" : "";
                highlighted = prefix + highlighted.substring(snippetStart, snippetEnd) + suffix;
            } else {
                // 没有匹配到高亮位置，截取开头
                highlighted = highlighted.substring(0, Math.min(highlighted.length(), MAX_HIGHLIGHT_LENGTH)) + "…";
            }
        }

        return highlighted;
    }

}
