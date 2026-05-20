package com.codingx.chat.domain.repository;
import com.codingx.chat.domain.model.ChatConversation;
import java.util.List;
import java.util.Optional;

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
    List<ChatConversation> findByCreatedByAndWorkspaceId(Long userId, Long workspaceId);

    /**
     * 供管理端按关键字查询会话列表，返回全量会话记录。
     * @param keyword 可选关键字，支持标题模糊匹配或 ID 精确匹配。
     * @return 会话列表。
     */
    List<ChatConversation> findAll(String keyword);

    /**
     * 按会话主键查询单条记录，不存在时返回空。
     * @param conversationId 会话标识。
     * @return 会话记录。
     */
    Optional<ChatConversation> findById(Long conversationId);
}
