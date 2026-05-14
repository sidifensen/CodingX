package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责保存聊天链路的根 Trace 与关键节点。
 */
@Service
@RequiredArgsConstructor
public class ConversationTraceRecordService {

    private final ChatTraceRunRepository chatTraceRunRepository;
    private final ChatTraceNodeRepository chatTraceNodeRepository;

    /**
     * 启动一条新的聊天 Trace。
     * @param traceName 链路名称。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @return 根 Trace。
     */
    public ChatTraceRun startTrace(String traceName, Long conversationId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ChatTraceRun traceRun = ConversationTraceContext.start(traceName, conversationId, userId).toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .entryMethod("chat")
            .startedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatTraceRunRepository.save(traceRun);
        chatTraceNodeRepository.save(ChatTraceNode.builder()
            .id(IdUtil.getSnowflakeNextId())
            .traceId(traceRun.getTraceId())
            .nodeId(IdUtil.fastSimpleUUID())
            .depth(0)
            .nodeType("entry")
            .nodeName(traceName)
            .status("RUNNING")
            .startedAt(now)
            .createdAt(now)
            .build());
        return traceRun;
    }
}
