package com.codingx.chat.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import com.codingx.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责按 traceId 聚合根记录与节点集合。
 */
@Service
@RequiredArgsConstructor
public class ConversationTraceQueryService {

    /** Trace 运行仓储，用于读取指定 traceId 的根运行记录。 */
    private final ChatTraceRunRepository chatTraceRunRepository;
    /** Trace 节点仓储，用于读取并规范化指定 traceId 的节点集合。 */
    private final ChatTraceNodeRepository chatTraceNodeRepository;

    /**
     * 查询指定链路的完整 Trace 视图。
     * @param traceId 链路标识。
     * @return 聚合视图。
     */
    public ConversationTraceView getTrace(String traceId) {
        return new ConversationTraceView(
            chatTraceRunRepository.findByTraceId(traceId).orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_TRACE_NOT_FOUND)),
            TraceNodeHierarchyNormalizer.normalize(chatTraceNodeRepository.findByTraceId(traceId))
        );
    }
}
