package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 聊天消息仓储端口，负责领域消息与持久化实现之间的边界隔离。
 */
public interface ChatMessageRepository {

    /**
     * 保存聊天消息，存在时更新，不存在时新增。
     * @param message 待持久化的领域消息对象。
     */
    void save(ChatMessage message);

    /**
     * 按会话查询未删除消息，供聊天上下文拼装与历史回放使用。
     * @param conversationId 会话标识。
     * @return 按创建时间升序排列的消息列表。
     */
    List<ChatMessage> findByConversationId(Long conversationId);

    /**
     * 按会话读取最近消息页，按创建时间倒序返回，应用层会在响应前恢复升序。
     * @param conversationId 会话标识。
     * @param limit 最大读取条数，调用方通常传入 pageSize + 1。
     * @param beforeCreatedAt 游标创建时间，为空时读取最新消息页。
     * @param beforeId 游标消息 ID，为空时读取最新消息页。
     * @return 按创建时间倒序排列的消息页。
     */
    List<ChatMessage> findRecentPageByConversationId(
        Long conversationId,
        int limit,
        LocalDateTime beforeCreatedAt,
        Long beforeId
    );

    /**
     * 按消息主键查询单条记录。
     * @param id 消息主键。
     * @return 消息记录。
     */
    Optional<ChatMessage> findById(Long id);

    /**
     * 按会话范围逻辑删除消息，供前端消息级删除与编辑重发清理旧上下文使用。
     * @param conversationId 会话标识。
     * @param messageIds 消息主键列表。
     */
    void softDeleteByConversationIdAndIds(Long conversationId, List<Long> messageIds);
}
