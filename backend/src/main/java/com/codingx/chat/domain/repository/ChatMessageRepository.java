package com.codingx.chat.domain.repository;
import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;

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
}
