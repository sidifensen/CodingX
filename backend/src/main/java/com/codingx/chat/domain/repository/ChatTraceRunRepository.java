package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.Optional;

/**
 * 定义 Trace 根链路仓储需要提供的最小持久化能力。
 */
public interface ChatTraceRunRepository {

    /**
     * 保存或更新根链路记录。
     * @param traceRun 根链路记录。
     */
    void save(ChatTraceRun traceRun);

    /**
     * 根据 traceId 查询根链路记录。
     * @param traceId 链路标识。
     * @return 根链路记录。
     */
    Optional<ChatTraceRun> findByTraceId(String traceId);
}
