package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.ChatConversationSummary;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话摘要 Mapper（Phase 3.4 长期记忆）
 * <p>
 * 继承 BaseMapper 获得自动 CRUD，额外提供按会话查询最新摘要的方法。
 * </p>
 */
public interface ChatConversationSummaryMapper extends BaseMapper<ChatConversationSummary> {

    /**
     * 查询指定会话的所有摘要（按时间正序）
     * <p>
     * 用于构建摘要上下文，注入 System Prompt。
     * </p>
     *
     * @param conversationId 会话 ID
     * @return 摘要列表（按创建时间升序）
     */
    @Select("SELECT * FROM chat_conversation_summary WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<ChatConversationSummary> selectByConversationId(Long conversationId);

    /**
     * 统计指定会话的摘要数量
     *
     * @param conversationId 会话 ID
     * @return 摘要总数
     */
    @Select("SELECT COUNT(*) FROM chat_conversation_summary WHERE conversation_id = #{conversationId}")
    int countByConversationId(Long conversationId);
}
