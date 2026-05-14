package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatConversationSummary;
import java.util.Optional;

/**
 * 定义会话摘要仓储需要提供的最小持久化能力。
 */
public interface ChatConversationSummaryRepository {

    /**
     * 保存或更新会话摘要。
     * @param summary 会话摘要快照。
     */
    void save(ChatConversationSummary summary);

    /**
     * 查询指定会话最新的一条摘要记录。
     * @param conversationId 会话标识。
     * @return 最新摘要。
     */
    Optional<ChatConversationSummary> findLatestByConversationId(Long conversationId);
}
