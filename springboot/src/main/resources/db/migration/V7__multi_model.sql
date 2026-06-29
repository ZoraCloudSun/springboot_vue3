-- Phase 5.3: 多模型支持
-- 为对话表增加模型提供商和模型 ID 字段，支持用户在不同模型间切换
ALTER TABLE chat_conversation
    ADD COLUMN model_provider VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT '模型提供商',
    ADD COLUMN model_id VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat' COMMENT '模型 ID';
