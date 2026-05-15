package com.codingx.chat.domain.repository;
import com.codingx.chat.domain.model.ChatConversation;
import java.util.List;

/**
 * 定义 ChatConversationRepository 的仓储契约。
 */
public interface ChatConversationRepository {

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    ChatConversation requireById(Long conversationId);

    /**
     * 持久化 save 处理的状态。
     * @param conversation 输入参数。
     */
    void save(ChatConversation conversation);

    /**
     * 逻辑删除指定会话。
     * @param conversationId 会话标识。
     */
    void deleteById(Long conversationId);

    /**
     * 查询 findByCreatedBy 需要的数据。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    List<ChatConversation> findByCreatedBy(Long userId);
}
