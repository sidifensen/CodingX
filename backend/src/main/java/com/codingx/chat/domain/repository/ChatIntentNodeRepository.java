package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;

/**
 * 定义意图树节点仓储需要提供的最小持久化能力。
 */
public interface ChatIntentNodeRepository {

    /**
     * 保存或更新意图节点。
     * @param node 意图节点记录。
     */
    void save(ChatIntentNode node);

    /**
     * 查询当前启用的意图节点集合。
     * @return 节点集合。
     */
    List<ChatIntentNode> findEnabledNodes();

    /**
     * 按意图编码查询单个节点。
     * @param intentCode 意图编码。
     * @return 意图节点；不存在时返回 null。
     */
    ChatIntentNode findByIntentCode(String intentCode);
}
