package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatMessageReference;
import java.util.List;

/**
 * 定义参考来源仓储需要提供的最小持久化能力。
 */
public interface ChatMessageReferenceRepository {

    /**
     * 保存或更新参考来源。
     * @param reference 参考来源记录。
     */
    void save(ChatMessageReference reference);

    /**
     * 根据执行主链路查询来源列表。
     * @param runId 执行主链路标识。
     * @return 来源列表。
     */
    List<ChatMessageReference> findByRunId(Long runId);

    /**
     * 根据消息查询来源列表，供反馈追溯页展示引用证据。
     * @param messageId 消息标识。
     * @return 来源列表。
     */
    List<ChatMessageReference> findByMessageId(Long messageId);
}
