package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatExecutionRun;
import java.util.List;

/**
 * 定义执行主链路仓储需要提供的最小持久化能力。
 */
public interface ChatExecutionRunRepository {

    /**
     * 保存或更新执行主链路。
     * @param run 执行主链路记录。
     */
    void save(ChatExecutionRun run);

    /**
     * 根据会话查询关联执行记录。
     * @param conversationId 会话标识。
     * @return 执行记录列表。
     */
    List<ChatExecutionRun> findByConversationId(Long conversationId);
}
