package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatMessageArtifact;
import java.util.List;

/**
 * 定义聊天产物仓储需要提供的最小持久化能力。
 */
public interface ChatMessageArtifactRepository {

    /**
     * 保存或更新产物记录。
     * @param artifact 产物记录。
     */
    void save(ChatMessageArtifact artifact);

    /**
     * 根据执行主链路查询产物列表。
     * @param runId 执行主链路标识。
     * @return 产物列表。
     */
    List<ChatMessageArtifact> findByRunId(Long runId);
}
