package com.codingx.chat.domain.repository;
import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import java.util.Optional;

/**
 * 定义 ChatMessageRepository 的仓储契约。
 */
public interface ChatMessageRepository {

    /**
     * 持久化 save 处理的状态。
     * @param message 输入参数。
     */
    void save(ChatMessage message);

    /**
     * 查询 findByConversationId 需要的数据。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    List<ChatMessage> findByConversationId(Long conversationId);

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
