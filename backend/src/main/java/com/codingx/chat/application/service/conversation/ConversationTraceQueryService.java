package com.codingx.chat.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责按 traceId 聚合根记录与节点集合。
 */
@Service
@RequiredArgsConstructor
public class ConversationTraceQueryService {

    private final ChatTraceRunRepository chatTraceRunRepository;
    private final ChatTraceNodeRepository chatTraceNodeRepository;

    /**
     * 查询指定链路的完整 Trace 视图。
     * @param traceId 链路标识。
     * @return 聚合视图。
     */
    public ConversationTraceView getTrace(String traceId) {
        return new ConversationTraceView(
            chatTraceRunRepository.findByTraceId(traceId).orElseThrow(() -> new IllegalArgumentException(ErrorMessageCatalog.CHAT_TRACE_NOT_FOUND)),
            chatTraceNodeRepository.findByTraceId(traceId)
        );
    }
}
