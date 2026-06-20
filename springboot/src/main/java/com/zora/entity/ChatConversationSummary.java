package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 对话摘要实体（Phase 3.4 长期记忆）
 * <p>
 * 当对话消息数量达到阈值（默认 10 条）时，由 LLM 异步生成对话历史摘要。
 * 摘要内容会在后续对话中注入 System Prompt，为 AI 提供"长期记忆"。
 * </p>
 *
 * <h3>与 chat_message 的关系</h3>
 * <p>
 * chat_message 存储完整的对话历史（短期记忆），受窗口大小限制（最近 20 条）。
 * chat_conversation_summary 存储对话摘要（长期记忆），不受窗口限制，
 * 让 AI 在长对话中仍能"记住"早期讨论内容。
 * </p>
 */
@TableName("chat_conversation_summary")
@Schema(description = "对话摘要（Phase 3.4 长期记忆）")
public class ChatConversationSummary {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "摘要 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "conversation_id")
    @Schema(description = "所属会话 ID", example = "1")
    private Long conversationId;

    @TableField(value = "summary")
    @Schema(description = "对话摘要内容（由 LLM 生成）", example = "用户询问了 Spring Boot 的配置方式...")
    private String summary;

    @TableField(value = "message_count")
    @Schema(description = "摘要覆盖的消息数量", example = "10")
    private Integer messageCount;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    public ChatConversationSummary() {
    }

    public ChatConversationSummary(Long conversationId, String summary, Integer messageCount) {
        this.conversationId = conversationId;
        this.summary = summary;
        this.messageCount = messageCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
