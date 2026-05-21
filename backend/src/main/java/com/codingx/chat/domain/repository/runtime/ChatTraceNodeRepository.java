package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatTraceNode;
import java.util.List;

/**
 * 定义 Trace 节点仓储需要提供的最小持久化能力。
 */
public interface ChatTraceNodeRepository {

    /**
     * 保存或更新节点记录。
     * @param traceNode Trace 节点记录。
     */
    void save(ChatTraceNode traceNode);

    /**
     * 根据 traceId 查询节点列表。
     * @param traceId 链路标识。
     * @return 节点列表。
     */
    List<ChatTraceNode> findByTraceId(String traceId);
}
