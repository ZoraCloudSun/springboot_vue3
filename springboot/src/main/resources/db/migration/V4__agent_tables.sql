-- ============================================================================
-- Agent 记忆系统数据库迁移（Phase 3.4）
-- 新增 chat_conversation_summary（对话摘要表）+ chat_conversation.summary_id 列
-- ============================================================================

-- 对话摘要表：存储 LLM 生成的对话历史摘要（长期记忆）
CREATE TABLE IF NOT EXISTS chat_conversation_summary (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '摘要 ID',
    conversation_id  BIGINT NOT NULL COMMENT '所属会话 ID（外键 → chat_conversation.id）',
    summary          TEXT NOT NULL COMMENT '对话摘要内容（由 LLM 生成，≤300 字）',
    message_count    INT NOT NULL COMMENT '摘要覆盖的消息数量',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话摘要表（Phase 3.4 长期记忆）';

-- 索引：按会话和创建时间查询最新摘要
CREATE INDEX idx_summary_conv ON chat_conversation_summary(conversation_id, created_at);

-- 会话表增加最新摘要关联（方便查询最新摘要）
ALTER TABLE chat_conversation
    ADD COLUMN summary_id BIGINT DEFAULT NULL COMMENT '最新摘要 ID（外键 → chat_conversation_summary.id）' AFTER deleted_at,
    ADD FOREIGN KEY fk_conv_summary (summary_id) REFERENCES chat_conversation_summary(id) ON DELETE SET NULL;
