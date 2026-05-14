package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatExecutionStep;
import java.util.List;

/**
 * 定义执行步骤仓储需要提供的最小持久化能力。
 */
public interface ChatExecutionStepRepository {

    /**
     * 保存或更新执行步骤。
     * @param step 执行步骤记录。
     */
    void save(ChatExecutionStep step);

    /**
     * 根据执行主链路查询步骤列表。
     * @param runId 执行主链路标识。
     * @return 步骤列表。
     */
    List<ChatExecutionStep> findByRunId(Long runId);
}
