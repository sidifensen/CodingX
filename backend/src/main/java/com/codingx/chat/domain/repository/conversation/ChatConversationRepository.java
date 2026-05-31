package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatConversation;
import java.util.List;
import java.util.Optional;

/**
 * 聊天会话仓储端口，负责会话聚合的加载、保存、分享查询和管理端检索。
 */
public interface ChatConversationRepository {

    /**
     * 按主键加载会话，不存在或已删除时抛出业务异常。
     * @param conversationId 会话标识。
     * @return 会话领域对象。
     */
    ChatConversation requireById(Long conversationId);

    /**
     * 保存会话聚合，存在时更新，不存在时新增。
     * @param conversation 待持久化的会话领域对象。
     */
    void save(ChatConversation conversation);

    /**
     * 逻辑删除指定会话。
     * @param conversationId 会话标识。
     */
    void deleteById(Long conversationId);

    /**
     * 查询指定用户在指定工作空间下的未删除会话。
     * @param userId 用户标识。
     * @param workspaceId 工作空间标识，可为空；为空时查询历史未归属云端会话。
     * @return 按置顶、更新时间和主键倒序排列的会话列表。
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
