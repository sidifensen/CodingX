package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatIntentExample;
import java.util.List;

/**
 * 定义意图示例仓储需要提供的最小持久化能力。
 */
public interface ChatIntentExampleRepository {

    /**
     * 保存或更新意图示例。
     * @param example 意图示例记录。
     */
    void save(ChatIntentExample example);

    /**
     * 根据意图编码查询示例列表。
     * @param intentCode 意图编码。
     * @return 示例列表。
     */
    List<ChatIntentExample> findByIntentCode(String intentCode);
}
