package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zora.entity.*;
import com.zora.entity.dto.SearchResult;
import com.zora.exception.NotFoundException;
import com.zora.mapper.*;
import com.zora.service.RecommendService;
import com.zora.utils.UserContext;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 智能推荐服务实现
 * <p>
 * Phase 4 智能推荐 —— 基于内容相似度和行为数据的轻量推荐引擎。
 * 不依赖 ML 框架，使用关键词提取 + MySQL FULLTEXT 匹配实现。
 * </p>
 */
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    private static final long RECOMMEND_TTL_SECONDS = 1800;
    private static final int RECENT_MESSAGE_COUNT = 10;
    private static final int TOP_N = 3;

    /** 中文常见停用词 */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "些", "什么", "怎么", "如何", "为什么",
            "可以", "这个", "那个", "还", "被", "把", "让", "能", "吗", "呢", "啊", "哦",
            "但", "但是", "因为", "所以", "如果", "虽然", "然后", "而", "而且", "或", "或者"
    ));

    /** 建议问题模板库 */
    private static final Map<String, List<String>> SUGGESTED_QUESTION_TEMPLATES = new HashMap<>();

    static {
        SUGGESTED_QUESTION_TEMPLATES.put("代码程序编程开发Java Python bug错误调试",
                Arrays.asList("帮我分析这段代码的性能瓶颈", "解释一下这段代码的设计模式", "如何优化这个算法的复杂度"));
        SUGGESTED_QUESTION_TEMPLATES.put("学习知识概念原理理解",
                Arrays.asList("总结一下我们之前讨论的要点", "能用更简单的方式解释这个概念吗", "给我推荐一些相关的学习资源"));
        SUGGESTED_QUESTION_TEMPLATES.put("数据数据库查询SQL",
                Arrays.asList("帮我优化这个数据库查询", "解释一下索引的工作原理", "如何进行数据库性能调优"));
        SUGGESTED_QUESTION_TEMPLATES.put("默认通用",
                Arrays.asList("总结一下我们最近的讨论内容", "基于之前的对话，给我一些建议", "帮我分析一下这个问题的根本原因"));
    }

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatConversationMapper chatConversationMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserContext userContext;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public Map<String, Object> getRecommendations(String email) {
        Integer userId = userContext.getUserId();

        // 尝试从 Redis 缓存获取
        String cacheKey = "recommend:" + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResult = jsonMapper.readValue(cached, Map.class);
                return cachedResult;
            }
        } catch (Exception e) {
            log.debug("推荐缓存读取失败: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relatedConversations", findRelatedConversations(userId));
        result.put("suggestedQuestions", generateSuggestedQuestions(userId));
        result.put("popularKnowledge", findPopularKnowledge(userId));

        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    jsonMapper.writeValueAsString(result),
                    RECOMMEND_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("推荐缓存写入失败: {}", e.getMessage());
        }

        return result;
    }

    private List<Map<String, Object>> findRelatedConversations(Integer userId) {
        try {
            LambdaQueryWrapper<ChatConversation> convWrapper = new LambdaQueryWrapper<>();
            convWrapper.eq(ChatConversation::getUserId, userId)
                    .isNull(ChatConversation::getDeletedAt)
                    .orderByDesc(ChatConversation::getUpdatedAt)
                    .last("LIMIT 20");
            List<ChatConversation> allConvs = chatConversationMapper.selectList(convWrapper);
            if (allConvs.isEmpty()) return Collections.emptyList();

            List<Long> convIds = new ArrayList<>();
            for (ChatConversation c : allConvs) {
                convIds.add(c.getId());
            }

            LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.in(ChatMessage::getConversationId, convIds)
                    .eq(ChatMessage::getRole, "user")
                    .isNull(ChatMessage::getDeletedAt)
                    .orderByDesc(ChatMessage::getCreatedAt)
                    .last("LIMIT " + RECENT_MESSAGE_COUNT);
            List<ChatMessage> recentMessages = chatMessageMapper.selectList(msgWrapper);

            String keywords = extractKeywords(recentMessages);
            if (keywords.isEmpty()) return Collections.emptyList();

            List<SearchResult> searchResults = chatMessageMapper.fulltextSearch(
                    userId, keywords, 0, 20);

            Set<Long> recentConvIds = new HashSet<>();
            for (int i = 0; i < Math.min(5, allConvs.size()); i++) {
                recentConvIds.add(allConvs.get(i).getId());
            }

            Map<Long, List<SearchResult>> groupedByConv = new HashMap<>();
            for (SearchResult r : searchResults) {
                if (!recentConvIds.contains(r.getConversationId())) {
                    groupedByConv.computeIfAbsent(r.getConversationId(), k -> new ArrayList<>()).add(r);
                }
            }

            List<Map.Entry<Long, List<SearchResult>>> sortedEntries = new ArrayList<>(groupedByConv.entrySet());
            sortedEntries.sort((a, b) -> {
                double scoreA = 0;
                for (SearchResult r : a.getValue()) {
                    if (r.getRelevanceScore() != null) scoreA += r.getRelevanceScore();
                }
                scoreA /= a.getValue().size();
                double scoreB = 0;
                for (SearchResult r : b.getValue()) {
                    if (r.getRelevanceScore() != null) scoreB += r.getRelevanceScore();
                }
                scoreB /= b.getValue().size();
                return Double.compare(scoreB, scoreA);
            });

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(TOP_N, sortedEntries.size()); i++) {
                Map.Entry<Long, List<SearchResult>> entry = sortedEntries.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("conversationId", entry.getKey());
                item.put("title", entry.getValue().get(0).getConversationTitle());
                item.put("matchCount", entry.getValue().size());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("相关对话推荐失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> generateSuggestedQuestions(Integer userId) {
        try {
            LambdaQueryWrapper<ChatConversation> convWrapper = new LambdaQueryWrapper<>();
            convWrapper.eq(ChatConversation::getUserId, userId)
                    .isNull(ChatConversation::getDeletedAt)
                    .orderByDesc(ChatConversation::getUpdatedAt)
                    .last("LIMIT 3");
            List<ChatConversation> recentConvs = chatConversationMapper.selectList(convWrapper);
            if (recentConvs.isEmpty()) {
                return SUGGESTED_QUESTION_TEMPLATES.get("默认通用");
            }

            List<Long> convIds = new ArrayList<>();
            for (ChatConversation c : recentConvs) {
                convIds.add(c.getId());
            }

            LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.in(ChatMessage::getConversationId, convIds)
                    .eq(ChatMessage::getRole, "user")
                    .isNull(ChatMessage::getDeletedAt)
                    .orderByDesc(ChatMessage::getCreatedAt)
                    .last("LIMIT 5");
            List<ChatMessage> recentMessages = chatMessageMapper.selectList(msgWrapper);

            StringBuilder contentBuilder = new StringBuilder();
            for (ChatMessage m : recentMessages) {
                if (m.getContent() != null) {
                    contentBuilder.append(m.getContent()).append(" ");
                }
            }
            String allContent = contentBuilder.toString();

            for (Map.Entry<String, List<String>> entry : SUGGESTED_QUESTION_TEMPLATES.entrySet()) {
                if ("默认通用".equals(entry.getKey())) continue;
                String[] topicKeys = entry.getKey().split("");
                int matchCount = 0;
                for (String key : topicKeys) {
                    if (allContent.contains(key)) matchCount++;
                }
                if (matchCount >= 2) {
                    return entry.getValue();
                }
            }
            return SUGGESTED_QUESTION_TEMPLATES.get("默认通用");
        } catch (Exception e) {
            log.warn("建议问题生成失败: {}", e.getMessage());
            return SUGGESTED_QUESTION_TEMPLATES.get("默认通用");
        }
    }

    private List<Map<String, Object>> findPopularKnowledge(Integer userId) {
        try {
            LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
            kbWrapper.eq(KnowledgeBase::getUserId, userId)
                    .isNull(KnowledgeBase::getDeletedAt)
                    .orderByDesc(KnowledgeBase::getUpdatedAt);
            List<KnowledgeBase> kbs = knowledgeBaseMapper.selectList(kbWrapper);
            if (kbs.isEmpty()) return Collections.emptyList();

            List<Map<String, Object>> kbStats = new ArrayList<>();
            for (KnowledgeBase kb : kbs) {
                LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
                docWrapper.eq(KbDocument::getKbId, kb.getId())
                        .isNull(KbDocument::getDeletedAt);
                long docCount = kbDocumentMapper.selectCount(docWrapper);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", kb.getId());
                item.put("name", kb.getName());
                item.put("documentCount", docCount);
                kbStats.add(item);
            }

            kbStats.sort((a, b) -> Long.compare(
                    (Long) b.get("documentCount"), (Long) a.get("documentCount")));

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(TOP_N, kbStats.size()); i++) {
                result.add(kbStats.get(i));
            }
            return result;
        } catch (Exception e) {
            log.warn("热门知识库推荐失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractKeywords(List<ChatMessage> messages) {
        if (messages.isEmpty()) return "";

        StringBuilder contentBuilder = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m.getContent() != null) {
                contentBuilder.append(m.getContent());
            }
        }
        String allContent = contentBuilder.toString();

        Map<String, Integer> wordFreq = new HashMap<>();
        for (int i = 0; i < allContent.length() - 1; i++) {
            String bigram = allContent.substring(i, i + 2);
            if (!bigram.matches("[\\u4e00-\\u9fa5]{2}")) continue;
            if (STOP_WORDS.contains(bigram)) continue;
            Integer count = wordFreq.get(bigram);
            if (count == null) {
                wordFreq.put(bigram, 1);
            } else {
                wordFreq.put(bigram, count + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(wordFreq.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder keywords = new StringBuilder();
        for (int i = 0; i < Math.min(5, sortedEntries.size()); i++) {
            if (keywords.length() > 0) keywords.append(" ");
            keywords.append("+").append(sortedEntries.get(i).getKey());
        }
        return keywords.toString();
    }

}
