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
     * 管理端按工作空间查询会话，供工作空间详情页查看空间内历史。
     * @param workspaceId 工作空间标识。
     * @param keyword 可选关键字，支持标题模糊匹配或 ID 精确匹配。
     * @return 工作空间内未删除会话列表。
     */
    List<ChatConversation> findAllByWorkspaceId(Long workspaceId, String keyword);

    /**
     * 按会话主键查询单条记录，不存在时返回空。
     * @param conversationId 会话标识。
     * @return 会话记录。
     */
    Optional<ChatConversation> findById(Long conversationId);

    /**
     * 根据分享令牌查询会话，供公开只读分享页加载。
     * @param shareToken 分享令牌。
     * @return 会话记录。
     */
    Optional<ChatConversation> findByShareToken(String shareToken);
}
