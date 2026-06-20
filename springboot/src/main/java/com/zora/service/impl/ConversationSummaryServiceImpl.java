package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zora.config.AgentConfig;
import com.zora.entity.ChatConversation;
import com.zora.entity.ChatConversationSummary;
import com.zora.entity.ChatMessage;
import com.zora.mapper.ChatConversationMapper;
import com.zora.mapper.ChatConversationSummaryMapper;
import com.zora.mapper.ChatMessageMapper;
import com.zora.service.ConversationSummaryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 对话摘要服务实现（Phase 3.4 长期记忆）
 * <p>
 * 核心流程：
 * <ol>
 * <li>每次 AI 回复保存后，检查消息数是否达到阈值</li>
 * <li>若达到（每 10 条），异步调用 LLM 生成摘要</li>
 * <li>摘要存储到 {@code chat_conversation_summary} 表</li>
 * <li>更新 {@code chat_conversation.summary_id} 指向最新摘要</li>
 * <li>后续对话时，摘要注入 System Prompt 作为长期记忆</li>
 * </ol>
 * </p>
 *
 * <h3>异步策略</h3>
 * <p>
 * 使用 {@link CompletableFuture#runAsync(Runnable)} 异步执行，
 * 避免阻塞流式对话的 done 事件推送（摘要生成需要 1-3 秒）。
 * </p>
 *
 * @see ConversationSummaryService
 */
@Service
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryServiceImpl.class);

    /** 摘要系统提示词 */
    private static final String SUMMARY_PROMPT =
            "你是一个对话摘要助手。请用不超过 %d 个中文字符总结以下对话的关键内容。" +
            "保留重要的事实、用户偏好、决策和待办事项。只输出摘要文本，不要包含任何前缀或后缀。";

    /** 未覆盖消息的查询偏移量（跳过已被摘要覆盖的消息） */
    private static final String SUMMARY_SYSTEM_MSG = "你是一个对话摘要助手。请总结对话关键内容。";

    @Resource
    private ChatModel chatLanguageModel;

    @Resource
    private ChatMessageMapper messageMapper;

    @Resource
    private ChatConversationMapper conversationMapper;

    @Resource
    private ChatConversationSummaryMapper summaryMapper;

    @Resource
    private AgentConfig agentConfig;

    @Override
    public void checkAndSummarize(Long conversationId) {
        // 统计当前会话消息数
        Long messageCount = messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .isNull(ChatMessage::getDeletedAt));

        if (messageCount == null || messageCount == 0) {
            return;
        }

        // 计算已覆盖消息数（已有摘要覆盖的消息总量）
        int coveredCount = getCoveredMessageCount(conversationId);

        // 未覆盖的消息数 = 总消息数 - 已覆盖消息数
        long uncoveredCount = messageCount - coveredCount;

        // 未达阈值，不触发
        if (uncoveredCount < SUMMARY_TRIGGER_COUNT) {
            return;
        }

        log.info("触发摘要生成: conversationId={}, totalMessages={}, covered={}, uncovered={}",
                conversationId, messageCount, coveredCount, uncoveredCount);

        final int triggerCount = agentConfig.getMemory().getSummaryTriggerCount();

        // 异步执行摘要生成（不阻塞对话流）
        CompletableFuture.runAsync(() -> generateAndSaveSummary(conversationId, coveredCount, triggerCount))
                .exceptionally(ex -> {
                    log.error("异步摘要生成失败: conversationId={}, error={}",
                            conversationId, ex.getMessage());
                    return null;
                });
    }

    @Override
    public String buildSummaryContext(Long conversationId) {
        List<ChatConversationSummary> summaries = summaryMapper.selectByConversationId(conversationId);

        if (summaries == null || summaries.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【对话历史摘要（长期记忆，按时间顺序）】\n");

        for (int i = 0; i < summaries.size(); i++) {
            ChatConversationSummary s = summaries.get(i);
            sb.append(String.format("%d. %s\n", i + 1, s.getSummary()));
        }

        sb.append("【历史摘要结束】\n");
        return sb.toString();
    }

    // ==================== 内部实现 ====================

    /**
     * 生成并保存对话摘要（异步执行）
     *
     * @param conversationId 会话 ID
     * @param offset         起始偏移量（跳过已覆盖的消息）
     * @param triggerCount   摘要触发阈值
     */
    private void generateAndSaveSummary(Long conversationId, int offset, int triggerCount) {
        try {
            // 加载最近 N 条未覆盖消息
            List<ChatMessage> messages = loadRecentMessages(conversationId, triggerCount + offset, offset);

            if (messages.size() < triggerCount) {
                log.debug("可摘要消息不足: conversationId={}, available={}, required={}",
                        conversationId, messages.size(), triggerCount);
                return;
            }

            // 构建摘要请求
            String summary = generateSummary(messages, agentConfig.getMemory().getSummaryMaxLength());
            if (summary == null || summary.isEmpty()) {
                log.warn("LLM 摘要生成返回空: conversationId={}", conversationId);
                return;
            }

            // 保存摘要
            ChatConversationSummary entity = new ChatConversationSummary(
                    conversationId, summary, messages.size());
            summaryMapper.insert(entity);

            // 更新会话的最新摘要 ID
            ChatConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation != null) {
                conversation.setSummaryId(entity.getId());
                conversationMapper.updateById(conversation);
            }

            log.info("摘要已保存: conversationId={}, summaryId={}, length={}, coveredMessages={}",
                    conversationId, entity.getId(), summary.length(), offset + messages.size());
        } catch (Exception e) {
            log.error("摘要生成异常: conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 调用 LLM 生成对话摘要
     *
     * @param messages  需要摘要的消息列表
     * @param maxLength 摘要最大长度
     * @return 摘要文本
     */
    private String generateSummary(List<ChatMessage> messages, int maxLength) {
        // 构建消息内容
        StringBuilder conversationText = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "AI";
            conversationText.append(role).append(": ")
                    .append(msg.getContent())
                    .append("\n\n");
        }

        // 构建 LangChain4j 请求
        var chatMessages = List.of(
                SystemMessage.from(String.format(SUMMARY_PROMPT, maxLength)),
                UserMessage.from(conversationText.toString())
        );

        ChatResponse response = chatLanguageModel.chat(
                ChatRequest.builder().messages(chatMessages).build());

        String summary = response.aiMessage().text();
        if (summary != null && summary.length() > maxLength) {
            summary = summary.substring(0, maxLength);
        }
        return summary;
    }

    /**
     * 加载指定会话的最近 N 条消息
     * <p>
     * 按时间正序排列。offset 用于跳过已被之前摘要覆盖的消息。
     * </p>
     */
    private List<ChatMessage> loadRecentMessages(Long conversationId, int limit, int offset) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
                .isNull(ChatMessage::getDeletedAt)
                .orderByAsc(ChatMessage::getCreatedAt)
                .last("LIMIT " + offset + ", " + limit);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 计算已经被历史摘要覆盖的消息数
     * <p>
     * 遍历所有已有摘要，累加其 message_count。
     * </p>
     */
    private int getCoveredMessageCount(Long conversationId) {
        List<ChatConversationSummary> summaries = summaryMapper.selectByConversationId(conversationId);
        if (summaries == null || summaries.isEmpty()) {
            return 0;
        }
        return summaries.stream()
                .mapToInt(s -> s.getMessageCount() != null ? s.getMessageCount() : 0)
                .sum();
    }
}
