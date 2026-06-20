package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体
 * 每个用户可以有多个对话，每个对话包含多条消息
 */
@TableName("chat_conversation")
@Schema(description = "AI 对话会话")
public class ChatConversation {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "对话 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "user_id")
    @Schema(description = "所属用户 ID", example = "1")
    private Integer userId;

    @TableField(value = "title")
    @Schema(description = "对话标题", example = "Spring Boot 问题咨询")
    private String title;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    @Schema(description = "最后更新时间")
    private LocalDateTime updatedAt;

    @TableField(value = "deleted_at")
    @Schema(description = "软删除时间（null 表示未删除）")
    private LocalDateTime deletedAt;

    @TableField(value = "summary_id")
    @Schema(description = "最新摘要 ID（Phase 3.4 长期记忆）")
    private Long summaryId;

    public ChatConversation() {
    }

    public ChatConversation(Integer userId, String title) {
        this.userId = userId;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getSummaryId() {
        return summaryId;
    }

    public void setSummaryId(Long summaryId) {
        this.summaryId = summaryId;
    }
}
