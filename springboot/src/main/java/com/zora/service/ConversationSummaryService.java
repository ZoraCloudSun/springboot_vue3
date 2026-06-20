package com.zora.service;

/**
 * 对话摘要服务接口（Phase 3.4 长期记忆）
 * <p>
 * 当对话消息数量达到阈值时，异步生成 LLM 摘要，为长对话提供"长期记忆"。
 * </p>
 *
 * <h3>触发条件</h3>
 * <p>
 * 每 {@link #SUMMARY_TRIGGER_COUNT} 条消息触发一次摘要生成。
 * 摘要覆盖已生成历史摘要之后的新消息。
 * </p>
 *
 * <h3>上下文注入</h3>
 * <p>
 * 后续对话中，所有历史摘要会被注入 System Prompt，格式：
 * <pre>{@code
 * 【对话历史摘要（长期记忆）】
 * - (摘要1)
 * - (摘要2)
 * ...
 * 【历史摘要结束】
 * }</pre>
 * </p>
 */
public interface ConversationSummaryService {

    /** 摘要触发阈值：每 10 条消息生成一次摘要 */
    int SUMMARY_TRIGGER_COUNT = 10;

    /** 摘要最大长度（字符数） */
    int SUMMARY_MAX_LENGTH = 300;

    /**
     * 检查并触发摘要生成
     * <p>
     * 在每条 AI 回复保存后调用。如果当前消息数达到阈值，
     * 异步调用 LLM 生成摘要，存储到数据库。
     * </p>
     *
     * @param conversationId 会话 ID
     */
    void checkAndSummarize(Long conversationId);

    /**
     * 构建摘要上下文（用于注入 System Prompt）
     * <p>
     * 从数据库加载该会话的所有历史摘要，格式化为一段文本。
     * 如果没有摘要，返回空字符串。
     * </p>
     *
     * @param conversationId 会话 ID
     * @return 格式化的摘要文本
     */
    String buildSummaryContext(Long conversationId);
}
